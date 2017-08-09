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

package com.google.android.sambadocumentsprovider.auth;

import android.net.Uri;

import com.google.android.sambadocumentsprovider.ShareManager;
import com.google.android.sambadocumentsprovider.base.BiResultTask;
import com.google.android.sambadocumentsprovider.base.OnTaskFinishedCallback;
import com.google.android.sambadocumentsprovider.document.DocumentMetadata;
import com.google.android.sambadocumentsprovider.nativefacade.SmbClient;

import java.io.IOException;

class AuthorizationTask extends BiResultTask<Void, Void, Void> {
  private static final String TAG = "AuthorizationTask";

  private final String mUri;
  private final String mUser;
  private final String mPassword;
  private final String mDomain;

  private final boolean mShouldPin;
  private final ShareManager mShareManager;
  private final SmbClient mClient;
  private final OnTaskFinishedCallback<Void> mCallback;

  AuthorizationTask(String uri, String user, String password, String domain, boolean shouldPin,
                    ShareManager shareManager, SmbClient client,
                    OnTaskFinishedCallback<Void> callback) {
    mUri = uri;
    mUser = user;
    mPassword = password;
    mDomain = domain;

    mShouldPin = shouldPin;
    mShareManager = shareManager;
    mClient = client;
    mCallback = callback;
  }

  @Override
  public Void run(Void... voids) throws Exception {
    final DocumentMetadata shareMetadata = DocumentMetadata.createShare(Uri.parse(mUri));

    final ShareManager.ShareMountChecker mountChecker = new ShareManager.ShareMountChecker() {
      @Override
      public void checkShareMounting() throws IOException {
        shareMetadata.loadChildren(mClient);
      }
    };

    mShareManager.addOrUpdateServer(mUri, mDomain, mUser, mPassword, mountChecker, mShouldPin);

    return null;
  }

  @Override
  public void onSucceeded(Void aVoid) {
    mCallback.onTaskFinished(OnTaskFinishedCallback.SUCCEEDED, null, null);
  }

  @Override
  public void onFailed(Exception e) {
    mCallback.onTaskFinished(OnTaskFinishedCallback.FAILED, null, e);
  }
}
