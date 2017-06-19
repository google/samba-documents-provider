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

import android.os.Looper;
import android.os.Message;
import android.support.annotation.IntDef;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;

class SambaFileClient extends BaseClient implements SmbFile {

  @IntDef({ READ, WRITE, CLOSE })
  @Retention(RetentionPolicy.SOURCE)
  @interface Operation {}
  private static final int READ = 1;
  private static final int WRITE = 2;
  private static final int CLOSE = 3;

  SambaFileClient(Looper looper, SmbFile smbFileImpl) {
    mHandler = new SambaFileHandler(looper, smbFileImpl);
  }

  @Override
  public int read(ByteBuffer buffer) throws IOException {
    final MessageValues<ByteBuffer> messageValues = MessageValues.obtain();
    messageValues.setObj(buffer);
    final Message msg = mHandler.obtainMessage(READ, messageValues);
    enqueue(msg);
    return messageValues.getInt();
  }

  @Override
  public int write(ByteBuffer buffer, int length) throws IOException {
    final MessageValues<ByteBuffer> messageValues = MessageValues.obtain();
    messageValues.setObj(buffer);
    final Message msg = mHandler.obtainMessage(WRITE, messageValues);
    msg.arg1 = length;
    enqueue(msg);
    return messageValues.getInt();
  }

  @Override
  public void close() throws IOException {
    final MessageValues messageValues = MessageValues.obtain();
    final Message msg = mHandler.obtainMessage(CLOSE, messageValues);
    enqueue(msg);
    messageValues.checkException();
  }

  private static class SambaFileHandler extends BaseHandler {

    private SmbFile mSmbFileImpl;

    private SambaFileHandler(Looper looper, SmbFile smbFileImpl) {
      super(looper);

      mSmbFileImpl = smbFileImpl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void processMessage(Message msg) {
      final MessageValues<ByteBuffer> messageValues = (MessageValues<ByteBuffer>) msg.obj;
      try {
        final ByteBuffer buffer = messageValues.getObj();

        switch (msg.what) {
          case READ:
            messageValues.setInt(mSmbFileImpl.read(buffer));
            break;
          case WRITE: {
            final int length = msg.arg1;
            messageValues.setInt(mSmbFileImpl.write(buffer, length));
            break;
          }
          case CLOSE:
            mSmbFileImpl.close();
            break;
          default:
            throw new UnsupportedOperationException("Unknown operation " + msg.what);
        }
      } catch (IOException e) {
        messageValues.setException(e);
      } catch (RuntimeException e) {
        messageValues.setRuntimeException(e);
      }
    }
  }
}
