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
  private static final String CREDENTIAL_TUPLE_KEY = "credentialTuple";
  private static final String WORKGROUP_KEY = "workgroup";
  private static final String USERNAME_KEY = "username";
  private static final String PASSWORD_KEY = "password";

  private final SharedPreferences mPref;
  private final Set<String> mServerStringSet;
  private final Map<String, String> mServerStringMap = new HashMap<>();
  private final CredentialCache mCredentialCache;

  private final List<MountedShareChangeListener> mListeners = new ArrayList<>();

  ShareManager(Context context, CredentialCache credentialCache) {
    mCredentialCache = credentialCache;

    mPref = context.getSharedPreferences(SERVER_CACHE_PREF_KEY, Context.MODE_PRIVATE);
    // Loading mounted servers
    final Set<String> serverStringSet =
        mPref.getStringSet(SERVER_STRING_SET_KEY, Collections.<String> emptySet());
    final Map<String, CredentialTuple> credentialMap = new HashMap<>(serverStringSet.size());
    for (String serverString : serverStringSet) {
      // TODO: Add decryption
      String uri = decode(serverString, credentialMap);
      if (uri != null) {
        mServerStringMap.put(uri, serverString);
      }
    }

    mServerStringSet = new HashSet<>(serverStringSet);

    for (Map.Entry<String, CredentialTuple> server : credentialMap.entrySet()) {
      final CredentialTuple tuple = server.getValue();
      mCredentialCache.putCredential(
          server.getKey(), tuple.mWorkgroup, tuple.mUsername, tuple.mPassword);
    }
  }

  public synchronized void mountServer(
      String uri, String workgroup, String username, String password, ShareMountChecker checker)
      throws IOException {
    if (mServerStringMap.containsKey(uri)) {
      throw new IllegalStateException("Uri " + uri + " is already mounted.");
    }

    final boolean hasPassword = !TextUtils.isEmpty(username) && !TextUtils.isEmpty(password);
    if (hasPassword) {
      mCredentialCache.putCredential(uri, workgroup, username, password);
    }
    try {
      checker.checkShareMounting();
    } catch (Exception e) {
      Log.i(TAG, "Failed to mount server.", e);
      mCredentialCache.removeCredential(uri);
      throw e;
    }

    final CredentialTuple tuple = hasPassword
        ? new CredentialTuple(workgroup, username, password)
        : CredentialTuple.EMPTY_TUPLE;
    final String serverString = encode(uri, tuple);
    if (serverString == null) {
      throw new IllegalStateException("Failed to encode credential tuple.");
    }
    // TODO: Add encryption
    mServerStringSet.add(serverString);
    mPref.edit().putStringSet(SERVER_STRING_SET_KEY, mServerStringSet).apply();

    mServerStringMap.put(uri, serverString);
    notifyServerChange();
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

  private static String encode(String uri, CredentialTuple tuple) {
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

  private static void encodeTuple(JsonWriter writer, CredentialTuple tuple) throws IOException {
    if (tuple == CredentialTuple.EMPTY_TUPLE) {
      writer.nullValue();
    } else {
      writer.beginObject();
      writer.name(WORKGROUP_KEY).value(tuple.mWorkgroup);
      writer.name(USERNAME_KEY).value(tuple.mUsername);
      writer.name(PASSWORD_KEY).value(tuple.mPassword);
      writer.endObject();
    }
  }

  private static String decode(String content, Map<String, CredentialTuple> credentialMap) {
    final StringReader stringReader = new StringReader(content);
    try (final JsonReader jsonReader = new JsonReader(stringReader)) {
      jsonReader.beginObject();

      String uri = null;
      CredentialTuple tuple = null;
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
      credentialMap.put(uri, tuple);

      return uri;
    } catch (IOException e) {
      Log.e(TAG, "Failed to load credential.");
      return null;
    }
  }

  private static CredentialTuple decodeTuple(JsonReader reader) throws IOException {
    if (reader.peek() == JsonToken.NULL) {
      reader.nextNull();
      return CredentialTuple.EMPTY_TUPLE;
    }

    String workgroup = null;
    String username = null;
    String password = null;

    reader.beginObject();
    while (reader.hasNext()) {
      String name = reader.nextName();
      String value = reader.nextString();

      switch (name) {
        case WORKGROUP_KEY:
          workgroup = value;
          break;
        case USERNAME_KEY:
          username = value;
          break;
        case PASSWORD_KEY:
          password = value;
          break;
        default:
          Log.w(TAG, "Ignoring unknown key " + name);
      }
    }
    reader.endObject();

    return new CredentialTuple(workgroup, username, password);
  }

  private static class CredentialTuple {
    private static final CredentialTuple EMPTY_TUPLE = new CredentialTuple("", "", "");

    private final String mWorkgroup;
    private final String mUsername;
    private final String mPassword;

    private CredentialTuple(String workgroup, String username, String password) {
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
    }
  }

  public interface ShareMountChecker {
    void checkShareMounting() throws IOException;
  }

  public interface MountedShareChangeListener {
    void onMountedServerChange();
  }
}
