/*
 * Copyright 2017 Google Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.google.android.sambadocumentsprovider.document;

import android.net.Uri;
import android.provider.DocumentsContract.Document;
import android.support.annotation.Nullable;
import android.system.OsConstants;
import android.system.StructStat;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.google.android.sambadocumentsprovider.R;
import com.google.android.sambadocumentsprovider.base.DirectoryEntry;
import com.google.android.sambadocumentsprovider.nativefacade.SmbClient;
import com.google.android.sambadocumentsprovider.nativefacade.SmbDir;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This is a snapshot of the metadata of a seen document. It contains its SMB URI, display name,
 * access/create/modified time, size, its children etc sometime in the past. It also contains
 * the last exception thrown when querying its children.
 *
 * The metadata inside this class may be fetched at different time due to Samba client API.
 */
public class DocumentMetadata {

  private static final String TAG = "DocumentMetadata";
  private static final String GENERIC_MIME_TYPE = "application/octet-stream";
  private static final Uri SMB_BASE_URI = Uri.parse("smb://");

  private final DirectoryEntry mEntry;
  private Uri mUri;

  private final AtomicReference<StructStat> mStat = new AtomicReference<>(null);
  private final AtomicReference<Map<Uri, DocumentMetadata>> mChildren = new AtomicReference<>(null);

  private final AtomicReference<Exception> mLastChildUpdateException = new AtomicReference<>(null);
  private final AtomicReference<Exception> mLastStatException = new AtomicReference<>(null);
  private long mTimeStamp;

  public DocumentMetadata(Uri uri, DirectoryEntry entry) {
    mUri = uri;
    mEntry = entry;

    mTimeStamp = System.currentTimeMillis();
  }

  public Uri getUri() {
    return mUri;
  }

  public boolean isFileShare() {
    return mEntry.getType() == DirectoryEntry.FILE_SHARE;
  }

  public Integer getIconResourceId() {
    switch (mEntry.getType()) {
      case DirectoryEntry.SERVER:
        return R.drawable.ic_server;
      case DirectoryEntry.FILE_SHARE:
        return R.drawable.ic_folder_shared;
      default:
        // Tells SAF to use the default icon.
        return null;
    }
  }

  public Long getLastModified() {
    final StructStat stat = mStat.get();
    return (stat == null) ? null : TimeUnit.MILLISECONDS.convert(stat.st_mtime, TimeUnit.SECONDS);
  }

  public String getDisplayName() {
    return mEntry.getName();
  }

  public String getComment() {
    return mEntry.getComment();
  }

  public boolean needsStat() {
    return hasStat() && mStat.get() == null;
  }

  private boolean hasStat() {
    switch (mEntry.getType()) {
      case DirectoryEntry.FILE:
        return true;
      case DirectoryEntry.WORKGROUP:
      case DirectoryEntry.SERVER:
      case DirectoryEntry.FILE_SHARE:
      case DirectoryEntry.DIR:
        // Everything is writable so no need to fetch stats for them.
        return false;
      default:
        throw new UnsupportedOperationException(
            "Unsupported type of Samba directory entry: " + mEntry.getType());
    }
  }

  public Long getSize() {
    final StructStat stat = mStat.get();
    return (stat == null) ? null : stat.st_size;
  }

  public boolean canCreateDocument() {
    switch (mEntry.getType()) {
      case DirectoryEntry.DIR:
      case DirectoryEntry.FILE_SHARE:
        return true;
      case DirectoryEntry.WORKGROUP:
      case DirectoryEntry.SERVER:
      case DirectoryEntry.FILE:
        return false;
      default:
        throw new UnsupportedOperationException(
            "Unsupported type of Samba directory entry " + mEntry.getType());
    }
  }

  public String getMimeType() {
    switch (mEntry.getType()) {
      case DirectoryEntry.FILE_SHARE:
      case DirectoryEntry.WORKGROUP:
      case DirectoryEntry.SERVER:
      case DirectoryEntry.DIR:
        return Document.MIME_TYPE_DIR;

      case DirectoryEntry.LINK:
      case DirectoryEntry.COMMS_SHARE:
      case DirectoryEntry.IPC_SHARE:
      case DirectoryEntry.PRINTER_SHARE:
        throw new UnsupportedOperationException(
            "Unsupported type of Samba directory entry " + mEntry.getType());

      case DirectoryEntry.FILE:
        final String ext = getExtension(mEntry.getName());
        if (ext == null) {
          return GENERIC_MIME_TYPE;
        }

        final String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return (mimeType == null) ? GENERIC_MIME_TYPE : mimeType;
    }

    throw new IllegalStateException("Should never reach here.");
  }

  private String getExtension(String name) {
    if (TextUtils.isEmpty(name)) {
      return null;
    }

    final int idxOfDot = name.lastIndexOf('.', name.length() - 1);
    if (idxOfDot <= 0) {
      return null;
    }

    return name.substring(idxOfDot + 1).toLowerCase();
  }

  public void reset() {
    mStat.set(null);
    mChildren.set(null);
  }

  public long getTimeStamp() {
    return mTimeStamp;
  }

  public void throwLastChildUpdateExceptionIfAny() throws Exception {
    final Exception e = mLastChildUpdateException.get();
    if (e != null) {
      mLastChildUpdateException.set(null);
      throw e;
    }
  }

