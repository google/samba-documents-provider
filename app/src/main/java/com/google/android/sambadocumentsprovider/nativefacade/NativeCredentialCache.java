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

class NativeCredentialCache implements CredentialCache {

  static {
    System.loadLibrary("samba_client");
  }

  private long mNativeHandler;

  NativeCredentialCache() {
    mNativeHandler = nativeInit();
  }

  long getNativeHandler() {
    return mNativeHandler;
  }

  @Override
  public void putCredential(String uri, String workgroup, String username, String password) {
    putCredential(mNativeHandler, uri, workgroup, username, password);
  }

  @Override
  public void removeCredential(String uri) {
    removeCredential(mNativeHandler, uri);
  }

  private native long nativeInit();

  private native void putCredential(
      long handler, String uri, String workgroup, String username, String password);

  private native void removeCredential(long handler, String uri);
}
