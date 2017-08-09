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

package com.google.android.sambadocumentsprovider.browsing.broadcast;

import android.util.Log;

import com.google.android.sambadocumentsprovider.BuildConfig;
import com.google.android.sambadocumentsprovider.browsing.BrowsingException;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

class BroadcastUtils {
  private static final String TAG = "BroadcastUtils";

  private static final int FILE_SERVER_NODE_TYPE = 0x20;
  private static final int SERVER_NAME_LENGTH = 15;
  private static final String SERVER_NAME_CHARSET = "US-ASCII";

  /**
   * Generates a NetBIOS name query request.
   * https://tools.ietf.org/html/rfc1002
   * Section 4.2.12
   */
  static byte[] createPacket(int transId) {
    ByteBuffer os = ByteBuffer.allocate(50);

    char broadcastFlag = 0x0010;
    char questionCount = 1;
    char answerResourceCount = 0;
    char authorityResourceCount = 0;
    char additionalResourceCount = 0;

    os.putChar((char) transId);
    os.putChar(broadcastFlag);
    os.putChar(questionCount);
    os.putChar(answerResourceCount);
    os.putChar(authorityResourceCount);
    os.putChar(additionalResourceCount);

    // Length of name. 16 bytes of name encoded to 32 bytes.
    os.put((byte) 0x20);

    // '*' character encodes to 2 bytes.
    os.put((byte) 0x43);
    os.put((byte) 0x4b);

    // Write the remaining 15 nulls which encode to 30* 0x41
    for (int i = 0; i < 30; i++) {
      os.put((byte) 0x41);
    }

    // Length of next segment.
    os.put((byte) 0);

    // Question type: Node status
    os.putChar((char) 0x21);

    // Question class: Internet
    os.putChar((char) 0x01);

    return os.array();
  }

  /**
   * Parses a positive response to NetBIOS name request query.
   * https://tools.ietf.org/html/rfc1002
   * Section 4.2.13
   */
  static List<String> extractServers(byte[] data, int expectedTransId) throws BrowsingException {
    try {
      ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);

      int transId = buffer.getChar();
      if (transId != expectedTransId) {
        // This response is not to our broadcast.

        if (BuildConfig.DEBUG) Log.d(TAG, "Irrelevant broadcast response");

        return Collections.emptyList();
      }

      skipBytes(buffer, 2); // Skip flags.

      skipBytes(buffer, 2); // No questions.
      skipBytes(buffer, 2); // Skip answers count.
      skipBytes(buffer, 2); // No authority resources.
      skipBytes(buffer, 2); // No additional resources.

      int nameLength = buffer.get();
      skipBytes(buffer, nameLength);

      skipBytes(buffer, 1);

      int nodeStatus = buffer.getChar();
      if (nodeStatus != 0x20 && nodeStatus != 0x21) {
        throw new BrowsingException("Received negative response for the broadcast");
      }

      skipBytes(buffer, 2);
      skipBytes(buffer, 4);
      skipBytes(buffer, 2);

      int addressListEntryCount = buffer.get();

      List<String> servers = new ArrayList<>();
      for (int i = 0; i < addressListEntryCount; i++) {
        byte[] nameArray = new byte[SERVER_NAME_LENGTH];
        buffer.get(nameArray, 0, SERVER_NAME_LENGTH);

        final String serverName = new String(nameArray, Charset.forName(SERVER_NAME_CHARSET));
        final int type = buffer.get();

        if (type == FILE_SERVER_NODE_TYPE) {
          servers.add(serverName.trim());
        }

        skipBytes(buffer, 2);
      }

      return servers;
    } catch (BufferUnderflowException e) {
      Log.e(TAG, "Malformed incoming packet");

      return Collections.emptyList();
    }
  }

  static List<String> getBroadcastAddress() throws BrowsingException, SocketException {
    Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

    List<String> broadcastAddresses = new ArrayList<>();

    while (interfaces.hasMoreElements()) {
      NetworkInterface networkInterface = interfaces.nextElement();
      if (networkInterface.isLoopback()) {
        continue;
      }

      for (InterfaceAddress interfaceAddress :
              networkInterface.getInterfaceAddresses()) {
        InetAddress broadcast = interfaceAddress.getBroadcast();

        if (broadcast != null) {
          broadcastAddresses.add(broadcast.toString().substring(1));
        }
      }
    }

    return broadcastAddresses;
  }

  private static void skipBytes(ByteBuffer buffer, int bytes) {
    for (int i = 0; i < bytes; i++) {
      buffer.get();
    }
  }
}
