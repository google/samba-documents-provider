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

package com.google.android.sambadocumentsprovider.base;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import com.google.android.sambadocumentsprovider.BuildConfig;

/**
 * Use this class to avoid using {@link Cursor#setExtras(Bundle)} on API level < 23.
 */
public class DocumentCursor extends MatrixCursor {

  private static final String TAG = "DocumentCursor";

  private Bundle mExtra;
  private AsyncTask<?, ?, ?> mLoadingTask;

  public DocumentCursor(String[] projection) {
    super(projection);
  }

  public void setLoadingTask(AsyncTask<?, ?, ?> task) {
    mLoadingTask = task;
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
    if (mLoadingTask != null && mLoadingTask.getStatus() != Status.FINISHED) {
      if(BuildConfig.DEBUG) Log.d(TAG, "Cursor is closed. Cancel the loading task " + mLoadingTask);
      // Interrupting the task is not a good choice as it's waiting for the Samba client thread
      // returning the result. Interrupting the task only frees the task from waiting for the
      // result, rather than freeing the Samba client thread doing the hard work.
      mLoadingTask.cancel(false);
    }
  }
}
