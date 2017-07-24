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
import com.google.android.sambadocumentsprovider.base.OnTaskFinishedCallback;
import com.google.android.sambadocumentsprovider.nativefacade.SmbClient;
import com.google.android.sambadocumentsprovider.nativefacade.SmbDir;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class NetworkBrowser {
  public static final Uri SMB_BROWSING_URI = Uri.parse("smb://");

  private static final String TAG = "NetworkBrowser";
  
  private final NetworkBrowsingProvider mMasterProvider;

  private final ExecutorService mExecutor = Executors.newCachedThreadPool();
  private final Map<Uri, Future> mTasks = new HashMap<>();

  public NetworkBrowser(SmbClient client) {
    mMasterProvider = new MasterBrowsingProvider(client);
  }

  public Future getServersAsync(OnTaskFinishedCallback<List<SmbServer>> callback) {
    synchronized (mTasks) {
      Future getServersTask = mTasks.get(SMB_BROWSING_URI);

      if (getServersTask == null) {
        getServersTask = mExecutor.submit(new LoadServersTask(callback, this));
        mTasks.put(SMB_BROWSING_URI, getServersTask);
      }

      return getServersTask;
    }
  }

  private List<SmbServer> getServers() throws IOException {
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

  private static class LoadServersTask implements Runnable {
    final OnTaskFinishedCallback<List<SmbServer>> mCallback;
    final WeakReference<NetworkBrowser> mBrowser;

    LoadServersTask(OnTaskFinishedCallback<List<SmbServer>> callback, NetworkBrowser browser) {
      mCallback = callback;
      mBrowser = new WeakReference<>(browser);
    }

    @Override
    public void run() {
      try {
        List<SmbServer> servers = loadData();
        mCallback.onTaskFinished(OnTaskFinishedCallback.SUCCEEDED, servers, null);
      } catch (IOException e) {
        Log.e(TAG, "Failed to load data for network browsing: ", e);
        mCallback.onTaskFinished(OnTaskFinishedCallback.FAILED, null, e);
      }
    }

    List<SmbServer> loadData() throws IOException {
      return mBrowser.get().getServers();
    }
  }
}
