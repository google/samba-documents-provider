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

import static com.google.android.sambadocumentsprovider.base.DocumentIdHelper.toRootId;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.sambadocumentsprovider.R;
import com.google.android.sambadocumentsprovider.SambaProviderApplication;
import com.google.android.sambadocumentsprovider.ShareManager;
import com.google.android.sambadocumentsprovider.TaskManager;
import com.google.android.sambadocumentsprovider.base.AuthFailedException;
import com.google.android.sambadocumentsprovider.base.OnTaskFinishedCallback;
import com.google.android.sambadocumentsprovider.cache.DocumentCache;
import com.google.android.sambadocumentsprovider.document.DocumentMetadata;
import com.google.android.sambadocumentsprovider.nativefacade.SmbClient;
import com.google.android.sambadocumentsprovider.provider.SambaDocumentsProvider;

import java.util.List;

public class MountServerActivity extends AppCompatActivity {

  private static final String TAG = "MountServerActivity";

  private static final String ACTION_BROWSE = "android.provider.action.BROWSE";

  private static final String SHARE_PATH_KEY = "sharePath";
  private static final String NEEDS_PASSWORD_KEY = "needsPassword";
  private static final String DOMAIN_KEY = "domain";
  private static final String USERNAME_KEY = "username";
  private static final String PASSWORD_KEY = "password";
  private static final String AUTH_LAUNCH_KEY = "authLaunch";


  private final OnClickListener mPasswordStateChangeListener = new OnClickListener() {
    @Override
    public void onClick(View view) {
      final boolean isChecked = mNeedPasswordCheckbox.isChecked();
      setNeedsPasswordState(isChecked);
    }
  };

  private final OnClickListener mMountListener = new OnClickListener() {
    @Override
    public void onClick(View view) {
      tryMount();
    }
  };

  private final OnKeyListener mMountKeyListener = new OnKeyListener() {
    @Override
    public boolean onKey(View view, int i, KeyEvent keyEvent) {
      if (keyEvent.getAction() == KeyEvent.ACTION_UP
          && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
        tryMount();
        return true;
      }
      return false;
    }
  };

  private DocumentCache mCache;
  private TaskManager mTaskManager;
  private ShareManager mShareManager;
  private SmbClient mClient;

  private CheckBox mNeedPasswordCheckbox;
  private View mPasswordHideGroup;

  private EditText mSharePathEditText;
  private EditText mDomainEditText;
  private EditText mUsernameEditText;
  private EditText mPasswordEditText;

  private ConnectivityManager mConnectivityManager;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    mCache = SambaProviderApplication.getDocumentCache(this);
    mTaskManager = SambaProviderApplication.getTaskManager(this);
    mShareManager = SambaProviderApplication.getServerManager(this);
    mClient = SambaProviderApplication.getSambaClient(this);

    mNeedPasswordCheckbox = (CheckBox) findViewById(R.id.needs_password);
    mNeedPasswordCheckbox.setOnClickListener(mPasswordStateChangeListener);

    mPasswordHideGroup = findViewById(R.id.password_hide_group);

    mSharePathEditText = (EditText) findViewById(R.id.share_path);
    mSharePathEditText.setOnKeyListener(mMountKeyListener);

    mUsernameEditText = (EditText) findViewById(R.id.username);
    mDomainEditText = (EditText) findViewById(R.id.domain);
    mPasswordEditText = (EditText) findViewById(R.id.password);
    mPasswordEditText.setOnKeyListener(mMountKeyListener);

    final Button mMountShareButton = (Button) findViewById(R.id.mount);
    mMountShareButton.setOnClickListener(mMountListener);

