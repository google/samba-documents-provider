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

package com.google.android.sambadocumentsprovider.provider;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ProxyFileDescriptorCallback;
import android.support.annotation.Nullable;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.system.StructStat;
import android.util.Log;

import com.google.android.sambadocumentsprovider.base.OnTaskFinishedCallback;
import com.google.android.sambadocumentsprovider.nativefacade.SmbFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

@TargetApi(26)
public class SambaProxyFileCallback extends ProxyFileDescriptorCallback {
  private static final String TAG = "SambaProxyFileCallback";

  private final String mUri;
  private final SmbFile mFile;
  private final ByteBufferPool mBufferPool;
  private final @Nullable OnTaskFinishedCallback<String> mCallback;

  public SambaProxyFileCallback(
      String uri,
      SmbFile file,
      ByteBufferPool bufferPool,
      @Nullable OnTaskFinishedCallback<String> callback) {

    mUri = uri;
    mFile = file;
    mBufferPool = bufferPool;
    mCallback = callback;
  }

  @Override
  public long onGetSize() throws ErrnoException {
    StructStat stat;
    try {
      stat = mFile.fstat();
      return stat.st_size;
    } catch (IOException e) {
      throwErrnoException(e);
    }

    return 0;
  }

  @Override
  public int onRead(long offset, int size, byte[] data) throws ErrnoException {
    final ByteBuffer buffer = mBufferPool.obtainBuffer();
    try {
      mFile.seek(offset);

      int readSize;
      int total = 0;
      while (size > total && (readSize = mFile.read(buffer, size - total)) > 0) {
        buffer.get(data, total, readSize);
        buffer.clear();
        total += readSize;
      }

      return total;
    } catch (IOException e) {
      throwErrnoException(e);
    } finally {
      mBufferPool.recycleBuffer(buffer);
    }

    return 0;
  }

  @Override
  public int onWrite(long offset, int size, byte[] data) throws ErrnoException {
    int written = 0;

    final ByteBuffer buffer = mBufferPool.obtainBuffer();
    try {
      mFile.seek(offset);

      while (written < size) {
        int willWrite = Math.min(size - written, buffer.capacity());
        buffer.put(data, written, willWrite);
        int res = mFile.write(buffer, willWrite);
        written += res;
        buffer.clear();
      }
    } catch (IOException e) {
      throwErrnoException(e);
    } finally {
      mBufferPool.recycleBuffer(buffer);
    }

    return written;
  }

  @Override
  public void onFsync() throws ErrnoException {
    // Nothing to do
  }

  @Override
  public void onRelease() {
    try {
      mFile.close();
    } catch (IOException e) {
      Log.e(TAG, "Failed to close file", e);
    }

    if (mCallback != null) {
      mCallback.onTaskFinished(OnTaskFinishedCallback.SUCCEEDED, mUri, null);
    }
  }

  private void throwErrnoException(IOException e) throws ErrnoException {
    // Hack around that SambaProxyFileCallback throws ErrnoException rather than IOException
    // assuming the underlying cause is an ErrnoException.
    if (e.getCause() instanceof ErrnoException) {
      throw (ErrnoException) e.getCause();
    } else {
      throw new ErrnoException("I/O", OsConstants.EIO, e);
    }
  }
}
