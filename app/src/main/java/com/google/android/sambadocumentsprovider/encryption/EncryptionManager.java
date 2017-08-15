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

package com.google.android.sambadocumentsprovider.encryption;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class EncryptionManager {
  private static final String TAG = "EncryptionManager";

  private static final String ENCRYPTION_MANAGER_PREF_KEY = "encryptionManager";
  private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
  private static final String KEY_ALIAS = "SambaEncryptionKey";
  private static final String AES_CIPHER = KeyProperties.KEY_ALGORITHM_AES + "/" +
          KeyProperties.BLOCK_MODE_GCM + "/" +
          KeyProperties.ENCRYPTION_PADDING_NONE;
  private static final int GCM_TAG_LENGTH = 128;
  private static final int IV_LENGTH = 12;
  private static final String DEFAULT_CHARSET = "UTF-8";

  private static final Random RANDOM = new Random();

  private final SharedPreferences mPref;
  private final EncryptionKey mKey;
  private final KeyStore mStore;

  public EncryptionManager(Context context) {
    mPref = context.getSharedPreferences(ENCRYPTION_MANAGER_PREF_KEY, Context.MODE_PRIVATE);

    mStore = loadKeyStore();
    mKey = loadKey();
  }

  public String encrypt(String data) throws EncryptionException {
    try {
      Cipher cipher = Cipher.getInstance(AES_CIPHER);
      cipher.init(
              Cipher.ENCRYPT_MODE, mKey.getKey(),
              new GCMParameterSpec(GCM_TAG_LENGTH, mKey.getIv()));

      byte[] encrypted = cipher.doFinal(data.getBytes(Charset.forName(DEFAULT_CHARSET)));

      return Base64.encodeToString(encrypted, Base64.DEFAULT);
    } catch (Exception e) {
      throw new EncryptionException("Failed to encrypt data: ", e);
    }
  }

  public String decrypt(String data) throws EncryptionException {
    try {
      Cipher cipher = Cipher.getInstance(AES_CIPHER);
      cipher.init(
              Cipher.DECRYPT_MODE, mKey.getKey(),
              new GCMParameterSpec(GCM_TAG_LENGTH, mKey.getIv()));

      byte[] decrypted = cipher.doFinal(Base64.decode(data, Base64.DEFAULT));
      return new String(decrypted, Charset.forName(DEFAULT_CHARSET));
    } catch (Exception e) {
      throw new EncryptionException("Failed to decrypt data: ", e);
    }
  }

  private EncryptionKey loadKey() {
    SecretKey key;
    KeyGenerator keyGen;

    try {
      key = (SecretKey) mStore.getKey(KEY_ALIAS, null);
      if (key != null) {
        return new EncryptionKey(key, loadIv());
      }

      keyGen = KeyGenerator.getInstance(
              KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);

      KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
              KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .setRandomizedEncryptionRequired(false)
              .build();
      keyGen.init(spec);
    } catch (GeneralSecurityException e) {
      // Should never happen.
      throw new RuntimeException("Failed to load encryption key: ", e);
    }

    key = keyGen.generateKey();
    byte[] iv = generateIv();

    saveIv(iv);

    return new EncryptionKey(key, iv);
  }

  private KeyStore loadKeyStore() {
    try {
      KeyStore store = KeyStore.getInstance(KEYSTORE_PROVIDER);
      store.load(null);
      return store;
    } catch (GeneralSecurityException | IOException e) {
      // Should never happen.
      throw new RuntimeException("Falied to init EncryptionManager: ", e);
    }
  }

  private static byte[] generateIv() {
    byte[] iv = new byte[IV_LENGTH];

    RANDOM.nextBytes(iv);

    return iv;
  }

  private byte[] loadIv() {
    String data = mPref.getString(KEY_ALIAS, null);
    if (data == null) {
      byte[] iv = generateIv();
      saveIv(iv);
      return iv;
    }

    return Base64.decode(data, Base64.DEFAULT);
  }

  private void saveIv(byte[] iv) {
    String data = Base64.encodeToString(iv, Base64.DEFAULT);
    mPref.edit().putString(KEY_ALIAS, data).apply();
  }
}