    final Button cancel = (Button) findViewById(R.id.cancel);
    cancel.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        finish();
      }
    });

    setNeedsPasswordState(false);

    // Set MovementMethod to make it respond to clicks on hyperlinks
    final TextView gplv3Link = (TextView) findViewById(R.id.gplv3_link);
    gplv3Link.setMovementMethod(LinkMovementMethod.getInstance());

    mConnectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

    restoreSavedInstanceState(savedInstanceState);
  }

  private void restoreSavedInstanceState(@Nullable Bundle savedInstanceState) {
    if (savedInstanceState == null) {
      return;
    }

    mSharePathEditText.setText(savedInstanceState.getString(SHARE_PATH_KEY, ""));

    final boolean needsPassword = savedInstanceState.getBoolean(NEEDS_PASSWORD_KEY);
    setNeedsPasswordState(needsPassword);
    if (needsPassword) {
      mDomainEditText.setText(savedInstanceState.getString(DOMAIN_KEY, ""));
      mUsernameEditText.setText(savedInstanceState.getString(USERNAME_KEY, ""));
      mPasswordEditText.setText(savedInstanceState.getString(PASSWORD_KEY, ""));
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

    outState.putString(SHARE_PATH_KEY, mSharePathEditText.getText().toString());
    final boolean needsPassword = mNeedPasswordCheckbox.isChecked();
    outState.putBoolean(NEEDS_PASSWORD_KEY, needsPassword);
    if (needsPassword) {
      outState.putString(DOMAIN_KEY, mDomainEditText.getText().toString());
      outState.putString(USERNAME_KEY, mUsernameEditText.getText().toString());
      outState.putString(PASSWORD_KEY, mPasswordEditText.getText().toString());
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.activity, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem menuItem) {
    switch (menuItem.getItemId()) {
      case R.id.send_feedback:
        sendFeedback();
        return true;
    }

    return false;
  }

  private void sendFeedback() {
    final String url = getString(R.string.feedback_link);

    try {
      final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(this, R.string.no_web_browser, Toast.LENGTH_LONG).show();
    }
  }

  private void tryMount() {
    final NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
    if (info == null || !info.isConnected()) {
      showMessage(R.string.no_active_network);
      return;
    }

    final String[] path = parseSharePath();
    if (path == null) {
      showMessage(R.string.share_path_malformed);
      return;
    }
    final String host = path[0];
    final String share = path[1];

    final String domain = mDomainEditText.getText().toString();
    final String username = mUsernameEditText.getText().toString();
    final String password = mPasswordEditText.getText().toString();

    final DocumentMetadata metadata = DocumentMetadata.createShare(host, share);

    if (mShareManager.isShareMounted(metadata.getUri().toString())) {
      showMessage(R.string.share_already_mounted);
      return;
    }

    mCache.put(metadata);

    final ProgressDialog dialog =
        ProgressDialog.show(this, null, getString(R.string.mounting_share), true);
    final OnTaskFinishedCallback<Void> callback = new OnTaskFinishedCallback<Void>() {
      @Override
      public void onTaskFinished(@Status int status, @Nullable Void item, Exception exception) {
        dialog.dismiss();
        switch (status) {
          case SUCCEEDED:
            clearInputs();
            launchFileManager(metadata);
            showMessage(R.string.share_mounted);
            break;
          case FAILED:
            mCache.remove(metadata.getUri());
            if ((exception instanceof AuthFailedException)) {
              showMessage(R.string.credential_error);
            } else {
              showMessage(R.string.failed_mounting);
            }
            break;
        }
      }
    };
    final MountServerTask task = new MountServerTask(
        metadata, domain, username, password, mClient, mCache, mShareManager, callback);
    mTaskManager.runTask(metadata.getUri(), task);
  }

  private void showMessage(@StringRes int id) {
    Snackbar.make(mNeedPasswordCheckbox, id, Snackbar.LENGTH_SHORT).show();
  }

  private void launchFileManager(DocumentMetadata metadata) {
    final Uri rootUri = DocumentsContract.buildRootUri(
        SambaDocumentsProvider.AUTHORITY, toRootId(metadata));

    if (launchFileManager(Intent.ACTION_VIEW, rootUri)) {
      return;
    }

    if (launchFileManager(ACTION_BROWSE, rootUri)) {
      return;
    }

    Log.w(TAG, "Failed to find an activity to show mounted root.");
  }

  private boolean launchFileManager(String action, Uri data) {
    try {
      final Intent intent = new Intent(action);
      intent.addCategory(Intent.CATEGORY_DEFAULT);
      intent.setData(data);
      startActivity(intent);
      return true;
    } catch (ActivityNotFoundException e) {
      return false;
    }
  }

  private void clearInputs() {
    mSharePathEditText.setText("");
    clearCredentials();
  }

  private void clearCredentials() {
    mDomainEditText.setText("");
    mUsernameEditText.setText("");
    mPasswordEditText.setText("");
  }

  private String[] parseSharePath() {
    final String path = mSharePathEditText.getText().toString();
    if (path.startsWith("\\")) {
      // Possibly Windows share path
      final int endCharacter = path.endsWith("\\") ? path.length() - 1 : path.length();
      final String[] components = path.substring(2, endCharacter).split("\\\\");
      return components.length == 2 ? components : null;
    } else {
      // Try SMB URI
      final Uri smbUri = Uri.parse(path);

      final String host = smbUri.getAuthority();
      if (TextUtils.isEmpty(host)) {
        return null;
      }

      final List<String> pathSegments = smbUri.getPathSegments();
      if (pathSegments.size() != 1) {
        return null;
      }
      final String share = pathSegments.get(0);
      return new String[] { host, share };
    }
  }

  private void setNeedsPasswordState(boolean needsPassword) {
    mNeedPasswordCheckbox.setChecked(needsPassword);

    // TODO: Add animation
    mPasswordHideGroup.setVisibility(needsPassword ? View.VISIBLE : View.GONE);
    if (!needsPassword) {
      clearCredentials();
    }
  }
}
