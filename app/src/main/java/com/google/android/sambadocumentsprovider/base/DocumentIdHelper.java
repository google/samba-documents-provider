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

import android.net.Uri;
import com.google.android.sambadocumentsprovider.BuildConfig;
import com.google.android.sambadocumentsprovider.document.DocumentMetadata;

public class DocumentIdHelper {

  private DocumentIdHelper() {}

  public static String toRootId(DocumentMetadata metadata) {
    if (BuildConfig.DEBUG && !metadata.isFileShare()) {
      throw new RuntimeException(metadata + " is not a file share.");
    }

    return metadata.getUri().toString();
  }

  public static String toDocumentId(Uri smbUri) {
    // TODO: Change document ID to infer root.
    return smbUri.toString();
  }

  public static Uri toUri(String documentId) {
    return Uri.parse(toUriString(documentId));
  }

  public static String toUriString(String documentId) {
    return documentId;
  }
}
