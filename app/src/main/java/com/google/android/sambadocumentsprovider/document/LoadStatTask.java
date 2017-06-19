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

package com.google.android.sambadocumentsprovider.document;

import android.net.Uri;
import android.os.AsyncTask;
import android.system.StructStat;
import android.util.Log;
import com.google.android.sambadocumentsprovider.base.OnTaskFinishedCallback;
import com.google.android.sambadocumentsprovider.nativefacade.SmbClient;
import java.util.HashMap;
import java.util.Map;

public class LoadStatTask extends AsyncTask<Void, Void, Map<Uri, StructStat>> {

  private static final String TAG = "LoadStatTask";

  private final Map<Uri, DocumentMetadata> mMetadataMap;
  private final SmbClient mClient;
  private final OnTaskFinishedCallback<Map<Uri, DocumentMetadata>> mCallback;

  public LoadStatTask(
      Map<Uri, DocumentMetadata> metadataMap,
      SmbClient client,
      OnTaskFinishedCallback<Map<Uri, DocumentMetadata>> callback) {
    mMetadataMap = metadataMap;
    mClient = client;
    mCallback = callback;
  }

  @Override
  public Map<Uri, StructStat> doInBackground(Void... args) {
    Map<Uri, StructStat> stats = new HashMap<>(mMetadataMap.size());
    for (DocumentMetadata metadata : mMetadataMap.values()) {
      try {
        metadata.loadStat(mClient);
        if (isCancelled()) {
          return stats;
        }
      } catch(Exception e) {
        // Failed to load a stat for a child... Just eat this exception, the only consequence it may
        // have is constantly retrying to fetch the stat.
        Log.e(TAG, "Failed to load stat for " + metadata.getUri());
      }
    }
    return stats;
  }

  @Override
  public void onPostExecute(Map<Uri, StructStat> stats) {
    mCallback.onTaskFinished(OnTaskFinishedCallback.SUCCEEDED, mMetadataMap, null);
  }

  @Override
  public void onCancelled(Map<Uri, StructStat> stats) {
    mCallback.onTaskFinished(OnTaskFinishedCallback.CANCELLED, mMetadataMap, null);
  }
}
