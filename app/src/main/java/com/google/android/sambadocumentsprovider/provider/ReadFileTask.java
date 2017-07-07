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
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;
import android.support.annotation.Nullable;
import android.util.Log;
import com.google.android.sambadocumentsprovider.nativefacade.SmbFile;
import com.google.android.sambadocumentsprovider.nativefacade.SmbClient;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ReadFileTask extends AsyncTask<Void, Void, Void> {

  private static final String TAG = "ReadFileTask";

  private final String mUri;
  private final SmbClient mClient;
  private final ParcelFileDescriptor mPfd;
  private final ByteBufferPool mBufferPool;

  private ByteBuffer mBuffer;

  ReadFileTask(String uri, SmbClient client, ParcelFileDescriptor pfd,
      ByteBufferPool bufferPool) {
    mUri = uri;
    mClient = client;
    mPfd = pfd;
    mBufferPool = bufferPool;
  }

  @Override
  public void onPreExecute() {
    mBuffer = mBufferPool.obtainBuffer();
  }

  @Override
  public Void doInBackground(Void... args) {
    try (final AutoCloseOutputStream os = new AutoCloseOutputStream(mPfd);
        final SmbFile file = mClient.openFile(mUri, "r")) {
      int size;
      byte[] buf = new byte[mBuffer.capacity()];
      while ((size = file.read(mBuffer, Integer.MAX_VALUE)) > 0) {
        mBuffer.get(buf, 0, size);
        os.write(buf, 0, size);
        mBuffer.clear();
      }
    } catch (IOException e) {
      Log.e(TAG, "Failed to read file.", e);

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
    mBuffer = null;
  }
}
