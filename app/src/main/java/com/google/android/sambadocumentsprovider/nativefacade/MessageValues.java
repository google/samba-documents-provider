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

import android.support.v4.util.Pools.Pool;
import android.support.v4.util.Pools.SynchronizedPool;
import java.io.IOException;

/**
 * A class used for pass values between two sides of {@link SambaMessageLooper}.
 *
 * If it were C/C++, this would be a union type.
 *
 * @param <T> A convenient parameterized type to avoid casting everywhere.
 */
class MessageValues<T> implements AutoCloseable {

  private static final Pool<MessageValues<?>> POOL = new SynchronizedPool<>(20);

  private volatile T mObj;
  private volatile int mInt;
  private volatile long mLong;
  private volatile IOException mException;
  private volatile RuntimeException mRuntimeException;

  private MessageValues() {}

  void checkException() throws IOException {
    if (mException != null) {
      throw mException;
    }
    if (mRuntimeException != null) {
      throw mRuntimeException;
    }
  }

  T getObj() throws IOException {
    checkException();
    return mObj;
  }

  void setObj(T obj) {
    mObj = obj;
  }

  int getInt() throws IOException {
    checkException();
    return mInt;
  }

  long getLong() throws IOException {
    checkException();
    return mLong;
  }

  void setLong(long value) {
    mLong = value;
  }

  void setInt(int value) {
    mInt = value;
  }

  void setException(IOException exception) {
    mException = exception;
  }

  void setRuntimeException(RuntimeException exception) {
    mRuntimeException = exception;
  }

  @SuppressWarnings("unchecked")
  static <T> MessageValues<T> obtain() {
    MessageValues<?> response = POOL.acquire();
    if (response == null) {
      response = new MessageValues<>();
    }
    return (MessageValues<T>) response;
  }

  @Override
  public void close() {
    mObj = null;
    mInt = 0;
    mLong = 0L;
    mException = null;
    mRuntimeException = null;
    POOL.release(this);
  }
}
