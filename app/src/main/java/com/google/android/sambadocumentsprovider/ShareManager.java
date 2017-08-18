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

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;
import android.util.Log;

import com.google.android.sambadocumentsprovider.encryption.EncryptionException;
import com.google.android.sambadocumentsprovider.encryption.EncryptionManager;
import com.google.android.sambadocumentsprovider.nativefacade.CredentialCache;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ShareManager implements Iterable<String> {
  private static final String TAG = "ShareManager";

  private static final String SERVER_CACHE_PREF_KEY = "ServerCachePref";
  private static final String SERVER_STRING_SET_KEY = "ServerStringSet";

  // JSON value
  private static final String URI_KEY = "uri";
  private static final String MOUNT_KEY = "mount";
  private static final String CREDENTIAL_TUPLE_KEY = "credentialTuple";
  private static final String WORKGROUP_KEY = "workgroup";
  private static final String USERNAME_KEY = "username";
  private static final String PASSWORD_KEY = "password";

  private final SharedPreferences mPref;
  private final Set<String> mServerStringSet;
  private final Set<String> mMountedServerSet = new HashSet<>();
  private final Map<String, String> mServerStringMap = new HashMap<>();
  private final CredentialCache mCredentialCache;

  private EncryptionManager mEncryptionManager;

  private final List<MountedShareChangeListener> mListeners = new ArrayList<>();

  ShareManager(Context context, CredentialCache credentialCache) {
    mCredentialCache = credentialCache;

    mEncryptionManager = new EncryptionManager(context);

    mPref = context.getSharedPreferences(SERVER_CACHE_PREF_KEY, Context.MODE_PRIVATE);
    // Loading saved servers.
    final Set<String> serverStringSet =
        mPref.getStringSet(SERVER_STRING_SET_KEY, Collections.<String> emptySet());

    final Map<String, ShareTuple> shareMap = new HashMap<>(serverStringSet.size());
    final List<String> forceEncryption = new ArrayList<>();
    for (String serverString : serverStringSet) {
      String decryptedString = serverString;
      try {
        decryptedString = mEncryptionManager.decrypt(serverString);
      } catch (EncryptionException e) {
        Log.i(TAG, "Failed to decrypt server data: ", e);

        forceEncryption.add(serverString);
      }

      String uri = decode(decryptedString, shareMap);
      if (uri != null) {
        mServerStringMap.put(uri, serverString);
      }
    }

    mServerStringSet = new HashSet<>(serverStringSet);

    encryptServers(forceEncryption);

    for (Map.Entry<String, ShareTuple> server : shareMap.entrySet()) {
      final ShareTuple tuple = server.getValue();

      if (tuple.mIsMounted) {
        mMountedServerSet.add(server.getKey());
      }

      mCredentialCache.putCredential(
          server.getKey(), tuple.mWorkgroup, tuple.mUsername, tuple.mPassword);
    }
  }

  /**
   * Save the server and credentials to permanent storage.
   * Throw an exception if a server with such a uri is already present.
   */
  public synchronized void addServer(
          String uri, String workgroup, String username, String password,
          ShareMountChecker checker, boolean mount) throws IOException {

    if (mMountedServerSet.contains(uri)) {
      throw new IllegalStateException("Uri " + uri + " is already stored.");
    }

    saveServerInfo(uri, workgroup, username, password, checker, mount);
  }

  /**
   * Update the server info. If a server with such a uri doesn't exist, create it.
   */
  public synchronized void addOrUpdateServer(
          String uri, String workgroup, String username, String password,
          ShareMountChecker checker, boolean mount) throws IOException {
    saveServerInfo(uri, workgroup, username, password, checker, mount);
  }

  private void saveServerInfo(
          String uri, String workgroup, String username, String password,
          ShareMountChecker checker, boolean mount) throws IOException {

    checkServerCredentials(uri, workgroup, username, password, checker);

    final boolean hasPassword = !TextUtils.isEmpty(username) && !TextUtils.isEmpty(password);
    final ShareTuple tuple = hasPassword
            ? new ShareTuple(workgroup, username, password, mount)
            : ShareTuple.EMPTY_TUPLE;

    updateServersData(uri, tuple, mount);
  }

  private void updateServersData(
          String uri, ShareTuple tuple, boolean shouldNotify) {
    final String serverString = encode(uri, tuple);
    if (serverString == null) {
      throw new IllegalStateException("Failed to encode credential tuple.");
    }

    String encryptedString;
    try {
      encryptedString = mEncryptionManager.encrypt(serverString);
      mServerStringSet.add(encryptedString);
    } catch (EncryptionException e) {
      throw new IllegalStateException("Failed to encrypt server data", e);
    }

    if (tuple.mIsMounted) {
      mMountedServerSet.add(uri);
    } else {
      mMountedServerSet.remove(uri);
    }
    mPref.edit().putStringSet(SERVER_STRING_SET_KEY, mServerStringSet).apply();
    mServerStringMap.put(uri, encryptedString);

    if (shouldNotify) {
      notifyServerChange();
    }
  }

  private void checkServerCredentials(
      String uri, String workgroup, String username, String password, ShareMountChecker checker)
      throws IOException {

    if (!username.isEmpty() && !password.isEmpty()) {
      mCredentialCache.putCredential(uri, workgroup, username, password);
    }

    runMountChecker(uri, checker);
  }

  private void runMountChecker(String uri, ShareMountChecker checker) throws IOException {
    try {
      checker.checkShareMounting();
    } catch (Exception e) {
      Log.i(TAG, "Failed to mount server.", e);
      mCredentialCache.removeCredential(uri);
      throw e;
    }
  }

  private void encryptServers(List<String> servers) {
    for (String server : servers) {
      try {
        mServerStringSet.add(mEncryptionManager.encrypt(server));
      } catch (EncryptionException e) {
        Log.e(TAG, "Failed to encrypt server data: ", e);
      }
    }

    mPref.edit().putStringSet(SERVER_STRING_SET_KEY, mServerStringSet).apply();
  }

  public synchronized boolean unmountServer(String uri) {
    if (!mServerStringMap.containsKey(uri)) {
      return true;
    }

    if (!mServerStringSet.remove(mServerStringMap.get(uri))) {
      Log.e(TAG, "Failed to remove server " + uri);
      return false;
    }

    mServerStringMap.remove(uri);
    mMountedServerSet.remove(uri);

    mPref.edit().putStringSet(SERVER_STRING_SET_KEY, mServerStringSet).apply();

    mCredentialCache.removeCredential(uri);

    notifyServerChange();

    return true;
  }

  @Override
  public synchronized Iterator<String> iterator() {
    // Create a deep copy of current set to avoid modification on iteration.
    return new ArrayList<>(mServerStringMap.keySet()).iterator();
  }

  public synchronized int size() {
    return mServerStringMap.size();
  }

  public synchronized boolean containsShare(String uri) {
    return mServerStringMap.containsKey(uri);
  }

  public synchronized boolean isShareMounted(String uri) {
    return mMountedServerSet.contains(uri);
  }

  private void notifyServerChange() {
    for (int i = mListeners.size() - 1; i >= 0; --i) {
      mListeners.get(i).onMountedServerChange();
    }
  }

  public void addListener(MountedShareChangeListener listener) {
    mListeners.add(listener);
  }

  public void removeListener(MountedShareChangeListener listener) {
    mListeners.remove(listener);
  }

  private static String encode(String uri, ShareTuple tuple) {
    final StringWriter stringWriter = new StringWriter();
    try (final JsonWriter jsonWriter = new JsonWriter(stringWriter)) {
      jsonWriter.beginObject();
      jsonWriter.name(URI_KEY).value(uri);

      jsonWriter.name(CREDENTIAL_TUPLE_KEY);
      encodeTuple(jsonWriter, tuple);
      jsonWriter.endObject();
    } catch (IOException e) {
      Log.e(TAG, "Failed to encode credential for " + uri);
      return null;
    }

    return stringWriter.toString();
  }

  private static void encodeTuple(JsonWriter writer, ShareTuple tuple) throws IOException {
    if (tuple == ShareTuple.EMPTY_TUPLE) {
      writer.nullValue();
    } else {
      writer.beginObject();
      writer.name(WORKGROUP_KEY).value(tuple.mWorkgroup);
      writer.name(USERNAME_KEY).value(tuple.mUsername);
      writer.name(PASSWORD_KEY).value(tuple.mPassword);
      writer.name(MOUNT_KEY).value(tuple.mIsMounted);
      writer.endObject();
    }
  }

  private static String decode(String content, Map<String, ShareTuple> shareMap) {
    final StringReader stringReader = new StringReader(content);
    try (final JsonReader jsonReader = new JsonReader(stringReader)) {
      jsonReader.beginObject();

      String uri = null;
      ShareTuple tuple = null;
      while (jsonReader.hasNext()) {
        final String name = jsonReader.nextName();
        switch (name) {
          case URI_KEY:
            uri = jsonReader.nextString();
            break;
          case CREDENTIAL_TUPLE_KEY:
            tuple = decodeTuple(jsonReader);
            break;
          default:
            Log.w(TAG, "Ignoring unknown key " + name);
        }
      }
      jsonReader.endObject();

      if (uri == null || tuple == null) {
        throw new IllegalStateException("Either uri or tuple is null.");
      }
      shareMap.put(uri, tuple);

      return uri;
    } catch (IOException e) {
      Log.e(TAG, "Failed to load credential.");
      return null;
    }
  }

  private static ShareTuple decodeTuple(JsonReader reader) throws IOException {
    if (reader.peek() == JsonToken.NULL) {
      reader.nextNull();
      return ShareTuple.EMPTY_TUPLE;
    }

    String workgroup = null;
    String username = null;
    String password = null;
    boolean mounted = true;

    reader.beginObject();
    while (reader.hasNext()) {
      String name = reader.nextName();

      switch (name) {
        case WORKGROUP_KEY:
          workgroup = reader.nextString();
          break;
        case USERNAME_KEY:
          username = reader.nextString();
          break;
        case PASSWORD_KEY:
          password = reader.nextString();
          break;
        case MOUNT_KEY:
          mounted = reader.nextBoolean();
        default:
          Log.w(TAG, "Ignoring unknown key " + name);
      }
    }
    reader.endObject();

    return new ShareTuple(workgroup, username, password, mounted);
  }

  private static class ShareTuple {
    private static final ShareTuple EMPTY_TUPLE = new ShareTuple("", "", "", true);

    private final String mWorkgroup;
    private final String mUsername;
    private final String mPassword;
    private boolean mIsMounted;

    private ShareTuple(String workgroup, String username, String password, boolean isMounted) {
      if (workgroup == null) {
        throw new IllegalArgumentException("workgroup is null.");
      }
      if (username == null) {
        throw new IllegalArgumentException("username is null.");
      }
      if (password == null) {
        throw new IllegalArgumentException("password is null.");
      }
      mWorkgroup = workgroup;
      mUsername = username;
      mPassword = password;
      mIsMounted = isMounted;
    }
  }

  public interface ShareMountChecker {
    void checkShareMounting() throws IOException;
  }

  public interface MountedShareChangeListener {
    void onMountedServerChange();
  }
}
