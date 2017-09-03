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
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.sambadocumentsprovider.TaskManager;
import com.google.android.sambadocumentsprovider.base.DirectoryEntry;
import com.google.android.sambadocumentsprovider.base.OnTaskFinishedCallback;
import com.google.android.sambadocumentsprovider.browsing.broadcast.BroadcastBrowsingProvider;
import com.google.android.sambadocumentsprovider.nativefacade.SmbClient;
import com.google.android.sambadocumentsprovider.nativefacade.SmbDir;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * This class discovers Samba servers and shares under them available on the local network.
 */
public class NetworkBrowser {
  private static final Uri SMB_BROWSING_URI = Uri.parse("smb://");

  private static final String TAG = "NetworkBrowser";

  private final NetworkBrowsingProvider mMasterProvider;
  private final NetworkBrowsingProvider mBroadcastProvider;
  private final TaskManager mTaskManager;
  private final SmbClient mClient;

  public NetworkBrowser(SmbClient client, TaskManager taskManager) {
    mMasterProvider = new MasterBrowsingProvider(client);
    mBroadcastProvider = new BroadcastBrowsingProvider();
    mTaskManager = taskManager;
    mClient = client;
  }

  /**
   * Asynchronously get available servers and shares under them.
   * A server name is mapped to the list of its children.
   */
  public AsyncTask getSharesAsync(OnTaskFinishedCallback<Map<String, List<String>>> callback) {
    AsyncTask<Void, Void, Map<String, List<String>>> loadServersTask = new LoadServersTask(callback);

    mTaskManager.runTask(SMB_BROWSING_URI, loadServersTask);

    return loadServersTask;
  }

  private Map<String, List<String>> getShares() throws BrowsingException {
    List<String> servers = getServers();

    Map<String, List<String>> shares = new HashMap<>();

    for (String server : servers) {
      try {
        shares.put(server, getSharesForServer(server));
      } catch (IOException e) {
        Log.e(TAG, "Failed to load shares for server", e);
      }
    }

    return shares;
  }

  private List<String> getSharesForServer(String server) throws IOException {
    List<String> shares = new ArrayList<>();

    String serverUri = SMB_BROWSING_URI + server;
    SmbDir serverDir = mClient.openDir(serverUri);

    DirectoryEntry shareEntry;
    while ((shareEntry = serverDir.readDir()) != null) {
      if (shareEntry.getType() == DirectoryEntry.FILE_SHARE) {
        shares.add(serverUri + "/" + shareEntry.getName().trim());
      } else {
        Log.i(TAG, "Unsupported entry type: " + shareEntry.getType());
      }
    }

    return shares;
  }

  private List<String> getServers() throws BrowsingException {
    List<String> servers = null;

    try {
      servers = mMasterProvider.getServers();
    } catch (BrowsingException e) {
      Log.e(TAG, "Master browsing failed", e);
    }

    if (servers == null || servers.isEmpty()) {
      return mBroadcastProvider.getServers();
    }

    return servers;
  }

  private class LoadServersTask extends AsyncTask<Void, Void, Map<String, List<String>>> {
    final OnTaskFinishedCallback<Map<String, List<String>>>  mCallback;

    private BrowsingException mException;

    LoadServersTask(OnTaskFinishedCallback<Map<String, List<String>>>  callback) {
      mCallback = callback;
    }

    @Override
    protected Map<String, List<String>>  doInBackground(Void... voids) {
      try {
        return getShares();
      } catch (BrowsingException e) {
        Log.e(TAG, "Failed to load data for network browsing: ", e);
        mException = e;
        return null;
      }
    }

    protected void onPostExecute(Map<String, List<String>>  servers) {
      if (servers != null) {
        mCallback.onTaskFinished(OnTaskFinishedCallback.SUCCEEDED, servers, null);
      } else {
        mCallback.onTaskFinished(OnTaskFinishedCallback.FAILED, null, mException);
      }
    }
  }
}
