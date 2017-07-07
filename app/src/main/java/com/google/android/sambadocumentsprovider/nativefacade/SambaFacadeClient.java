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

import android.annotation.TargetApi;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.system.StructStat;

import com.google.android.sambadocumentsprovider.base.DirectoryEntry;
import com.google.android.sambadocumentsprovider.base.OnTaskFinishedCallback;
import com.google.android.sambadocumentsprovider.provider.ByteBufferPool;
import com.google.android.sambadocumentsprovider.provider.SambaProxyFileCallback;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.List;

class SambaFacadeClient extends BaseClient implements SmbFacade {

  @IntDef({ RESET, READ_DIR, STAT, MKDIR, RENAME, UNLINK, RMDIR, OPEN_FILE })
  @Retention(RetentionPolicy.SOURCE)
  @interface Operation {}
  static final int RESET = 1;
  static final int READ_DIR = RESET + 1;
  static final int STAT = READ_DIR + 1;
  static final int CREATE_FILE = STAT + 1;
  static final int MKDIR = CREATE_FILE + 1;
  static final int RENAME = MKDIR + 1;
  static final int UNLINK = RENAME + 1;
  static final int RMDIR = UNLINK + 1;
  static final int OPEN_FILE = RMDIR + 1;

  private static final String URI = "URI";
  private static final String NEW_URI = "NEW_URI";
  private static final String MODE = "MODE";

  SambaFacadeClient(Looper looper, SmbClient clientImpl) {
    mHandler = new SambaServiceHandler(looper, clientImpl);
  }

  private Message obtainMessage(int what, MessageValues messageValues, String uri) {
    final Message msg = mHandler.obtainMessage(what, messageValues);

    final Bundle args = msg.getData();
    args.putString(URI, uri);

    return msg;
  }

  @Override
  public void reset() {
    try (MessageValues<Void> messageValues = MessageValues.obtain()) {
      final Message msg = obtainMessage(RESET, messageValues, null);
      enqueue(msg);
    }
  }

  @Override
  public SmbDir openDir(String uri) throws IOException {
    try (final MessageValues<SmbDir> messageValues = MessageValues.obtain()) {
      final Message msg = obtainMessage(READ_DIR, messageValues, uri);
      enqueue(msg);
      return new SambaDirClient(mHandler.getLooper(), messageValues.getObj());
    }
  }

  @Override
  public StructStat stat(String uri) throws IOException {
    try (final MessageValues<StructStat> messageValues = MessageValues.obtain()) {
      final Message msg = obtainMessage(STAT, messageValues, uri);
      enqueue(msg);
      return messageValues.getObj();
    }
  }

  @Override
  public void createFile(String uri) throws IOException {
    try (final MessageValues<?> messageValues = MessageValues.obtain()) {
      final Message msg = obtainMessage(CREATE_FILE, messageValues, uri);
      enqueue(msg);
      messageValues.checkException();
    }
  }

  @Override
  public void mkdir(String uri) throws IOException {
    try (final MessageValues<?> messageValues = MessageValues.obtain()) {
      final Message msg = obtainMessage(MKDIR, messageValues, uri);
      enqueue(msg);
      messageValues.checkException();
    }
  }

  @Override
  public void rename(String uri, String newUri) throws IOException {
    try (final MessageValues<?> messageValues = MessageValues.obtain()) {
      final Message msg = obtainMessage(RENAME, messageValues, uri);
      msg.peekData().putString(NEW_URI, newUri);
      enqueue(msg);
      messageValues.checkException();
    }
  }

  @Override
  public void unlink(String uri) throws IOException {
    try (final MessageValues<?> messageValues = MessageValues.obtain()) {
      final Message msg = obtainMessage(UNLINK, messageValues, uri);
      enqueue(msg);
      messageValues.checkException();
    }
  }

  @Override
  public void rmdir(String uri) throws IOException {
    try (final MessageValues<?> messageValues = MessageValues.obtain()) {
      final Message msg = obtainMessage(RMDIR, messageValues, uri);
      enqueue(msg);
      messageValues.checkException();
    }
  }

  @Override
  public SmbFile openFile(String uri, String mode) throws IOException {
    return new SambaFileClient(mHandler.getLooper(), openFileRaw(uri, mode));
  }

  @Override
  @TargetApi(26)
  public ParcelFileDescriptor openProxyFile(
      String uri,
      String mode,
      StorageManager storageManager,
      ByteBufferPool bufferPool,
      @Nullable OnTaskFinishedCallback<String> callback) throws IOException {
    SambaFile file = openFileRaw(uri, mode);
    return storageManager.openProxyFileDescriptor(
            ParcelFileDescriptor.parseMode(mode),
            new SambaProxyFileCallback(uri, file, bufferPool, callback),
            mHandler);
  }

  private SambaFile openFileRaw(String uri, String mode) throws IOException {
    try (final MessageValues<SambaFile> messageValues = MessageValues.obtain()) {
      enqueue(obtainMessageForOpenFile(uri, mode, messageValues));
      return messageValues.getObj();
    }
  }

  private Message obtainMessageForOpenFile(
      String uri, String mode, MessageValues<SambaFile> messageValues) {
    final Message msg = obtainMessage(OPEN_FILE, messageValues, uri);
    msg.peekData().putString(MODE, mode);
    return msg;
  }

  private static class SambaServiceHandler extends BaseHandler {

    private final SmbClient mClientImpl;

    private SambaServiceHandler(Looper looper, SmbClient clientImpl) {
      super(looper);
      mClientImpl = clientImpl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void processMessage(Message msg) {
      final Bundle args = msg.peekData();
      final String uri = args.getString(URI);
      final MessageValues messageValues = (MessageValues) msg.obj;

      try {
        switch (msg.what) {
          case RESET:
            mClientImpl.reset();
            break;
          case READ_DIR:
            messageValues.setObj(mClientImpl.openDir(uri));
            break;
          case STAT:
            messageValues.setObj(mClientImpl.stat(uri));
            break;
          case CREATE_FILE:
            mClientImpl.createFile(uri);
            break;
          case MKDIR:
            mClientImpl.mkdir(uri);
            break;
          case RENAME: {
            final String newUri = args.getString(NEW_URI);
            mClientImpl.rename(uri, newUri);
            break;
          }
          case UNLINK:
            mClientImpl.unlink(uri);
            break;
          case RMDIR:
            mClientImpl.rmdir(uri);
            break;
          case OPEN_FILE: {
            final String mode = args.getString(MODE);
            messageValues.setObj(mClientImpl.openFile(uri, mode));
            break;
          }
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
