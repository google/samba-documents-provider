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

import com.google.android.sambadocumentsprovider.SambaConfiguration.OnConfigurationChangedListener;
import com.google.android.sambadocumentsprovider.cache.DocumentCache;
import com.google.android.sambadocumentsprovider.nativefacade.CredentialCache;
import com.google.android.sambadocumentsprovider.nativefacade.SambaMessageLooper;
import com.google.android.sambadocumentsprovider.nativefacade.SmbFacade;

public class SambaProviderApplication extends Application {

  private final DocumentCache mCache = new DocumentCache();
  private final TaskManager mTaskManager = new TaskManager();
  private final SmbFacade mSambaClient;
  private final CredentialCache mCredentialCache;

  private SambaConfiguration mSambaConf;
  private ShareManager mShareManager;

  public SambaProviderApplication() {
    final SambaMessageLooper looper = new SambaMessageLooper();
    mCredentialCache = looper.getCredentialCache();
    mSambaClient = looper.getClient();
  }

  @Override
  public void onCreate() {
    final ConnectivityManager manager =
        (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
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

    initializeSambaConf();
  }

  private void initializeSambaConf() {
    mSambaConf = new SambaConfiguration(getDir("home", MODE_PRIVATE));

    // lmhosts are not used in SambaDocumentsProvider and prioritize bcast because sometimes in home
    // settings DNS will resolve unknown domain name to a specific IP for advertisement.
    //
    // lmhosts -- lmhosts file if existed side by side to smb.conf
    // wins -- Windows Internet Name Service
    // hosts -- hosts file and DNS resolution
    // bcast -- NetBIOS broadcast
    mSambaConf.addConfiguration("name resolve order", "wins bcast hosts");
    mSambaConf.addConfiguration("client min protocol", "SMB2");
    mSambaConf.flushAsDefault(new OnConfigurationChangedListener() {
      @Override
      public void onConfigurationChanged() {
        mSambaClient.reset();
      }
    });
  }

  public static ShareManager getServerManager(Context context) {
    final SambaProviderApplication application = getApplication(context);
    synchronized (application) {
      if (application.mShareManager == null) {
        application.mShareManager = new ShareManager(context, application.mCredentialCache);
      }
      return application.mShareManager;
    }
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

  private static SambaProviderApplication getApplication(Context context) {
    return ((SambaProviderApplication) context.getApplicationContext());
  }
}
