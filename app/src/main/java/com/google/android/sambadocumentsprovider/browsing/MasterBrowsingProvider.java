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

import com.google.android.sambadocumentsprovider.base.DirectoryEntry;
import com.google.android.sambadocumentsprovider.nativefacade.SmbClient;
import com.google.android.sambadocumentsprovider.nativefacade.SmbDir;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class MasterBrowsingProvider implements NetworkBrowsingProvider {
  private static final String MASTER_BROWSING_DIR = "smb://";

  private final SmbClient mClient;

  MasterBrowsingProvider(SmbClient client) {
    mClient = client;
  }

  @Override
  public List<String> getServers() throws BrowsingException {
    List<String> serversList = new ArrayList<>();

    try {
      SmbDir rootDir = mClient.openDir(MASTER_BROWSING_DIR);

      List<DirectoryEntry> workgroups = getDirectoryChildren(rootDir);
      for (DirectoryEntry workgroup : workgroups) {
        if (workgroup.getType() == DirectoryEntry.WORKGROUP) {
          List<DirectoryEntry> servers = getDirectoryChildren
                  (mClient.openDir(MASTER_BROWSING_DIR + workgroup.getName()));

          for (DirectoryEntry server : servers) {
            if (server.getType() == DirectoryEntry.SERVER) {
              serversList.add(server.getName());
            }
          }
        }
      }
    } catch (IOException e) {
      throw new BrowsingException(e.getMessage());
    }

    return serversList;
  }

  private static List<DirectoryEntry> getDirectoryChildren(SmbDir dir) throws IOException {
    List<DirectoryEntry> children = new ArrayList<>();

    DirectoryEntry currentEntry;
    while ((currentEntry = dir.readDir()) != null) {
      children.add(currentEntry);
    }

    return children;
  }
}
