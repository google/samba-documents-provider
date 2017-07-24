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

  private final SmbClient mClient;
  private final NetworkBrowsingProvider mMasterProvider;

  private final ExecutorService mExecutor = Executors.newCachedThreadPool();
  private final Map<Uri, Future> mTasks = new HashMap<>();

  public NetworkBrowser(SmbClient client) {
    mClient = client;
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

  public Future getSharesForServerAsync(
          Uri serverUri,
          OnTaskFinishedCallback<List<String>> callback) {
    synchronized (mTasks) {
      Future getSharesTask = mTasks.get(SMB_BROWSING_URI);

      if (getSharesTask == null) {
        getSharesTask = mExecutor.submit(new LoadSharesTask(callback, serverUri, mClient, this));
        mTasks.put(serverUri, getSharesTask);
      }

      return getSharesTask;
    }
  }

  private List<SmbServer> getServers() throws IOException {
    return mMasterProvider.getServers();
  }

  public List<String> getSharesForServer(Uri serverUri) {
    List<String> shares = new ArrayList<>();

    try {
      SmbDir serverDir = mClient.openDir(serverUri.toString());

      List<DirectoryEntry> shareEntries = getDirectoryChildren(serverDir);
      for (DirectoryEntry shareEntry : shareEntries) {
        if (shareEntry.getType() == DirectoryEntry.FILE_SHARE) {
          shares.add(shareEntry.getName());
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

  private static abstract class NetworkBrowsingTask<T> implements Runnable {
    final OnTaskFinishedCallback<T> mCallback;
    final WeakReference<NetworkBrowser> mBrowser;

    NetworkBrowsingTask(OnTaskFinishedCallback<T> callback, NetworkBrowser browser) {
      mCallback = callback;
      mBrowser = new WeakReference<>(browser);
    }

    @Override
    public void run() {
      try {
        T data = loadData();
        mCallback.onTaskFinished(OnTaskFinishedCallback.SUCCEEDED, data, null);
      } catch (IOException e) {
        Log.e(TAG, "Failed to load data for network browsing: ", e);
        mCallback.onTaskFinished(OnTaskFinishedCallback.FAILED, null, e);
      }
    }

    abstract T loadData() throws IOException;
  }

  private static class  LoadServersTask extends NetworkBrowsingTask<List<SmbServer>> {
    LoadServersTask(OnTaskFinishedCallback<List<SmbServer>> callback, NetworkBrowser browser) {
      super(callback, browser);
    }

    List<SmbServer> loadData() throws IOException {
      return super.mBrowser.get().getServers();
    }
  }

  private static class  LoadSharesTask extends NetworkBrowsingTask<List<String>> {
    final Uri mServerUri;
    final SmbClient mClient;

    LoadSharesTask(OnTaskFinishedCallback<List<String>> callback,
                    Uri serverUri,
                    SmbClient client,
                    NetworkBrowser browser) {
      super(callback, browser);

      mServerUri = serverUri;
      mClient = client;
    }

    List<String> loadData() throws IOException {
      return super.mBrowser.get().getSharesForServer(mServerUri);
    }
  }
}
