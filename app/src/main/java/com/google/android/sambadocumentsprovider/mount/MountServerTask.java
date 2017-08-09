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

package com.google.android.sambadocumentsprovider.mount;

import android.net.Uri;
import android.util.Log;
import com.google.android.sambadocumentsprovider.ShareManager;
import com.google.android.sambadocumentsprovider.ShareManager.ShareMountChecker;
import com.google.android.sambadocumentsprovider.base.BiResultTask;
import com.google.android.sambadocumentsprovider.base.OnTaskFinishedCallback;
import com.google.android.sambadocumentsprovider.cache.DocumentCache;
import com.google.android.sambadocumentsprovider.document.DocumentMetadata;
import com.google.android.sambadocumentsprovider.nativefacade.SmbClient;
import java.io.IOException;
import java.util.Map;

class MountServerTask extends BiResultTask<Void, Void, Void> {

  private static final String TAG = "MountServerTask";

  private final DocumentMetadata mMetadata;
  private final String mDomain;
  private final String mUsername;
  private final String mPassword;
  private final SmbClient mClient;
  private final DocumentCache mCache;
  private final ShareManager mShareManager;
  private final OnTaskFinishedCallback<Void> mCallback;

  private final ShareMountChecker mChecker = new ShareMountChecker() {
    @Override
    public void checkShareMounting() throws IOException {
        mMetadata.loadChildren(mClient);
    }
  };

  MountServerTask(
      DocumentMetadata metadata,
      String domain,
      String username,
      String password,
      SmbClient client,
      DocumentCache cache,
      ShareManager shareManager,
      OnTaskFinishedCallback<Void> callback) {
    mMetadata = metadata;
    mDomain = domain;
    mUsername = username;
    mPassword = password;
    mClient = client;
    mCache = cache;
    mShareManager = shareManager;
    mCallback = callback;
  }

  @Override
  public Void run(Void... args) throws IOException {
    mShareManager.addServer(
        mMetadata.getUri().toString(), mDomain, mUsername, mPassword, mChecker, true);
    return null;
  }

  @Override
  public void onSucceeded(Void arg) {
    final Map<Uri, DocumentMetadata> children = mMetadata.getChildren();
    for (DocumentMetadata metadata : children.values()) {
      mCache.put(metadata);
    }

    mCallback.onTaskFinished(OnTaskFinishedCallback.SUCCEEDED, null, null);
  }

  @Override
  public void onFailed(Exception e) {
    Log.e(TAG, "Failed to mount share.", e);
    mCallback.onTaskFinished(OnTaskFinishedCallback.FAILED, null, e);
  }

  @Override
  public void onCancelled(Void arg) {
    // User cancelled the task, unmount it regardless of its result.
    mShareManager.unmountServer(mMetadata.getUri().toString());
  }
}
