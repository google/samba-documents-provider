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

import java.util.concurrent.CountDownLatch;

public class SambaMessageLooper {

  private final Thread mLooperThread = new Thread(new Runnable() {
    @Override
    public void run() {
      prepare();
    }
  });
  private final CountDownLatch mLatch = new CountDownLatch(1);

  private volatile Looper mLooper;
  private volatile NativeSambaFacade mClientImpl;
  private volatile NativeCredentialCache mCredentialCacheImpl;

  private SambaFacadeClient mServiceClient;
  private CredentialCacheClient mCredentialCacheClient;

  public SambaMessageLooper() {
    init();
  }

  public SmbFacade getClient() {
    return mServiceClient;
  }

  public CredentialCache getCredentialCache() {
    return mCredentialCacheClient;
  }

  private void init() {
    try {
      mLooperThread.start();
      mLatch.await();

      mCredentialCacheClient = new CredentialCacheClient(mLooper, mCredentialCacheImpl);

      mServiceClient = new SambaFacadeClient(mLooper, mClientImpl);
    } catch(InterruptedException e) {
      // Should never happen
      throw new RuntimeException(e);
    }
  }

  private void prepare() {
    Looper.prepare();
    mLooper = Looper.myLooper();

    mCredentialCacheImpl = new NativeCredentialCache();
    mClientImpl = new NativeSambaFacade(mCredentialCacheImpl);
    mLatch.countDown();

    Looper.loop();
  }
}
