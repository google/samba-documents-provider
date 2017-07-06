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

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;

import com.google.android.sambadocumentsprovider.provider.ByteBufferPool;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface SmbFacade extends SmbClient {
  ParcelFileDescriptor openProxyFile(
          String uri,
          String mode,
          StorageManager storageManager,
          ByteBufferPool bufferPool,
          CancellationSignal signal) throws IOException;
}
