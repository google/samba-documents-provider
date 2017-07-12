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

  // Hack: magic number of current default conf file size. Used to update old conf without reading
  // file content. Will not be upstreamed to master.
  private static final long DEFAULT_CONF_FILE_LEN = 65L;

  private final File mHomeFolder;
  private final Map<String, String> mConfigurations = new HashMap<>();

  SambaConfiguration(File homeFolder) {
    mHomeFolder = homeFolder;

    setHomeEnv(homeFolder.getAbsolutePath());
  }

  public void flushAsDefault(OnConfigurationChangedListener listener) {
    File smbFile = getSmbFile(mHomeFolder);
    if (!smbFile.exists() || (smbFile.isFile() && smbFile.length() < DEFAULT_CONF_FILE_LEN)) {
      flush(listener);
    }
  }

  public synchronized SambaConfiguration addConfiguration(String key, String value) {
    mConfigurations.put(key, value);
    return this;
  }

  public synchronized SambaConfiguration removeConfiguration(String key) {
    mConfigurations.remove(key);
    return this;
  }

  public void load(OnConfigurationChangedListener listener) {
    new LoadTask(listener).execute();
  }

  public void flush(OnConfigurationChangedListener listener) {
    new FlushTask(listener).execute();
  }

  private synchronized void read() throws IOException {
    mConfigurations.clear();
    try (BufferedReader reader = new BufferedReader(new FileReader(getSmbFile(mHomeFolder)))) {
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

  private class LoadTask extends BiResultTask<Void, Void, Void> {
    private final OnConfigurationChangedListener mListener;

    private LoadTask(OnConfigurationChangedListener listener) {
      mListener = listener;
    }

    @Override
    public Void run(Void... params) throws IOException {
      read();
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
