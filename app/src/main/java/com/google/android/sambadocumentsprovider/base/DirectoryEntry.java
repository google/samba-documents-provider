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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Denotes a directory entry in Samba. This can represent not only a directory in Samba, but also
 * a workgroup, a share or a file.
 */
public class DirectoryEntry {

  @IntDef({WORKGROUP, SERVER, FILE_SHARE, PRINTER_SHARE, COMMS_SHARE, IPC_SHARE, DIR, FILE, LINK})
  @Retention(RetentionPolicy.SOURCE)
  public @interface Type {}
  public static final int WORKGROUP = 1;
  public static final int SERVER = 2;
  public static final int FILE_SHARE = 3;
  public static final int PRINTER_SHARE = 4;
  public static final int COMMS_SHARE = 5;
  public static final int IPC_SHARE = 6;
  public static final int DIR = 7;
  public static final int FILE = 8;
  public static final int LINK = 9;

  private final int mType;
  private final String mComment;
  private String mName;

  public DirectoryEntry(@Type int type, String comment, String name) {
    mType = type;
    mComment = comment;
    mName = name;
  }

  public @Type int getType() {
    return mType;
  }

  public String getComment() {
    return mComment;
  }

  public String getName() {
    return mName;
  }

  public void setName(String newName) {
    mName = newName;
  }
}
