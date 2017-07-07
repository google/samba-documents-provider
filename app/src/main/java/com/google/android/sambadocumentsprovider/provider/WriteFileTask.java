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

import android.os.AsyncTask;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.support.annotation.Nullable;
import android.util.Log;
import com.google.android.sambadocumentsprovider.base.OnTaskFinishedCallback;
import com.google.android.sambadocumentsprovider.nativefacade.SmbFile;
import com.google.android.sambadocumentsprovider.nativefacade.SmbClient;
import java.io.IOException;
import java.nio.ByteBuffer;

public class WriteFileTask extends AsyncTask<Void, Void, Void> {

  private static final String TAG = "WriteFileTask";

  private final String mUri;
  private final SmbClient mClient;
  private final ParcelFileDescriptor mPfd;
  private final OnTaskFinishedCallback<String> mCallback;
  private final ByteBufferPool mBufferPool;
  private final ByteBuffer mBuffer;

  WriteFileTask(String uri,
      SmbClient service,
      ParcelFileDescriptor pfd,
      ByteBufferPool bufferPool,
      OnTaskFinishedCallback<String> callback) {
    mUri = uri;
    mClient = service;
    mPfd = pfd;
    mCallback = callback;

    mBufferPool = bufferPool;
    mBuffer = mBufferPool.obtainBuffer();
  }

  @Override
  public Void doInBackground(Void... args) {
    try (final AutoCloseInputStream is = new AutoCloseInputStream(mPfd);
        final SmbFile file = mClient.openFile(mUri, "w")){
      int size;
      byte[] buf = new byte[mBuffer.capacity()];
      while ((size = is.read(buf)) > 0) {
        mBuffer.put(buf, 0, size);
        file.write(mBuffer, size);
        mBuffer.clear();
      }
    } catch (IOException e) {
      Log.e(TAG, "Failed to write file.", e);

      try {
        mPfd.closeWithError(e.getMessage());
      } catch (IOException exc) {
        Log.e(TAG, "Can't even close PFD with error.", exc);
      }
    }

    return null;
  }

  @Override
  public void onPostExecute(Void arg) {
    mBufferPool.recycleBuffer(mBuffer);

    mCallback.onTaskFinished(OnTaskFinishedCallback.SUCCEEDED, mUri, null);
  }
}
