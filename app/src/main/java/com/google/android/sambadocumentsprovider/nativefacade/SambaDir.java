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

import android.support.annotation.Nullable;
import android.system.ErrnoException;
import com.google.android.sambadocumentsprovider.base.DirectoryEntry;
import java.io.IOException;

class SambaDir implements SmbDir {

  private final long mNativeHandler;
  private int mNativeDh;

  SambaDir(long nativeHandler, int nativeFd) {
    mNativeHandler = nativeHandler;
    mNativeDh = nativeFd;
  }

  @Override
  public DirectoryEntry readDir() throws IOException {
    try {
      return readDir(mNativeHandler, mNativeDh);
    } catch (ErrnoException e) {
      throw new IOException(e);
    }
  }


  @Override
  public void close() throws IOException {
    try {
      int dh = mNativeDh;
      mNativeDh = -1;
      close(mNativeHandler, dh);
    } catch (ErrnoException e) {
      throw new IOException(e);
    }
  }

  private native @Nullable DirectoryEntry readDir(long handler, int fd) throws ErrnoException;
  private native void close(long handler, int fd) throws ErrnoException;
}
