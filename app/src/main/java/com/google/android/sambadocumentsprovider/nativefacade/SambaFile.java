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

import android.system.ErrnoException;
import android.system.StructStat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

class SambaFile implements SmbFile {

  private final long mNativeHandler;
  private int mNativeFd;
  final long mSize;
  final String mUri;

  SambaFile(long nativeHandler, int nativeFd, long size, String uri) {
    mNativeHandler = nativeHandler;
    mNativeFd = nativeFd;
    mSize = size;
    mUri = uri;
  }
  public int read(ByteBuffer buffer) throws IOException {
    try {
      return read(mNativeHandler, mNativeFd, buffer, buffer.capacity());
    } catch(ErrnoException e) {
      throw new IOException("Failed to read file. Fd: " + mNativeFd, e);
    }
  }

  public int write(ByteBuffer buffer, int length) throws IOException {
    try {
      return write(mNativeHandler, mNativeFd, buffer, length);
    } catch(ErrnoException e) {
      throw new IOException("Failed to write file. Fd: " + mNativeFd, e);
    }
  }

  public long seek(long offset) throws IOException {
    try {
      return seek(mNativeHandler, mNativeFd, offset, 0);
    } catch (ErrnoException e) {
      throw new IOException("Failed to move to offset in file. Fd: " + mNativeFd, e);
    }
  }

  @Override
  public StructStat fstat() throws IOException {
    try {
      return fstat(mNativeHandler, mNativeFd);
    } catch (ErrnoException e) {
      throw new IOException("Failed to get stat of " + mNativeFd, e);
    }
  }

  @Override
  public void close() throws IOException {
    try {
      int fd = mNativeFd;
      mNativeFd = -1;
      close(mNativeHandler, fd);
    } catch(ErrnoException e) {
      throw new IOException("Failed to close file. Fd: " + mNativeFd, e);
    }
  }

  private native int read(long handler, int fd, ByteBuffer buffer, int capacity)
      throws ErrnoException;

  private native int write(long handler, int fd, ByteBuffer buffer, int length)
      throws ErrnoException;

  private native long seek(long handler, int fd, long offset, int whence)
      throws ErrnoException;

  private native StructStat fstat(long handler, int fd) throws ErrnoException;

  private native void close(long handler, int fd) throws ErrnoException;
}
