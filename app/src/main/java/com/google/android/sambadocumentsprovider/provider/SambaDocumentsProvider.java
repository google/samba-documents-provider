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

package com.google.android.sambadocumentsprovider.provider;

import static com.google.android.sambadocumentsprovider.base.DocumentIdHelper.toDocumentId;
import static com.google.android.sambadocumentsprovider.base.DocumentIdHelper.toRootId;
import static com.google.android.sambadocumentsprovider.base.DocumentIdHelper.toUri;
import static com.google.android.sambadocumentsprovider.base.DocumentIdHelper.toUriString;

import android.app.AuthenticationRequiredException;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.util.Log;
import com.google.android.sambadocumentsprovider.BuildConfig;
import com.google.android.sambadocumentsprovider.R;
import com.google.android.sambadocumentsprovider.SambaProviderApplication;
import com.google.android.sambadocumentsprovider.ShareManager;
import com.google.android.sambadocumentsprovider.ShareManager.MountedShareChangeListener;
import com.google.android.sambadocumentsprovider.TaskManager;
import com.google.android.sambadocumentsprovider.auth.AuthActivity;
import com.google.android.sambadocumentsprovider.base.AuthFailedException;
import com.google.android.sambadocumentsprovider.base.DirectoryEntry;
import com.google.android.sambadocumentsprovider.base.DocumentCursor;
import com.google.android.sambadocumentsprovider.browsing.NetworkBrowser;
import com.google.android.sambadocumentsprovider.cache.CacheResult;
import com.google.android.sambadocumentsprovider.cache.DocumentCache;
import com.google.android.sambadocumentsprovider.document.DocumentMetadata;
import com.google.android.sambadocumentsprovider.document.LoadChildrenTask;
import com.google.android.sambadocumentsprovider.base.OnTaskFinishedCallback;
import com.google.android.sambadocumentsprovider.document.LoadDocumentTask;
import com.google.android.sambadocumentsprovider.document.LoadStatTask;
import com.google.android.sambadocumentsprovider.mount.MountServerActivity;
import com.google.android.sambadocumentsprovider.nativefacade.SmbFacade;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SambaDocumentsProvider extends DocumentsProvider {

  public static final String AUTHORITY = "com.google.android.sambadocumentsprovider";

  private static final String TAG = "SambaDocumentsProvider";

  private static final String[] DEFAULT_ROOT_PROJECTION = {
      Root.COLUMN_ROOT_ID,
      Root.COLUMN_DOCUMENT_ID,
      Root.COLUMN_TITLE,
      Root.COLUMN_FLAGS,
      Root.COLUMN_ICON
  };

  private static final String[] DEFAULT_DOCUMENT_PROJECTION = {
      Document.COLUMN_DOCUMENT_ID,
      Document.COLUMN_DISPLAY_NAME,
      Document.COLUMN_FLAGS,
      Document.COLUMN_MIME_TYPE,
      Document.COLUMN_SIZE,
      Document.COLUMN_LAST_MODIFIED,
      Document.COLUMN_ICON
  };

  private final OnTaskFinishedCallback<Uri> mLoadDocumentCallback =
      new OnTaskFinishedCallback<Uri>() {
        @Override
        public void onTaskFinished(@Status int status, @Nullable Uri uri, Exception exception) {
          getContext().getContentResolver().notifyChange(toNotifyUri(uri), null, false);
        }
      };

  private final OnTaskFinishedCallback<DocumentMetadata> mLoadChildrenCallback =
      new OnTaskFinishedCallback<DocumentMetadata>() {
        @Override
        public void onTaskFinished(@Status int status, DocumentMetadata metadata,
            Exception exception) {
          // Notify remote side that we get the list even though we don't have the stat yet.
          // If it failed we still should notify the remote side that the loading failed.
          getContext().getContentResolver().notifyChange(
              toNotifyUri(metadata.getUri()), null, false);
        }
      };

  private final OnTaskFinishedCallback<String> mWriteFinishedCallback =
      new OnTaskFinishedCallback<String>() {
        @Override
        public void onTaskFinished(
            @Status int status, @Nullable String item, Exception exception) {
          final Uri uri = toUri(item);
          try (final CacheResult result = mCache.get(uri)) {
            if (result.getState() != CacheResult.CACHE_MISS) {
              result.getItem().reset();
            }
          }

          final Uri parentUri = DocumentMetadata.buildParentUri(uri);
          getContext().getContentResolver().notifyChange(toNotifyUri(parentUri), null, false);
        }
      };

  private final OnTaskFinishedCallback<List<String>> mLoadSharesFinishedCallback =
    new OnTaskFinishedCallback<List<String>>() {
      @Override
      public void onTaskFinished(
      @OnTaskFinishedCallback.Status int status,
      @Nullable List<String> item,
      @Nullable Exception exception) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Browsing callback");

        mBrowsingStorage = item;

        getContext().getContentResolver().notifyChange(
                toNotifyUri(toUri(NetworkBrowser.SMB_BROWSING_URI.toString())), null, false);
      }
    };

  private final MountedShareChangeListener mShareChangeListener = new MountedShareChangeListener() {
    @Override
    public void onMountedServerChange() {
      final Uri rootsUri = DocumentsContract.buildRootsUri(AUTHORITY);
      final ContentResolver resolver = getContext().getContentResolver();
      resolver.notifyChange(rootsUri, null, false);
    }
  };

  private ShareManager mShareManager;
  private SmbFacade mClient;
  private ByteBufferPool mBufferPool;
  private DocumentCache mCache;
  private TaskManager mTaskManager;
  private StorageManager mStorageManager;
  private NetworkBrowser mNetworkBrowser;

  private List<String> mBrowsingStorage;

  @Override
  public boolean onCreate() {
    final Context context = getContext();
    SambaProviderApplication.init(getContext());

    mClient = SambaProviderApplication.getSambaClient(context);
    mCache = SambaProviderApplication.getDocumentCache(context);
    mTaskManager = SambaProviderApplication.getTaskManager(context);
    mBufferPool = new ByteBufferPool();
    mShareManager = SambaProviderApplication.getServerManager(context);
    mShareManager.addListener(mShareChangeListener);
    mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
    mNetworkBrowser = SambaProviderApplication.getNetworkBrowser(context);

    return mClient != null;
  }

  @Override
  public Cursor queryRoots(String[] projection) throws FileNotFoundException {
    if(BuildConfig.DEBUG) Log.d(TAG, "Querying roots.");
    projection = (projection == null) ? DEFAULT_ROOT_PROJECTION : projection;

    MatrixCursor cursor = new MatrixCursor(projection);

    cursor.addRow(new Object[] {
      NetworkBrowser.SMB_BROWSING_URI.toString(),
      NetworkBrowser.SMB_BROWSING_URI.toString(),
      getContext().getResources().getString(R.string.browsing_root_name),
      0,
      R.drawable.ic_cloud,
    });

    for (String uri : mShareManager) {
      if (!mShareManager.isShareMounted(uri)) {
        continue;
      }

      final String name;
      final Uri parsedUri = Uri.parse(uri);
      try(CacheResult result = mCache.get(parsedUri)) {
        final DocumentMetadata metadata;
        if (result.getState() == CacheResult.CACHE_MISS) {
          metadata = DocumentMetadata.createShare(parsedUri);
          mCache.put(metadata);
        } else {
          metadata = result.getItem();
        }

        name = metadata.getDisplayName();

        cursor.addRow(new Object[] {
            toRootId(metadata),
            toDocumentId(parsedUri),
            name,
            Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_IS_CHILD | Root.FLAG_SUPPORTS_EJECT,
            R.drawable.ic_folder_shared
        });
      }

    }
    return cursor;
  }

  @Override
  public void ejectRoot(String rootId) {
    if (BuildConfig.DEBUG) Log.d(TAG, "Ejecting root: " + rootId);

    if (!mShareManager.unmountServer(rootId)) {
      throw new IllegalStateException("Failed to eject root: " + rootId);
    }
  }

  @Override
  public boolean isChildDocument(String parentDocumentId, String documentId) {
    final String parentUri = toUriString(parentDocumentId);
    final String childUri = toUriString(documentId);
    return childUri.startsWith(parentUri);
  }

  @Override
  public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
    if (BuildConfig.DEBUG) Log.d(TAG, "Querying document: " + documentId);
    projection = (projection == null) ? DEFAULT_DOCUMENT_PROJECTION : projection;

    final MatrixCursor cursor = new MatrixCursor(projection);
    final Uri uri = toUri(documentId);

    if (documentId.equals(NetworkBrowser.SMB_BROWSING_URI.toString())) {
      cursor.addRow(getCursorRowForBrowsingRoot(projection));

      return cursor;
    }

    try {
      try (CacheResult result = mCache.get(uri)) {

        final DocumentMetadata metadata;
        if (result.getState() == CacheResult.CACHE_MISS) {
          if (mShareManager.containsShare(uri.toString())) {
            metadata = DocumentMetadata.createShare(uri);
          } else {
            // There is no cache for this URI. Fetch it from remote side.
            metadata = DocumentMetadata.fromUri(uri, mClient);
          }
          mCache.put(metadata);
        } else {
          metadata = result.getItem();
        }

        cursor.addRow(getDocumentValues(projection, metadata));

        return cursor;
      }
    } catch (FileNotFoundException|RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Cursor queryChildDocuments(String documentId, String[] projection, String sortOrder)
      throws FileNotFoundException, AuthenticationRequiredException {
    if (BuildConfig.DEBUG) Log.d(TAG, "Querying children documents under " + documentId);
    projection = (projection == null) ? DEFAULT_DOCUMENT_PROJECTION : projection;

    if (documentId.equals(NetworkBrowser.SMB_BROWSING_URI.toString())) {
      return getFilesSharesCursor(projection);
    }

    final Uri uri = toUri(documentId);

    try {
      if (DocumentMetadata.isServerUri(uri)) {
        try (final CacheResult result = mCache.get(uri)) {
          if (result.getState() == CacheResult.CACHE_MISS) {
            DocumentMetadata metadata = DocumentMetadata.createServer(uri);
            mCache.put(metadata);
          }
        }
      }

      try (final CacheResult result = mCache.get(uri)) {
        boolean isLoading = false;
        final Bundle extra = new Bundle();
        final Uri notifyUri = toNotifyUri(uri);
        final DocumentCursor cursor = new DocumentCursor(projection);

        if (result.getState() == CacheResult.CACHE_MISS) {
          // Last loading failed... Just feed the bitter fruit.
          mCache.throwLastExceptionIfAny(uri);

          final LoadDocumentTask task =
              new LoadDocumentTask(uri, mClient, mCache, mLoadDocumentCallback);
          mTaskManager.runTask(uri, task);
          cursor.setLoadingTask(task);

          isLoading = true;
        } else { // At least we have something in cache.
          final DocumentMetadata metadata = result.getItem();

          if (!Document.MIME_TYPE_DIR.equals(metadata.getMimeType())) {
            throw new IllegalArgumentException(documentId + " is not a folder.");
          }

          metadata.throwLastChildUpdateExceptionIfAny();

          final Map<Uri, DocumentMetadata> childrenMap = metadata.getChildren();
          if (childrenMap == null || result.getState() == CacheResult.CACHE_EXPIRED) {
            final LoadChildrenTask task =
                new LoadChildrenTask(metadata, mClient, mCache, mLoadChildrenCallback);
            mTaskManager.runTask(uri, task);
            cursor.setLoadingTask(task);

            isLoading = true;
          }

          // Still return something even if the cache expired.
          if (childrenMap != null) {
            final Collection<DocumentMetadata> children = childrenMap.values();
            final Map<Uri, DocumentMetadata> docMap = new HashMap<>();
            for (DocumentMetadata child : children) {
              if (child.needsStat() && !child.hasLoadingStatFailed()) {
                docMap.put(child.getUri(), child);
              }
              cursor.addRow(getDocumentValues(projection, child));
            }
            if (!isLoading && !docMap.isEmpty()) {
              LoadStatTask task = new LoadStatTask(docMap, mClient,
                  new OnTaskFinishedCallback<Map<Uri, DocumentMetadata>>() {
                    @Override
                    public void onTaskFinished(
                        @Status int status, Map<Uri, DocumentMetadata> item, Exception exception) {
                      getContext().getContentResolver().notifyChange(notifyUri, null, false);
                    }
                  });
              mTaskManager.runTask(uri, task);
              cursor.setLoadingTask(task);

              isLoading = true;
            }
          }
        }
        extra.putBoolean(DocumentsContract.EXTRA_LOADING, isLoading);
        cursor.setExtras(extra);
        cursor.setNotificationUri(getContext().getContentResolver(), notifyUri);
        return cursor;
      }
    } catch (AuthFailedException e) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && DocumentMetadata.isShareUri(uri)) {
        throw new AuthenticationRequiredException(
                e, AuthActivity.createAuthIntent(getContext(), uri.toString()));
      } else {
        return buildErrorCursor(projection, R.string.view_folder_denied);
      }
    } catch (FileNotFoundException|RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Cursor buildErrorCursor(String[] projection, @StringRes int resId) {
    final String message = getContext().getString(resId);

    final Bundle extra = new Bundle();
    extra.putString(DocumentsContract.EXTRA_ERROR, message);

    final DocumentCursor cursor = new DocumentCursor(projection);
    cursor.setExtras(extra);

    return cursor;
  }

  private Object[] getDocumentValues(
      String[] projection, DocumentMetadata metadata) {
    Object[] row = new Object[projection.length];
    for (int i = 0; i < projection.length; ++i) {
      switch (projection[i]) {
        case Document.COLUMN_DOCUMENT_ID:
          row[i] = toDocumentId(metadata.getUri());
          break;
        case Document.COLUMN_DISPLAY_NAME:
          row[i] = metadata.getDisplayName();
          break;
        case Document.COLUMN_FLAGS:
          // Always assume it can write to it until the file operation fails. Windows 10 also does
          // the same thing.
          int flag = metadata.canCreateDocument() ? Document.FLAG_DIR_SUPPORTS_CREATE : 0;
          flag |= Document.FLAG_SUPPORTS_WRITE;
          flag |= Document.FLAG_SUPPORTS_DELETE;
          flag |= Document.FLAG_SUPPORTS_RENAME;
          flag |= Document.FLAG_SUPPORTS_REMOVE;
          flag |= Document.FLAG_SUPPORTS_MOVE;
          row[i] = flag;
          break;
        case Document.COLUMN_MIME_TYPE:
          row[i] = metadata.getMimeType();
          break;
        case Document.COLUMN_SIZE:
          row[i] = metadata.getSize();
          break;
        case Document.COLUMN_LAST_MODIFIED:
          row[i] = metadata.getLastModified();
          break;
        case Document.COLUMN_ICON:
          row[i] = metadata.getIconResourceId();
          break;
      }
    }
    return row;
  }

  private Object[] getCursorRowForServer(
          String[] projection,
          String server) {
    Object[] row = new Object[projection.length];

    for (int i = 0; i < projection.length; ++i) {
      switch (projection[i]) {
        case Document.COLUMN_DOCUMENT_ID:
          row[i] = NetworkBrowser.SMB_BROWSING_URI.toString() + server;
          break;
        case Document.COLUMN_DISPLAY_NAME:
          row[i] = server.isEmpty()
                  ? getContext().getResources().getString(R.string.browsing_root_name) : server;
          break;
        case Document.COLUMN_FLAGS:
          row[i] = 0;
          break;
        case Document.COLUMN_MIME_TYPE:
          row[i] = Document.MIME_TYPE_DIR;
          break;
        case Document.COLUMN_SIZE:
        case Document.COLUMN_LAST_MODIFIED:
          row[i] = null;
          break;
        case Document.COLUMN_ICON:
          row[i] = R.drawable.ic_server;
          break;
      }
    }

    return row;
  }

  private Object[] getCursorRowForBrowsingRoot(String[] projection) {
    return getCursorRowForServer(projection, "");
  }

  private Cursor getFilesSharesCursor(String[] projection) {
    final DocumentCursor cursor = new DocumentCursor(projection);

    final Uri uri = toUri(NetworkBrowser.SMB_BROWSING_URI.toString());

    if (mBrowsingStorage == null) {
      AsyncTask serversTask = mNetworkBrowser.getServersAsync(mLoadSharesFinishedCallback);

      Bundle extra = new Bundle();
      extra.putBoolean(DocumentsContract.EXTRA_LOADING, true);

      cursor.setNotificationUri(getContext().getContentResolver(), toNotifyUri(uri));
      cursor.setExtras(extra);
      cursor.setLoadingTask(serversTask);
    } else {
      for (String server : mBrowsingStorage) {
        cursor.addRow(getCursorRowForServer(projection, server));
      }

      mBrowsingStorage = null;
    }

    return cursor;
  }

  @Override
  public String createDocument(String parentDocumentId, String mimeType, String displayName)
      throws FileNotFoundException {
    try {
      final Uri parentUri = toUri(parentDocumentId);

      boolean isDir = Document.MIME_TYPE_DIR.equals(mimeType);
      final DirectoryEntry entry = new DirectoryEntry(
          isDir ? DirectoryEntry.DIR : DirectoryEntry.FILE,
          "", // comment
          displayName);
      final Uri uri = DocumentMetadata.buildChildUri(parentUri, entry);

      if (isDir) {
        mClient.mkdir(uri.toString());
      } else {
        mClient.createFile(uri.toString());
      }

      // Notify anyone who's listening on the parent folder.
      getContext().getContentResolver().notifyChange(toNotifyUri(parentUri), null, false);

      try (CacheResult result = mCache.get(uri)) {
        if (result.getState() != CacheResult.CACHE_MISS) {
          // It must be a file, and the file is truncated... Reset its cache.
          result.getItem().reset();

          // No need to update the cache anymore.
          return toDocumentId(uri);
        }
      }

      // Put it to cache without stat, newly created stuff is likely to be changed soon.
      DocumentMetadata metadata = new DocumentMetadata(uri, entry);
      mCache.put(metadata);

      return toDocumentId(uri);
    } catch (FileNotFoundException e) {
      throw e;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
    try {
      final Uri uri = toUri(documentId);
      final Uri parentUri = DocumentMetadata.buildParentUri(uri);
      if (parentUri.getPathSegments().isEmpty()) {
        throw new UnsupportedOperationException("Not support renaming a share/workgroup/server.");
      }
      final Uri newUri = DocumentMetadata.buildChildUri(parentUri, displayName);

      if (newUri == null) {
        throw new UnsupportedOperationException(displayName + " is not a valid name.");
      }

      mClient.rename(uri.toString(), newUri.toString());

      revokeDocumentPermission(documentId);

      getContext().getContentResolver().notifyChange(toNotifyUri(parentUri), null, false);

      // Update cache
      try (CacheResult result = mCache.get(uri)) {
        if (result.getState() != CacheResult.CACHE_MISS) {
          DocumentMetadata metadata = result.getItem();
          metadata.rename(newUri);
          mCache.remove(uri);
          mCache.put(metadata);
        }
      }

      return toDocumentId(newUri);
    } catch (FileNotFoundException e) {
      throw e;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void deleteDocument(String documentId) throws FileNotFoundException {
    final Uri uri = toUri(documentId);

    try {
      // Obtain metadata first to determine whether it's a file or a folder. We need to do
      // different things on them. Ignore our cache since it might be out of date.
      final DocumentMetadata metadata = DocumentMetadata.fromUri(uri, mClient);
      if (Document.MIME_TYPE_DIR.equals(metadata.getMimeType())) {
        recursiveDeleteFolder(metadata);
      } else {
        deleteFile(metadata);
      }

      final Uri notifyUri = toNotifyUri(DocumentMetadata.buildParentUri(uri));
      getContext().getContentResolver().notifyChange(notifyUri, null, false);

    } catch(FileNotFoundException e) {
      Log.w(TAG, documentId + " is not found. No need to delete it.", e);
      mCache.remove(uri);
      final Uri notifyUri = toNotifyUri(DocumentMetadata.buildParentUri(uri));
      getContext().getContentResolver().notifyChange(notifyUri, null, false);
    } catch(IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private void recursiveDeleteFolder(DocumentMetadata metadata) throws IOException {
    // Fetch the latest children just in case our cache is out of date.
    metadata.loadChildren(mClient);
    for (DocumentMetadata child : metadata.getChildren().values()) {
      if (Document.MIME_TYPE_DIR.equals(child.getMimeType())) {
        recursiveDeleteFolder(child);
      } else {
        deleteFile(child);
      }
    }

    final Uri uri = metadata.getUri();
    mClient.rmdir(uri.toString());
    mCache.remove(uri);

    revokeDocumentPermission(toDocumentId(uri));
  }

  private void deleteFile(DocumentMetadata metadata) throws IOException {
    final Uri uri = metadata.getUri();
    mClient.unlink(uri.toString());
    mCache.remove(uri);
    revokeDocumentPermission(toDocumentId(uri));
  }

  @Override
  public void removeDocument(String documentId, String parentDocumentId)
      throws FileNotFoundException {
    // documentId is hierarchical. It can only have one parent.
    deleteDocument(documentId);
  }

  @Override
  public String moveDocument(
      String sourceDocumentId, String sourceParentDocumentId, String targetParentDocumentId)
    throws FileNotFoundException {
    try {
      final Uri uri = toUri(sourceDocumentId);
      final Uri targetParentUri = toUri(targetParentDocumentId);

      if (!Objects.equals(uri.getAuthority(), targetParentUri.getAuthority())) {
        throw new UnsupportedOperationException("Instant move across services are not supported.");
      }

      final List<String> pathSegmentsOfSource = uri.getPathSegments();
      final List<String> pathSegmentsOfTargetParent = targetParentUri.getPathSegments();
      if (pathSegmentsOfSource.isEmpty() ||
          pathSegmentsOfTargetParent.isEmpty() ||
          !Objects.equals(pathSegmentsOfSource.get(0), pathSegmentsOfTargetParent.get(0))) {
        throw new UnsupportedOperationException("Instance move across shares are not supported.");
      }

      final Uri targetUri = DocumentMetadata
          .buildChildUri(targetParentUri, uri.getLastPathSegment());
      mClient.rename(uri.toString(), targetUri.toString());

      revokeDocumentPermission(sourceDocumentId);

      getContext().getContentResolver()
          .notifyChange(toNotifyUri(DocumentMetadata.buildParentUri(uri)), null, false);
      getContext().getContentResolver().notifyChange(toNotifyUri(targetParentUri), null, false);

      try (CacheResult result = mCache.get(uri)) {
        if (result.getState() != CacheResult.CACHE_MISS) {
          final DocumentMetadata metadata = result.getItem();
          metadata.rename(targetUri);

          mCache.remove(uri);
          mCache.put(metadata);
        }
      }

      return toDocumentId(targetUri);
    } catch(FileNotFoundException e) {
      throw e;
    } catch(IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public ParcelFileDescriptor openDocument(String documentId, String mode,
      CancellationSignal cancellationSignal) throws FileNotFoundException {
    if (BuildConfig.DEBUG) Log.d(TAG, "Opening document " + documentId + " with mode " + mode);

    try {
      final String uri = toUriString(documentId);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        OnTaskFinishedCallback<String> callback =
            mode.contains("w") ? mWriteFinishedCallback : null;
        return mClient.openProxyFile(
                uri,
                mode,
                mStorageManager,
                mBufferPool,
                callback);
      } else {
        return openDocumentPreO(uri, mode);
      }

    } catch(FileNotFoundException e) {
      throw e;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private ParcelFileDescriptor openDocumentPreO(String uri, String mode) throws IOException {

    // Doesn't support complex mode on pre-O devices.
    if (!"r".equals(mode) && !"w".equals(mode)) {
      throw new UnsupportedOperationException("Mode " + mode + " is not supported");
    }

    ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
    switch (mode) {
      case "r": {
        final ReadFileTask task = new ReadFileTask(
            uri, mClient, pipe[1], mBufferPool);
        mTaskManager.runIoTask(task);
      }
      return pipe[0];
      case "w": {
        final WriteFileTask task =
            new WriteFileTask(uri, mClient, pipe[0], mBufferPool, mWriteFinishedCallback);
        mTaskManager.runIoTask(task);
        return pipe[1];
      }
      default:
        // Should never happen.
        pipe[0].close();
        pipe[1].close();
        throw new UnsupportedOperationException("Mode " + mode + " is not supported.");
    }
  }

  private Uri toNotifyUri(Uri uri) {
    return DocumentsContract.buildDocumentUri(AUTHORITY, toDocumentId(uri));
  }
}
