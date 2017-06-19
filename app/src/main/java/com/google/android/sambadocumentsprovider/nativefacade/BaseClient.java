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

abstract class BaseClient {

  BaseHandler mHandler;

  void enqueue(Message msg) {
    try {
      synchronized (msg.obj) {
        mHandler.sendMessage(msg);
        msg.obj.wait();
      }
    } catch(InterruptedException e) {
      // It should never happen.
      throw new RuntimeException("Unexpected interruption.", e);
    }
  }

  abstract static class BaseHandler extends Handler {

    BaseHandler(Looper looper) {
      super(looper);
    }

    abstract void processMessage(Message msg);

    @Override
    public void handleMessage(Message msg) {
      synchronized (msg.obj) {
        processMessage(msg);
        msg.obj.notify();
      }
    }
  }
}
