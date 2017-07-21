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

package com.google.android.sambadocumentsprovider.browsing;

import android.net.Uri;
import android.util.Log;

import com.google.android.sambadocumentsprovider.base.DirectoryEntry;
import com.google.android.sambadocumentsprovider.document.DocumentMetadata;
import com.google.android.sambadocumentsprovider.nativefacade.SmbClient;
import com.google.android.sambadocumentsprovider.nativefacade.SmbDir;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by rthakohov on 7/20/17.
 */

public class NetworkBrowser {
  public static final Uri SMB_BROWSING_URI = Uri.parse("smb://");

  private static final String TAG = "NetworkBrowser";

  private final SmbClient mClient;
  private final DocumentMetadata mBrowsingRoot;

  public NetworkBrowser(SmbClient client) {
    mClient = client;

    final DirectoryEntry entry = new DirectoryEntry(DirectoryEntry.BROWSING_ROOT, "", "");
    mBrowsingRoot = new DocumentMetadata(SMB_BROWSING_URI, entry);
  }

  public DocumentMetadata getBrowsingRoot() {
    return mBrowsingRoot;
  }

  public List<DocumentMetadata> getServers() {
    return new ArrayList<>();
  }

  public List<DocumentMetadata> getSharesForServer(Uri serverUri) {
    List<DocumentMetadata> shares = new ArrayList<>();

    try {
      SmbDir serverDir = mClient.openDir(serverUri.toString());

      List<DirectoryEntry> shareEntries = getDirectoryChildren(serverDir);
      for (DirectoryEntry shareEntry : shareEntries) {
        if (shareEntry.getType() == DirectoryEntry.FILE_SHARE) {
          Uri shareUri = DocumentMetadata.buildChildUri(serverUri, shareEntry);
          shares.add(new DocumentMetadata(shareUri, shareEntry));
        }
      }
    } catch (IOException e) {
      Log.e(TAG, "Failed to load shares for server: ", e);
    }

    return shares;
  }

  static List<DirectoryEntry> getDirectoryChildren(SmbDir dir) throws IOException {
    List<DirectoryEntry> children = new ArrayList<>();

    DirectoryEntry currentEntry;
    while ((currentEntry = dir.readDir()) != null) {
      children.add(currentEntry);
    }

    return children;
  }
}
