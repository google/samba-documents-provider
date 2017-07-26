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

  public AsyncTask getServersAsync(OnTaskFinishedCallback<List<String>> callback) {
    AsyncTask<Void, Void, List<String>> loadServersTask = new LoadServersTask(callback);

    mTaskManager.runTask(SMB_BROWSING_URI, loadServersTask);

    return loadServersTask;
  }

  private List<String> getServers() throws BrowsingException {
    return mMasterProvider.getServers();
  }

  private class LoadServersTask extends AsyncTask<Void, Void, List<String>> {
    final OnTaskFinishedCallback<List<String>> mCallback;

    private BrowsingException mException;

    LoadServersTask(OnTaskFinishedCallback<List<String>> callback) {
      mCallback = callback;
    }

    List<String> loadData() throws BrowsingException {
      return getServers();
    }

    @Override
    protected List<String> doInBackground(Void... voids) {
      try {
        return loadData();
      } catch (BrowsingException e) {
        Log.e(TAG, "Failed to load data for network browsing: ", e);
        mException = e;
        return null;
      }
    }

    protected void onPostExecute(List<String> servers) {
      if (servers != null) {
        mCallback.onTaskFinished(OnTaskFinishedCallback.SUCCEEDED, servers, null);
      } else {
        mCallback.onTaskFinished(OnTaskFinishedCallback.FAILED, null, mException);
      }
    }
  }
}
