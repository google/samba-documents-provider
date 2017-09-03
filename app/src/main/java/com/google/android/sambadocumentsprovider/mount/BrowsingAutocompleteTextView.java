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

import android.content.Context;
import android.util.AttributeSet;

public class BrowsingAutocompleteTextView extends android.support.v7.widget.AppCompatAutoCompleteTextView {
  public BrowsingAutocompleteTextView(Context context) {
    super(context);
  }

  public BrowsingAutocompleteTextView(Context context, AttributeSet attributeSet) {
    super(context, attributeSet);
  }

  public BrowsingAutocompleteTextView(Context context, AttributeSet attributeSet, int v) {
    super(context, attributeSet, v);
  }

  @Override
  public boolean enoughToFilter() {
    return true;
  }

  public void filter() {
    int length = getText().length();
    performFiltering(getText(), length == 0 ? 0 : getText().charAt(length - 1));
  }
}
