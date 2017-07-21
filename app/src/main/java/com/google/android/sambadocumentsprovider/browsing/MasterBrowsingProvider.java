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
import com.google.android.sambadocumentsprovider.base.DocumentIdHelper;
import com.google.android.sambadocumentsprovider.document.DocumentMetadata;
import com.google.android.sambadocumentsprovider.nativefacade.SmbClient;
import com.google.android.sambadocumentsprovider.nativefacade.SmbDir;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.google.android.sambadocumentsprovider.browsing.NetworkBrowser.getDirectoryChildren;

/**
 * Created by rthakohov on 7/20/17.
 */

public class MasterBrowsingProvider implements NetworkBrowsingProvider {
  private static final String TAG = "MasterBrowsingProvider";
  private static final String MASTER_BROWSING_DIR = "smb://";

  private final SmbClient mClient;

  public MasterBrowsingProvider(SmbClient client) {
    mClient = client;
  }

  @Override
  public List<DocumentMetadata> getServers() {
    List<DocumentMetadata> serversList = new ArrayList<>();

    try {
      Uri rootUri = DocumentIdHelper.toUri(MASTER_BROWSING_DIR);
      SmbDir rootDir = mClient.openDir(rootUri.toString());

      List<DirectoryEntry> workgroups = getDirectoryChildren(rootDir);
      for (DirectoryEntry workgroup : workgroups) {
        if (workgroup.getType() == DirectoryEntry.WORKGROUP) {
          Uri workgroupUri = DocumentMetadata.buildChildUri(rootUri, workgroup);

          List<DirectoryEntry> servers = getDirectoryChildren
                  (mClient.openDir(workgroupUri.toString()));

          for (DirectoryEntry server : servers) {
            if (server.getType() == DirectoryEntry.SERVER) {
              Uri serverUri = DocumentMetadata.buildChildUri(rootUri, server);
              serversList.add(new DocumentMetadata(serverUri, server));
            }
          }
        }
      }
    } catch (IOException e) {
      Log.e(TAG, "Failed to open dir for master browsing: ", e);
      // TODO: Exceptions handling
    }

    return serversList;
  }


}
