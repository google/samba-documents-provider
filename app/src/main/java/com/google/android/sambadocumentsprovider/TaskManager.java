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

package com.google.android.sambadocumentsprovider;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TaskManager {

  private static final String TAG = "TaskManager";

  private final Map<Uri, AsyncTask> mTasks = new HashMap<>();

  private final Executor mExecutor = Executors.newCachedThreadPool();

  public <T> void runTask(Uri uri, AsyncTask<T, ?, ?> task, T... args) {
    synchronized (mTasks) {
      if (!mTasks.containsKey(uri) || mTasks.get(uri).getStatus() == Status.FINISHED) {
        mTasks.put(uri, task);
        // TODO: Use different executor for different servers.
        task.executeOnExecutor(mExecutor, args);
      } else {
        Log.i(TAG,
            "Ignore this task for " + uri + " to avoid running multiple updates at the same time.");
      }
    }
  }

  public void runIoTask(AsyncTask<Void, Void, Void> task) {
    task.executeOnExecutor(mExecutor);
  }
}