  public boolean hasLoadingStatFailed() {
    final Exception e = mLastStatException.getAndSet(null);
    return e != null;
  }

  public void rename(Uri newUri) {
    mEntry.setName(newUri.getLastPathSegment());
    mUri = newUri;
  }

  /**
   * Gets children of this document.
   * @return the list of children or {@code null} if it's not fetched yet.
   */
  public @Nullable Map<Uri, DocumentMetadata> getChildren() {
    return mChildren.get();
  }

  public void loadChildren(SmbClient client) throws IOException {
    try (final SmbDir dir = client.openDir(mUri.toString())) {

      Map<Uri, DocumentMetadata> children = new HashMap<>();
      DirectoryEntry entry;
      while ((entry = dir.readDir()) != null) {
        Uri childUri = DocumentMetadata.buildChildUri(mUri, entry);
        if (childUri != null) {
          children.put(childUri, new DocumentMetadata(childUri, entry));
        }
      }

      mChildren.set(children);
      mTimeStamp = System.currentTimeMillis();

    } catch (Exception e) {
      Log.e(TAG, "Failed to load children.", e);
      mLastChildUpdateException.set(e);
      throw e;
    }
  }

  public void putChild(DocumentMetadata child) {
    Map<Uri, DocumentMetadata> children = mChildren.get();
    if (children != null) {
      children.put(child.getUri(), child);
    }
  }

  void loadStat(SmbClient client) throws IOException {
    try {
      mStat.set(client.stat(mUri.toString()));

      mTimeStamp = System.currentTimeMillis();
    } catch (Exception e) {
      Log.e(TAG, "Failed to get stat.", e);
      mLastStatException.set(e);
      throw e;
    }
  }

  public static Uri buildChildUri(Uri parentUri, DirectoryEntry entry) {
    switch (entry.getType()) {
      // TODO: Support LINK type?
      case DirectoryEntry.LINK:
      case DirectoryEntry.COMMS_SHARE:
      case DirectoryEntry.IPC_SHARE:
      case DirectoryEntry.PRINTER_SHARE:
        Log.i(TAG, "Found unsupported type: " + entry.getType()
            + " name: " + entry.getName()
            + " comment: " + entry.getComment());
        return null;
      case DirectoryEntry.WORKGROUP:
      case DirectoryEntry.SERVER:
        return SMB_BASE_URI.buildUpon().authority(entry.getName()).build();
      case DirectoryEntry.FILE_SHARE:
      case DirectoryEntry.DIR:
      case DirectoryEntry.FILE:
        return buildChildUri(parentUri, entry.getName());
    }

    Log.w(TAG, "Unknown type: " + entry.getType()
        + " name: " + entry.getName()
        + " comment: " + entry.getComment());
    return null;
  }

  public static Uri buildChildUri(Uri parentUri, String displayName) {
    if (".".equals(displayName) || "..".equals(displayName)) {
      return null;
    } else {
      return parentUri.buildUpon().appendPath(displayName).build();
    }
  }

  public static Uri buildParentUri(Uri childUri) {
    final List<String> segments = childUri.getPathSegments();
    if (segments.isEmpty()) {
      // This is possibly a server or a workgroup. We don't know its exact parent, so just return
      // "smb://".
      return SMB_BASE_URI;
    }

    Uri.Builder builder = SMB_BASE_URI.buildUpon().authority(childUri.getAuthority());
    for (int i = 0; i < segments.size() - 1; ++i) {
      builder.appendPath(segments.get(i));
    }
    return builder.build();
  }

  public static boolean isServerUri(Uri uri) {
    return uri.getPathSegments().isEmpty() && !uri.getAuthority().isEmpty();
  }

  public static boolean isShareUri(Uri uri) {
    return uri.getPathSegments().size() == 1;
  }

  public static DocumentMetadata fromUri(Uri uri, SmbClient client) throws IOException {
    final List<String> pathSegments = uri.getPathSegments();
    if (pathSegments.isEmpty()) {
      throw new UnsupportedOperationException("Can't load metadata for workgroup or server.");
    }

    final StructStat stat = client.stat(uri.toString());
      final DirectoryEntry entry = new DirectoryEntry(
          OsConstants.S_ISDIR(stat.st_mode) ? DirectoryEntry.DIR : DirectoryEntry.FILE,
          "",
          uri.getLastPathSegment());
      final DocumentMetadata metadata = new DocumentMetadata(uri, entry);
      metadata.mStat.set(stat);

      return metadata;
    }

  public static DocumentMetadata createShare(String host, String share) {
    final Uri uri = SMB_BASE_URI.buildUpon().authority(host).encodedPath(share).build();
    return createShare(uri);
  }

  public static DocumentMetadata createServer(Uri uri) {
    return create(uri, DirectoryEntry.SERVER);
  }

  public static DocumentMetadata createShare(Uri uri) {
    return create(uri, DirectoryEntry.FILE_SHARE);
  }

  private static DocumentMetadata create(Uri uri, @DirectoryEntry.Type int type) {
    final DirectoryEntry entry =
            new DirectoryEntry(type, "", uri.getLastPathSegment());
    return new DocumentMetadata(uri, entry);
  }
}
