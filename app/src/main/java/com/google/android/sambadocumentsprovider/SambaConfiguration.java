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

import android.support.annotation.Nullable;
import android.system.ErrnoException;
import android.util.Log;
import com.google.android.sambadocumentsprovider.base.BiResultTask;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

class SambaConfiguration implements Iterable<Map.Entry<String, String>> {

  static {
    System.loadLibrary("samba_client");
  }

  private static final String TAG = "SambaConfiguration";

  private static final String HOME_VAR = "HOME";
  private static final String SMB_FOLDER_NAME = ".smb";
  private static final String SMB_CONF_FILE = "smb.conf";
  private static final String CONF_KEY_VALUE_SEPARATOR = " = ";

  private final File mHomeFolder;
  private final File mShareFolder;
  private final Map<String, String> mConfigurations = new HashMap<>();

  SambaConfiguration(File homeFolder, @Nullable File shareFolder) {
    mHomeFolder = homeFolder;
    if (shareFolder != null && (shareFolder.isDirectory() || shareFolder.mkdirs())) {
      mShareFolder = shareFolder;
    } else {
      Log.w(TAG, "Failed to create share folder. Only default value is supported.");

      // Use home folder as the share folder to avoid null checks everywhere.
      mShareFolder = mHomeFolder;
    }

    setHomeEnv(homeFolder.getAbsolutePath());
  }

  void flushDefault(OnConfigurationChangedListener listener) {
    // lmhosts are not used in SambaDocumentsProvider and prioritize bcast because sometimes in home
    // settings DNS will resolve unknown domain name to a specific IP for advertisement.
    //
    // lmhosts -- lmhosts file if existed side by side to smb.conf
    // wins -- Windows Internet Name Service
    // hosts -- hosts file and DNS resolution
    // bcast -- NetBIOS broadcast
    addConfiguration("name resolve order", "wins bcast hosts");

    // Urge from users to disable SMB1 by default.
    addConfiguration("client min protocol", "SMB2");
    addConfiguration("client max protocol", "SMB3");

    File smbFile = getSmbFile(mHomeFolder);
    if (!smbFile.exists()) {
      flush(listener);
    }
  }

  synchronized SambaConfiguration addConfiguration(String key, String value) {
    mConfigurations.put(key, value);
    return this;
  }

  synchronized SambaConfiguration removeConfiguration(String key) {
    mConfigurations.remove(key);
    return this;
  }

  boolean syncFromExternal(OnConfigurationChangedListener listener) {
    final File smbFile = getSmbFile(mHomeFolder);
    final File extSmbFile = getExtSmbFile(mShareFolder);

    if (extSmbFile.isFile() && extSmbFile.lastModified() > smbFile.lastModified()) {
      if (BuildConfig.DEBUG) Log.d(TAG, "Syncing " + SMB_CONF_FILE +
          " from external source to internal source.");
      new SyncTask(listener).execute(extSmbFile);

      return true;
    }

    return false;
  }

  private void flush(OnConfigurationChangedListener listener) {
    new FlushTask(listener).execute();
  }

  private synchronized void read(File smbFile) throws IOException {
    mConfigurations.clear();
    try (BufferedReader reader = new BufferedReader(new FileReader(smbFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String[] conf = line.split(CONF_KEY_VALUE_SEPARATOR);
        if (conf.length == 2) {
          mConfigurations.put(conf[0], conf[1]);
        }
      }
    }
  }

  private synchronized void write() throws IOException {
    try (PrintStream fs = new PrintStream(getSmbFile(mHomeFolder))) {
      for (Map.Entry<String, String> entry : mConfigurations.entrySet()) {
        fs.print(entry.getKey());
        fs.print(CONF_KEY_VALUE_SEPARATOR);
        fs.print(entry.getValue());
        fs.println();
      }

      fs.flush();
    }
  }

  private static File getSmbFile(File homeFolder) {
    File smbFolder = new File(homeFolder, SMB_FOLDER_NAME);
    if (!smbFolder.isDirectory() && !smbFolder.mkdir()) {
      Log.e(TAG, "Failed to obtain .smb folder.");
    }

    return new File(smbFolder, SMB_CONF_FILE);
  }

  private static File getExtSmbFile(File shareFolder) {
    return new File(shareFolder, SMB_CONF_FILE);
  }

  private void setHomeEnv(String absoluteFolder) {
    try {
      setEnv(HOME_VAR, absoluteFolder);
    } catch(ErrnoException e) {
      Log.e(TAG, "Failed to set HOME environment variable.", e);
    }
  }

  private native void setEnv(String var, String value) throws ErrnoException;

  @Override
  public synchronized Iterator<Entry<String, String>> iterator() {
    return mConfigurations.entrySet().iterator();
  }

  private class SyncTask extends BiResultTask<File, Void, Void> {

    private final OnConfigurationChangedListener mListener;

    private SyncTask(OnConfigurationChangedListener listener) {
      mListener = listener;
    }

    @Override
    public Void run(File... params) throws IOException {
      read(params[0]);
      write();
      return null;
    }

    @Override
    public void onSucceeded(Void result) {
      mListener.onConfigurationChanged();
    }
  }

  private class FlushTask extends BiResultTask<Void, Void, Void> {
    private final OnConfigurationChangedListener mListener;

    private FlushTask(OnConfigurationChangedListener listener) {
      mListener = listener;
    }

    @Override
    public Void run(Void... params) throws IOException {
      write();
      return null;
    }

    @Override
    public void onSucceeded(Void result) {
      mListener.onConfigurationChanged();
    }
  }

  interface OnConfigurationChangedListener {
    void onConfigurationChanged();
  }
}
