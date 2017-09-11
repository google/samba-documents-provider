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

package com.google.android.sambadocumentsprovider.mount;

import android.widget.Filter;
import android.widget.Filterable;

import com.google.android.sambadocumentsprovider.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BrowsingAutocompleteAdapter extends SectionedAdapter implements Filterable {
  private final List<ServerInfo> mServers = new ArrayList<>();

  private List<ServerInfo> mFilteredServers = new ArrayList<>();

  public void addServer(String name, List<String> children) {
    synchronized (mServers) {
      mServers.add(new ServerInfo(name, children));
    }

    notifyDataSetChanged();
  }

  @Override
  public Filter getFilter() {
    return new Filter() {
      @Override
      protected FilterResults performFiltering(CharSequence charSequence) {
        String query = charSequence == null ? "" : charSequence.toString().toLowerCase();

        List<ServerInfo> filteredServers = new ArrayList<>();

        if (!query.isEmpty()) {
          filteredServers.add(new ServerInfo(query, Collections.<String>emptyList()));
        }

        if (!isLoadingData()) {
          synchronized (mServers) {
            for (ServerInfo serverInfo : mServers) {

              List<String> displayedShares = new ArrayList<>();
              for (String share : serverInfo.getChildren()) {
                if (share.toLowerCase().contains(query)) {
                  displayedShares.add(share);
                }
              }

              if (!displayedShares.isEmpty()) {
                filteredServers.add(new ServerInfo(serverInfo.getName(), displayedShares));
              }
            }
          }
        }

        FilterResults results = new FilterResults();
        results.values = filteredServers;

        return results;
      }

      @Override
      protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
        mFilteredServers = (List<ServerInfo>) filterResults.values;

        notifyDataSetChanged();
      }
    };
  }

  @Override
  public int getSectionCount() {
    return mFilteredServers.size();
  }

  @Override
  public String getNameForSection(int sectionIndex) {
    return mFilteredServers.get(sectionIndex).getName();
  }

  @Override
  public int getIconForSection(int sectionIndex) {
    return R.drawable.ic_server;
  }

  @Override
  public int getSizeForSection(int sectionIndex) {
    return mFilteredServers.get(sectionIndex).getChildren().size();
  }

  @Override
  public String getNameForRow(int sectionIndex, int rowIndex) {
    return mFilteredServers.get(sectionIndex).getChildren().get(rowIndex);
  }

  @Override
  public int getIconForRow(int sectionIndex, int rowIndex) {
    return R.drawable.ic_folder_shared;
  }

  private static class ServerInfo {
    private final String mName;
    private final List<String> mChildren;

    ServerInfo(String name, List<String> children) {
      this.mName = name;
      this.mChildren = children;
    }

    String getName() {
      return mName;
    }

    List<String> getChildren() {
      return mChildren;
    }
  }
}
