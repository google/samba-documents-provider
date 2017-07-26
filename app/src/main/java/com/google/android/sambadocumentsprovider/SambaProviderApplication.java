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

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import android.util.Log;
import com.google.android.sambadocumentsprovider.SambaConfiguration.OnConfigurationChangedListener;
import com.google.android.sambadocumentsprovider.browsing.NetworkBrowser;
import com.google.android.sambadocumentsprovider.cache.DocumentCache;
import com.google.android.sambadocumentsprovider.nativefacade.CredentialCache;
import com.google.android.sambadocumentsprovider.nativefacade.SambaMessageLooper;
import com.google.android.sambadocumentsprovider.nativefacade.SmbFacade;
import java.io.File;

public class SambaProviderApplication extends Application {

  private static final String TAG = "SambaProviderApplication";

  private final DocumentCache mCache = new DocumentCache();
  private final TaskManager mTaskManager = new TaskManager();

  private SmbFacade mSambaClient;
  private ShareManager mShareManager;
  private NetworkBrowser mNetworkBrowser;

  @Override
  public void onCreate() {
    super.onCreate();

    init(this);
  }

  private void initialize(Context context) {
    if (mSambaClient != null) {
      // Already initialized.
      return;
    }

    initializeSambaConf(context);

    final SambaMessageLooper looper = new SambaMessageLooper();
    CredentialCache credentialCache = looper.getCredentialCache();
    mSambaClient = looper.getClient();

    mShareManager = new ShareManager(context, credentialCache);

    mNetworkBrowser = new NetworkBrowser(mSambaClient, mTaskManager);

    registerNetworkCallback(context);
  }

  private void initializeSambaConf(Context context) {
    final File home = context.getDir("home", MODE_PRIVATE);
    final File share = context.getExternalFilesDir(null);
    final SambaConfiguration sambaConf = new SambaConfiguration(home, share);

    final OnConfigurationChangedListener listener = new OnConfigurationChangedListener() {
      @Override
      public void onConfigurationChanged() {
        if (mSambaClient != null) {
          mSambaClient.reset();
        }
      }
    };

    // Sync from external folder. The reason why we don't use external folder directly as HOME is
    // because there are cases where external storage is not ready, and we don't have an external
    // folder at all.
    if (sambaConf.syncFromExternal(listener)) {
      if (BuildConfig.DEBUG) Log.d(TAG, "Syncing smb.conf from external folder. No need to try "
          + "flushing default config.");
      return;
    }

    sambaConf.flushDefault(listener);
  }

  private void registerNetworkCallback(Context context) {
    final ConnectivityManager manager =
        (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
    manager.registerNetworkCallback(
        new NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build(),
        new NetworkCallback() {
          @Override
          public void onAvailable(Network network) {
            mSambaClient.reset();
          }
        });
  }

  public static void init(Context context) {
    getApplication(context).initialize(context);
  }

  public static ShareManager getServerManager(Context context) {
    return getApplication(context).mShareManager;
  }

  public static SmbFacade getSambaClient(Context context) {
    return getApplication(context).mSambaClient;
  }

  public static DocumentCache getDocumentCache(Context context) {
    return getApplication(context).mCache;
  }

  public static TaskManager getTaskManager(Context context) {
    return getApplication(context).mTaskManager;
  }

  public static NetworkBrowser getNetworkBrowser(Context context) {
    return getApplication(context).mNetworkBrowser;
  }

  private static SambaProviderApplication getApplication(Context context) {
    return ((SambaProviderApplication) context.getApplicationContext());
  }
}
