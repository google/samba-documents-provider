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
import com.google.android.sambadocumentsprovider.browsing.NetworkBrowsingProvider;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BroadcastBrowsingProvider implements NetworkBrowsingProvider {
  private static final String TAG = "BroadcastBrowsing";
  private static final int LISTENING_TIMEOUT = 3000; // 3 seconds.
  private static final int NBT_PORT = 137;

  private final ExecutorService mExecutor = Executors.newCachedThreadPool();

  private int mTransId = 0;

  @Override
  public List<String> getServers() throws BrowsingException {
    try {
      List<Future<List<String>>> serverFutures = new ArrayList<>();

      List<String> broadcastAddresses = BroadcastUtils.getBroadcastAddress();
      for (String address : broadcastAddresses) {
        mTransId++;
        serverFutures.add(mExecutor.submit(new GetServersForInterfaceTask(address, mTransId)));
      }

      List<String> servers = new ArrayList<>();
      for (Future<List<String>> future : serverFutures) {
        servers.addAll(future.get());
      }

      return servers;
    } catch (IOException | ExecutionException | InterruptedException e) {
      Log.e(TAG, "Failed to get servers via broadcast", e);
      throw new BrowsingException("Failed to get servers via broadcast", e);
    }
  }

  private class GetServersForInterfaceTask implements Callable<List<String>> {
    private final String mBroadcastAddress;
    private final int mTransId;

    GetServersForInterfaceTask(String broadcastAddress, int transId) {
      mBroadcastAddress = broadcastAddress;
      mTransId = transId;
    }

    @Override
    public List<String> call() throws Exception {
      try (DatagramSocket socket = new DatagramSocket()) {
        InetAddress address = InetAddress.getByName(mBroadcastAddress);

        sendNameQueryBroadcast(socket, address);

        return listenForServers(socket);
      }
    }

    private void sendNameQueryBroadcast(
            DatagramSocket socket,
            InetAddress address) throws IOException {
      byte[] data = BroadcastUtils.createPacket(mTransId);
      int dataLength = data.length;

      DatagramPacket packet = new DatagramPacket(data, 0, dataLength, address, NBT_PORT);
      socket.send(packet);

      if (BuildConfig.DEBUG) Log.d(TAG, "Broadcast package sent");
    }

    private List<String> listenForServers(DatagramSocket socket) throws IOException {
      List<String> servers = new ArrayList<>();

      socket.setSoTimeout(LISTENING_TIMEOUT);

      while (true) {
        try {
          byte[] buf = new byte[1024];
          DatagramPacket packet = new DatagramPacket(buf, buf.length);

          socket.receive(packet);

          try {
            servers.addAll(BroadcastUtils.extractServers(packet.getData(), mTransId));
          } catch (BrowsingException e) {
            Log.e(TAG, "Failed to parse incoming packet: ", e);
          }
        } catch (SocketTimeoutException e) {
          break;
        }
      }

      return servers;
    }
  }
}
