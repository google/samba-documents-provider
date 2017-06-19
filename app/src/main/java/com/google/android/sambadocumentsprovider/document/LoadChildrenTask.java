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
import com.google.android.sambadocumentsprovider.cache.DocumentCache;
import com.google.android.sambadocumentsprovider.base.BiResultTask;
import com.google.android.sambadocumentsprovider.base.OnTaskFinishedCallback;
import com.google.android.sambadocumentsprovider.nativefacade.SmbClient;
import java.io.IOException;
import java.util.Map;

public class LoadChildrenTask extends BiResultTask<Void, Void, Map<Uri, DocumentMetadata>> {

  private final DocumentMetadata mMetadata;
  private final DocumentCache mCache;
  private final SmbClient mClient;
  private final OnTaskFinishedCallback<DocumentMetadata> mCallback;

  public LoadChildrenTask(DocumentMetadata metadata, SmbClient client,
      DocumentCache cache, OnTaskFinishedCallback<DocumentMetadata> callback) {
    mMetadata = metadata;
    mCache = cache;
    mClient = client;
    mCallback = callback;
  }

  @Override
  public Map<Uri, DocumentMetadata> run(Void... args) throws IOException {
    mMetadata.loadChildren(mClient);

    return mMetadata.getChildren();
  }

  private void onFinish(Map<Uri, DocumentMetadata> children) {
    for (DocumentMetadata metadata : children.values()) {
      mCache.put(metadata);
    }
  }

  @Override
  public void onSucceeded(Map<Uri, DocumentMetadata> children) {
    onFinish(children);
    mCallback.onTaskFinished(OnTaskFinishedCallback.SUCCEEDED, mMetadata, null);
  }

  @Override
  public void onFailed(Exception e) {
    mCallback.onTaskFinished(OnTaskFinishedCallback.FAILED, mMetadata, e);
  }

  @Override
  public void onCancelled(Map<Uri, DocumentMetadata> children) {
    if (children != null) {
      onFinish(children);
      mCallback.onTaskFinished(OnTaskFinishedCallback.CANCELLED, mMetadata, null);
    }
  }
}
