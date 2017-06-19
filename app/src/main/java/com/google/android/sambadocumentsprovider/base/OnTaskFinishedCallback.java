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

import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface OnTaskFinishedCallback<T> {

  @IntDef({ SUCCEEDED, FAILED, CANCELLED })
  @Retention(RetentionPolicy.SOURCE)
  @interface Status {}
  int SUCCEEDED = 0;
  int FAILED = 1;
  int CANCELLED = 2;

  void onTaskFinished(@Status int status, @Nullable T item, @Nullable Exception exception);
}
