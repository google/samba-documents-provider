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

import android.system.StructStat;
import com.google.android.sambadocumentsprovider.base.DirectoryEntry;
import java.io.IOException;
import java.util.List;

public interface SmbClient {

  void reset();

  List<DirectoryEntry> readDir(String uri) throws IOException;

  StructStat stat(String uri) throws IOException;

  void createFile(String uri) throws IOException;

  void mkdir(String uri) throws IOException;

  void rename(String uri, String newUri) throws IOException;

  void unlink(String uri) throws IOException;

  void rmdir(String uri) throws IOException;

  SmbFile openFile(String uri, String mode) throws IOException;
}
