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
import com.google.android.sambadocumentsprovider.nativefacade.SmbClient;
import com.google.android.sambadocumentsprovider.nativefacade.SmbDir;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

public class NetworkBrowser {
  public static final Uri SMB_BROWSING_URI = Uri.parse("smb://");

  private static final String TAG = "NetworkBrowser";
  
  private final NetworkBrowsingProvider mMasterProvider;
  private final TaskManager mTaskManager;

  private final Map<Uri, Future> mTasks = new HashMap<>();

  public NetworkBrowser(SmbClient client, TaskManager taskManager) {
    mMasterProvider = new MasterBrowsingProvider(client);
    mTaskManager = taskManager;
  }

  public AsyncTask getServersAsync(OnTaskFinishedCallback<List<SmbServer>> callback) {
    AsyncTask<Void, Void, List<SmbServer>> loadServersTask = new LoadServersTask(callback, this);

    mTaskManager.runTask(SMB_BROWSING_URI, loadServersTask);

    return loadServersTask;
  }

  private List<SmbServer> getServers() throws BrowsingException {
    return mMasterProvider.getServers();
  }

  static List<DirectoryEntry> getDirectoryChildren(SmbDir dir) throws IOException {
    List<DirectoryEntry> children = new ArrayList<>();

    DirectoryEntry currentEntry;
    while ((currentEntry = dir.readDir()) != null) {
      children.add(currentEntry);
    }

    return children;
  }

  private static class LoadServersTask extends AsyncTask<Void, Void, List<SmbServer>> {
    final OnTaskFinishedCallback<List<SmbServer>> mCallback;
    final WeakReference<NetworkBrowser> mBrowser;

    private BrowsingException mException;

    LoadServersTask(OnTaskFinishedCallback<List<SmbServer>> callback, NetworkBrowser browser) {
      mCallback = callback;
      mBrowser = new WeakReference<>(browser);
    }

    List<SmbServer> loadData() throws BrowsingException {
      return mBrowser.get().getServers();
    }

    @Override
    protected List<SmbServer> doInBackground(Void... voids) {
      try {
        List<SmbServer> servers = loadData();
        return servers;
      } catch (BrowsingException e) {
        Log.e(TAG, "Failed to load data for network browsing: ", e);
        mException = e;
        return null;
      }
    }

    protected void onPostExecute(List<SmbServer> servers) {
      if (servers != null) {
        mCallback.onTaskFinished(OnTaskFinishedCallback.SUCCEEDED, servers, null);
      } else {
        mCallback.onTaskFinished(OnTaskFinishedCallback.FAILED, null, mException);
      }
    }
  }
}
