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

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import com.google.android.sambadocumentsprovider.base.DirectoryEntry;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

class SambaDirClient extends BaseClient implements SmbDir {

  @IntDef({ READ_DIR, CLOSE })
  @Retention(RetentionPolicy.SOURCE)
  @interface Operation {}
  private static final int READ_DIR = 0;
  private static final int CLOSE = READ_DIR + 1;

  SambaDirClient(Looper looper, SmbDir smbDirImpl) {
    mHandler = new SambaDirHandler(looper, smbDirImpl);
  }

  @Nullable
  @Override
  public DirectoryEntry readDir() throws IOException {
    try (MessageValues<DirectoryEntry> messageValues = MessageValues.obtain()) {
      final Message msg = mHandler.obtainMessage(READ_DIR, messageValues);
      enqueue(msg);
      return messageValues.getObj();
    }
  }

  @Override
  public void close() throws IOException {
    try (MessageValues<?> messageValues = MessageValues.obtain()) {
      final Message msg = mHandler.obtainMessage(CLOSE, messageValues);
      enqueue(msg);
      messageValues.checkException();
    }
  }

  private static class SambaDirHandler extends BaseHandler {

    private final SmbDir mSmbDirImpl;

    private SambaDirHandler(Looper looper, SmbDir smbDirImpl) {
      super(looper);

      mSmbDirImpl = smbDirImpl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void processMessage(Message msg) {
      final MessageValues<DirectoryEntry> messageValues = (MessageValues<DirectoryEntry>) msg.obj;
      try {
        switch (msg.what) {
          case READ_DIR:
            messageValues.setObj(mSmbDirImpl.readDir());
            break;
          case CLOSE:
            mSmbDirImpl.close();
            break;
          default:
            throw new UnsupportedOperationException("Unknown operation " + msg.what);
        }
      } catch (RuntimeException e) {
        messageValues.setRuntimeException(e);
      } catch (IOException e) {
        messageValues.setException(e);
      }
    }
  }
}
