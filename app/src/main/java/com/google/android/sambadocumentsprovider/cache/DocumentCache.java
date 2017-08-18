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

package com.google.android.sambadocumentsprovider.cache;

import android.net.Uri;

import com.google.android.sambadocumentsprovider.document.DocumentMetadata;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DocumentCache {

  private static final long CACHE_EXPIRATION = TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES);
  private final Map<Uri, DocumentMetadata> mCache = new ConcurrentHashMap<>();
  private final Map<Uri, Exception> mExceptionCache = new ConcurrentHashMap<>();

  public CacheResult get(Uri uri) {
    DocumentMetadata metadata = mCache.get(uri);
    if (metadata == null) {
      return CacheResult.obtain(CacheResult.CACHE_MISS, null);
    }

    if (metadata.getTimeStamp() + CACHE_EXPIRATION < System.currentTimeMillis()) {
      return CacheResult.obtain(CacheResult.CACHE_EXPIRED, metadata);
    }

    return CacheResult.obtain(CacheResult.CACHE_HIT, metadata);
  }

  public void throwLastExceptionIfAny(Uri uri) throws Exception {
    if (mExceptionCache.containsKey(uri)) {
      Exception e = mExceptionCache.get(uri);
      mExceptionCache.remove(uri);
      throw e;
    }
  }

  public void put(DocumentMetadata metadata) {
    mCache.put(metadata.getUri(), metadata);

    final Uri parentUri = DocumentMetadata.buildParentUri(metadata.getUri());
    final DocumentMetadata parentMetadata = mCache.get(parentUri);
    if (parentMetadata != null) {
      parentMetadata.putChild(metadata);
    }
  }

  public void put(Uri uri, Exception e) {
    mExceptionCache.put(uri, e);
  }

  public void remove(Uri uri) {
    mCache.remove(uri);
    mExceptionCache.remove(uri);

    final Uri parentUri = DocumentMetadata.buildParentUri(uri);
    final DocumentMetadata parentMetadata = mCache.get(parentUri);
    if (parentMetadata != null && parentMetadata.getChildren() != null) {
      parentMetadata.getChildren().remove(uri);
    }
  }
}
