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

import android.database.MatrixCursor;
import android.os.Bundle;
import android.util.Log;

import com.google.android.sambadocumentsprovider.BuildConfig;

import java.util.concurrent.Future;

public class NetworkBrowserCursor extends MatrixCursor {
  private static final String TAG = "NetworkBrowsingCursor";

  private Bundle mExtra;
  private Future mFuture;

  public NetworkBrowserCursor(String[] columnNames) {
    super(columnNames);
  }

  public void setFuture(Future future) {
    mFuture = future;
  }

  @Override
  public void setExtras(Bundle extras) {
    mExtra = extras;
  }

  @Override
  public Bundle getExtras() {
    return mExtra;
  }

  @Override
  public void close() {
    super.close();
    if (mFuture != null && !mFuture.isDone()) {
      if (BuildConfig.DEBUG) Log.d(TAG, "Cursor is closed. Cancel the loading task " + mFuture);
      mFuture.cancel(false);
    }
  }
}
