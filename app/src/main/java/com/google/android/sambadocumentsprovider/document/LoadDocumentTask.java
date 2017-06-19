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
import com.google.android.sambadocumentsprovider.base.BiResultTask;
import com.google.android.sambadocumentsprovider.base.OnTaskFinishedCallback;
import com.google.android.sambadocumentsprovider.cache.DocumentCache;
import com.google.android.sambadocumentsprovider.nativefacade.SmbClient;

public class LoadDocumentTask extends BiResultTask<Void, Void, DocumentMetadata> {

  private final Uri mUri;
  private final SmbClient mClient;
  private final DocumentCache mCache;
  private final OnTaskFinishedCallback<Uri> mCallback;

  public LoadDocumentTask(Uri uri, SmbClient client, DocumentCache cache,
      OnTaskFinishedCallback<Uri> callback) {
    mUri = uri;
    mClient = client;
    mCache = cache;
    mCallback = callback;
  }

  @Override
  public DocumentMetadata run(Void... args) throws Exception {
    return DocumentMetadata.fromUri(mUri, mClient);
  }

  @Override
  public void onSucceeded(DocumentMetadata documentMetadata) {
    mCache.put(documentMetadata);

    mCallback.onTaskFinished(OnTaskFinishedCallback.SUCCEEDED, mUri, null);
  }

  @Override
  public void onFailed(Exception e) {
    mCache.put(mUri, e);

    mCallback.onTaskFinished(OnTaskFinishedCallback.FAILED, mUri, e);
  }

  @Override
  public void onCancelled(DocumentMetadata metadata) {
    if (metadata != null) {
      // This is still valid result. Don't waste the hard work.
      mCache.put(metadata);

      mCallback.onTaskFinished(OnTaskFinishedCallback.FAILED, mUri, null);
    }
  }
}
