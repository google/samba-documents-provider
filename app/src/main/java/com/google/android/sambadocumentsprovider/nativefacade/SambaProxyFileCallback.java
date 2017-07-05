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

import android.os.CancellationSignal;
import android.os.ProxyFileDescriptorCallback;
import android.system.ErrnoException;
import android.system.StructStat;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by rthakohov on 7/4/17.
 */

class SambaProxyFileCallback extends ProxyFileDescriptorCallback {
    private static final String TAG = "SambaProxyFileCallback";

    private final SambaProxyFileClient mFile;
    private final ByteBuffer mBuffer;
    private final CancellationSignal mSignal;

    SambaProxyFileCallback(SambaProxyFileClient file, ByteBuffer buffer, CancellationSignal signal) {
        mFile = file;
        mBuffer = buffer;
        mSignal = signal;
    }

    @Override
    public long onGetSize() throws ErrnoException {
        return mFile.mSize;
    }

    @Override
    public int onRead(long offset, int size, byte[] data) throws ErrnoException {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            mFile.seek(offset);

            byte[] buf = new byte[mBuffer.capacity()];

            int readSize;
            int total = 0;
            while ((mSignal == null || !mSignal.isCanceled())
                    && (readSize = mFile.read(mBuffer)) > 0) {
                mBuffer.get(buf, 0, readSize);
                os.write(buf, 0, readSize);
                mBuffer.clear();
                total += readSize;
                if (total >= size) {
                    break;
                }
            }

            byte[] output = os.toByteArray();
            System.arraycopy(output, 0, data, 0, Math.min(size, output.length));

            return Math.min(size, output.length);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return 0;
    }

    @Override
    public int onWrite(long offset, int size, byte[] data) throws ErrnoException {
        int written = 0;

        try {
            mFile.seek(offset);

            while ((mSignal == null || !mSignal.isCanceled())
                    && written < size) {
                int willWrite = Math.min(size - written, mBuffer.capacity());
                mBuffer.put(data, written, willWrite);
                int res = mFile.write(mBuffer, willWrite);
                written += res;
                mBuffer.clear();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to write file.", e);
        }

        Log.d(TAG, "written = " + written);

        return written;
    }

    @Override
    public void onRelease() {
        try {
            mFile.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to close file");
        }
    }
}
