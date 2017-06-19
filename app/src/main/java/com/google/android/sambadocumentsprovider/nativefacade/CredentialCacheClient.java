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

package com.google.android.sambadocumentsprovider.nativefacade;

import android.os.Bundle;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

class CredentialCacheClient extends BaseClient implements CredentialCache {

  @IntDef( {PUT_CREDENTIAL, REMOVE_CREDENTIAL })
  @Retention(RetentionPolicy.SOURCE)
  @interface Operation {}
  private static final int PUT_CREDENTIAL = 1;
  private static final int REMOVE_CREDENTIAL = 2;

  private static final String URI_KEY = "URI";
  private static final String WORKGROUP_KEY = "WORKGROUP";
  private static final String USERNAME_KEY = "USERNAME";
  private static final String PASSWORD_KEY = "PASSWORD";

  CredentialCacheClient(Looper looper, CredentialCache credentialCacheImpl) {
    mHandler = new CredentialCacheHandler(looper, credentialCacheImpl);
  }

  @Override
  public void putCredential(String uri, String workgroup, String username, String password) {
    try (final MessageValues messageValues = MessageValues.obtain()) {
      final Message msg = mHandler.obtainMessage(PUT_CREDENTIAL, messageValues);

      final Bundle args = msg.getData();
      args.putString(URI_KEY, uri);
      args.putString(WORKGROUP_KEY, workgroup);
      args.putString(USERNAME_KEY, username);
      args.putString(PASSWORD_KEY, password);

      enqueue(msg);
    }
  }

  @Override
  public void removeCredential(String uri) {
    try (final MessageValues messageValues = MessageValues.obtain()) {
      final Message msg = mHandler.obtainMessage(REMOVE_CREDENTIAL, messageValues);

      final Bundle args = msg.getData();
      args.putString(URI_KEY, uri);

      enqueue(msg);
    }
  }

  private static class CredentialCacheHandler extends BaseHandler {

    private CredentialCache mCredentialCacheImpl;
    private CredentialCacheHandler(Looper looper, CredentialCache credentialCacheImpl) {
      super(looper);
      mCredentialCacheImpl = credentialCacheImpl;
    }

    @Override
    void processMessage(Message msg) {
      final Bundle args = msg.peekData();
      final String uri = args.getString(URI_KEY);
      switch (msg.what) {
        case PUT_CREDENTIAL: {
          final String workgroup = args.getString(WORKGROUP_KEY);
          final String username = args.getString(USERNAME_KEY);
          final String password = args.getString(PASSWORD_KEY);
          mCredentialCacheImpl.putCredential(uri, workgroup, username, password);
          break;
        }
        case REMOVE_CREDENTIAL: {
          mCredentialCacheImpl.removeCredential(uri);
          break;
        }
        default:
          throw new UnsupportedOperationException("Unknown operation " + msg.what);
      }
    }
  }
}
