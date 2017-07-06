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

import android.os.ParcelFileDescriptor;
import android.os.ProxyFileDescriptorCallback;
import android.os.storage.StorageManager;
import android.system.ErrnoException;
import android.system.StructStat;
import com.google.android.sambadocumentsprovider.BuildConfig;
import com.google.android.sambadocumentsprovider.base.DirectoryEntry;
import java.io.IOException;
import java.util.List;

/**
 * Java facade for libsmbclient native library.
 *
 * This class is not thread safe.
 */
class NativeSambaFacade implements SmbClient {

  private final long mCredentialCacheHandler;
  private long mNativeHandler;

  static {
    System.loadLibrary("samba_client");
  }

  NativeSambaFacade(NativeCredentialCache cache) {
    mCredentialCacheHandler = cache.getNativeHandler();
  }

  private boolean isInitialized() {
    return mNativeHandler != 0;
  }

  @Override
  public void reset() {
    if (isInitialized()) {
      nativeDestroy(mNativeHandler);
    }
    mNativeHandler = nativeInit(BuildConfig.DEBUG, mCredentialCacheHandler);
  }

  @Override
  public SmbDir openDir(String uri) throws IOException {
    try {
      checkNativeHandler();
      return new SambaDir(mNativeHandler, openDir(mNativeHandler, uri));
    } catch (ErrnoException e) {
      throw new IOException("Failed to read directory " + uri, e);
    }
  }

  @Override
  public StructStat stat(String uri) throws IOException {
    try {
      checkNativeHandler();
      return stat(mNativeHandler, uri);
    } catch (ErrnoException e) {
      throw new IOException("Failed to get stat of " + uri, e);
    }
  }

  @Override
  public void createFile(String uri) throws IOException {
    try {
      checkNativeHandler();
      createFile(mNativeHandler, uri);
    } catch(ErrnoException e) {
      throw new IOException("Failed to create file at " + uri, e);
    }
  }

  @Override
  public void mkdir(String uri) throws IOException {
    try {
      checkNativeHandler();
      mkdir(mNativeHandler, uri);
    } catch(ErrnoException e) {
      throw new IOException("Failed to make directory at " + uri, e);
    }
  }

  @Override
  public void rename(String uri, String newUri) throws IOException {
    try {
      checkNativeHandler();
      rename(mNativeHandler, uri, newUri);
    } catch(ErrnoException e) {
      throw new IOException("Failed to rename " + uri + " to " + newUri, e);
    }
  }

  @Override
  public void unlink(String uri) throws IOException {
    try {
      checkNativeHandler();
      unlink(mNativeHandler, uri);
    } catch(ErrnoException e) {
      throw new IOException("Failed to unlink " + uri, e);
    }
  }

  @Override
  public void rmdir(String uri) throws IOException {
    try {
      checkNativeHandler();
      rmdir(mNativeHandler, uri);
    } catch(ErrnoException e) {
      throw new IOException("Failed to rmdir " + uri, e);
    }
  }

  @Override
  public SambaFile openFile(String uri, String mode) throws IOException {
    try {
      checkNativeHandler();
      return new SambaFile(mNativeHandler, openFile(mNativeHandler, uri, mode));
    } catch(ErrnoException e) {
      throw new IOException("Failed to open " + uri, e);
    }
  }

  private void checkNativeHandler() {
    if (!isInitialized()) {
      throw new IllegalStateException("Samba client is not initialized.");
    }
  }

  private native long nativeInit(boolean debug, long cacheHandler);

  private native void nativeDestroy(long handler);

  private native int openDir(long handler, String uri) throws ErrnoException;

  private native StructStat stat(long handler, String uri) throws ErrnoException;

  private native void createFile(long handler, String uri) throws ErrnoException;

  private native void mkdir(long handler, String uri) throws ErrnoException;

  private native void rmdir(long handler, String uri) throws ErrnoException;

  private native void rename(long handler, String uri, String newUri) throws ErrnoException;

  private native void unlink(long handler, String uri) throws ErrnoException;

  private native int openFile(long handler, String uri, String mode) throws ErrnoException;
}
