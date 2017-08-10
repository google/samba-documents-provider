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

import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class EncryptionManagerTests {
  private static final String TEST_STRING = "testtring";

  private EncryptionManager mManager;

  @Rule
  public ExpectedException mThrown = ExpectedException.none();

  @Before
  public void init() throws EncryptionException {
    mManager = new EncryptionManager(InstrumentationRegistry.getTargetContext());
  }

  @Test
  public void encryption_encryptAndDecryptAreConsistent() throws EncryptionException {
    String encrypted = mManager.encrypt(TEST_STRING);

    assertEquals(TEST_STRING, mManager.decrypt(encrypted));
  }

  @Test
  public void encryption_decryptOnPlainDataThrows() throws EncryptionException {
    mThrown.expect(EncryptionException.class);

    mManager.decrypt(TEST_STRING);
  }
}
