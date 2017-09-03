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

import android.graphics.Typeface;
import android.support.annotation.IntDef;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.sambadocumentsprovider.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

public abstract class SectionedAdapter extends BaseAdapter {
  private static final String TAG = "SectionedAdapter";

  @IntDef({SECTION_HEADER, REGULAR_ROW, LOADING_ROW})
  @Retention(RetentionPolicy.SOURCE)
  private @interface Type {}
  private static final int SECTION_HEADER = 0;
  private static final int REGULAR_ROW = 1;
  private static final int LOADING_ROW = 2;

  private List<FlatRow> mFlatRows = new ArrayList<>();

  private volatile boolean mIsLoadingData = true;

  public void finishLoading() {
    mIsLoadingData = false;
  }

  public abstract int getSectionCount();

  public abstract String getNameForSection(int sectionIndex);
  public abstract int getIconForSection(int sectionIndex);
  public abstract int getSizeForSection(int sectionIndex);

  public abstract String getNameForRow(int sectionIndex, int rowIndex);
  public abstract int getIconForRow(int sectionIndex, int rowIndex);

  @Override
  public int getCount() {
    return mFlatRows.size() + (mIsLoadingData ? 1 : 0);
  }

  @Override
  public FlatRow getItem(int position) {
    if (position == mFlatRows.size() && mIsLoadingData) {
      return null;
    }

    return mFlatRows.get(position);
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  public @Type int getItemViewType(int position) {
    if (position == mFlatRows.size()) {
      return LOADING_ROW;
    }

    return mFlatRows.get(position).isSectionHeader() ? SECTION_HEADER : REGULAR_ROW;
  }

  @Override
  public View getView(int position, View reuseView, ViewGroup parent) {
    int viewType = getItemViewType(position);

    if (reuseView == null) {
      LayoutInflater inflater = LayoutInflater.from(parent.getContext());

      switch (viewType) {
        case LOADING_ROW:
          reuseView = inflater.inflate(R.layout.browsing_progress_item_layout, null);
          break;
        case SECTION_HEADER:
        case REGULAR_ROW:
          reuseView = inflater.inflate(R.layout.browsing_list_item_layout, null);
          break;
        default:
          // Should never happen.
          throw new RuntimeException("Unknown view type: " + viewType);
      }

      reuseView.setTag(new ViewHolder(reuseView));
    }

    ViewHolder holder = (ViewHolder) reuseView.getTag();

    if (viewType == LOADING_ROW) {
      return reuseView;
    }

    holder.bindView(mFlatRows.get(position));

    return reuseView;
  }

  @Override
  public boolean isEnabled(int position) {
    if (position == mFlatRows.size() && mIsLoadingData) {
      return false;
    }

    return !mFlatRows.get(position).isSectionHeader();
  }

  @Override
  public void notifyDataSetChanged() {
    flattenData();

    super.notifyDataSetChanged();
  }

  private void flattenData() {
    mFlatRows.clear();

    int rowsCount = 0;

    for (int i = 0; i < getSectionCount(); i++) {
      rowsCount = addFlatRow(getNameForSection(i), getIconForSection(i), true, rowsCount);

      for (int j = 0; j < getSizeForSection(i); j++) {
        rowsCount = addFlatRow(getNameForRow(i, j), getIconForRow(i, j), false, rowsCount);
      }
    }
  }

  private int addFlatRow(String name, int iconId, boolean isSectionHeader, int position) {
    FlatRow newRow = FlatRow.create(
            name,
            iconId,
            isSectionHeader,
            position < mFlatRows.size() ? mFlatRows.get(position) : null);


    if (position < mFlatRows.size()) {
      mFlatRows.set(position, newRow);
    } else {
      mFlatRows.add(newRow);
    }

    return position + 1;
  }

  protected boolean isLoadingData() {
    return mIsLoadingData;
  }

  private static class ViewHolder {
    private ImageView mIconImageView;
    private TextView mNameTextView;

    ViewHolder(View parent) {
      mIconImageView = parent.findViewById(R.id.browsing_icon);
      mNameTextView = parent.findViewById(R.id.browsing_name);
    }

    void bindView(FlatRow row) {
      if (mIconImageView == null || mNameTextView == null) {
        return;
      }

      if (row.isSectionHeader()) {
        mIconImageView.setPadding(8, 0, 0, 0);
      }

      mIconImageView.setImageResource(row.getIconId());
      mNameTextView.setText(row.getName());

      if (row.isSectionHeader()) {
        mIconImageView.setPadding(0, 0, 0, 0);
        mNameTextView.setTypeface(null, Typeface.BOLD);
      } else {
        mIconImageView.setPadding(16, 0, 0, 0);
        mNameTextView.setTypeface(null, Typeface.NORMAL);
      }
    }
  }

  private static class FlatRow {
    private String mName;
    private int mIconId;
    private boolean mIsSectionHeader;

    private FlatRow(String name, int iconId, boolean isSectionHeader) {
      mName = name;
      mIconId = iconId;
      mIsSectionHeader = isSectionHeader;
    }

    static FlatRow create(String name, int iconId, boolean isSectionHeader, FlatRow reuse) {
      if (reuse == null) {
        return new FlatRow(name, iconId, isSectionHeader);
      }

      reuse.mName = name;
      reuse.mIconId = iconId;
      reuse.mIsSectionHeader = isSectionHeader;

      return reuse;
    }

    String getName() {
      return mName;
    }

    int getIconId() {
      return mIconId;
    }

    boolean isSectionHeader() {
      return mIsSectionHeader;
    }

    @Override
    public String toString() {
      return mName;
    }
  }
}
