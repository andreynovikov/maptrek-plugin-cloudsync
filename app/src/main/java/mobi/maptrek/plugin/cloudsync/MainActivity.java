package mobi.maptrek.plugin.cloudsync;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.dropbox.core.android.Auth;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListRevisionsResult;
import com.dropbox.core.v2.users.FullAccount;

import java.io.FileNotFoundException;
import java.util.Date;
import java.util.List;

import mobi.maptrek.plugin.cloudsync.dropbox.DownloadFileTask;
import mobi.maptrek.plugin.cloudsync.dropbox.DropboxClientFactory;
import mobi.maptrek.plugin.cloudsync.dropbox.GetCurrentAccountTask;
import mobi.maptrek.plugin.cloudsync.dropbox.GetFileInfoTask;
import mobi.maptrek.plugin.cloudsync.dropbox.GetPreviousVersionsTask;
import mobi.maptrek.plugin.cloudsync.dropbox.UploadFileTask;

public class MainActivity extends Activity {
    public static final String BROADCAST_UPDATE = "mobi.maptrek.plugin.cloudsync.update";

    private View mMainLayout;
    private View mProgressBar;
    private Button mLoginButton;
    private TextView mMessageView;
    private ImageButton mDownloadButton;

    private FileInfo mCloudPlacesFileInfo;
    private FileInfo mLocalPlacesFileInfo;

    private String mName;
    private String mEmail;
    private boolean mAfterLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ActionBar actionBar = getActionBar();
        if (actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(true);

        mMainLayout = findViewById(R.id.mainLayout);
        mProgressBar = findViewById(R.id.progressBar);

        mMessageView = (TextView) findViewById(R.id.message);

        mLoginButton = (Button) findViewById(R.id.loginButton);
        mLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Auth.startOAuth2Authentication(MainActivity.this, getString(R.string.dropboxAppKey));
            }
        });

        mDownloadButton = (ImageButton) findViewById(R.id.downloadButton);
        mDownloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listPlacesFileVersions();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        mLocalPlacesFileInfo = FileTasks.getLocalPlacesFileInfo(this);

        SharedPreferences prefs = getSharedPreferences("dropbox", MODE_PRIVATE);
        String accessToken = prefs.getString("access-token", null);
        if (accessToken == null) {
            showLogin();
            accessToken = Auth.getOAuth2Token();
            if (accessToken != null) {
                prefs.edit().putString("access-token", accessToken).apply();
                initAndLoadData(accessToken, true);
            }
        } else {
            mLoginButton.setVisibility(View.GONE);
            initAndLoadData(accessToken, false);
        }

        registerReceiver(mBroadcastReceiver, new IntentFilter(BROADCAST_UPDATE));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBroadcastReceiver);
    }

    private void initAndLoadData(String accessToken, boolean forceUpdate) {
        DropboxClientFactory.init(accessToken);
        if (forceUpdate || mName == null || mEmail == null) {
            showProgress(true);
            new GetCurrentAccountTask(DropboxClientFactory.getClient(), new GetCurrentAccountTask.Callback() {
                @Override
                public void onComplete(FullAccount result) {
                    mLoginButton.setVisibility(View.GONE);
                    mName = result.getName().getDisplayName();
                    mEmail = result.getEmail();
                    setAccountInfo();
                    updateStatusMessage();
                    getCloudPlacesFileInfo();
                }

                @Override
                public void onError(Exception e) {
                    Log.e(getClass().getName(), "Failed to get account details", e);
                    showProgress(false);
                }
            }).execute();
        } else {
            setAccountInfo();
            updateStatusMessage();
            getCloudPlacesFileInfo();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.actionLogout:
                SharedPreferences prefs = getSharedPreferences("dropbox", MODE_PRIVATE);
                prefs.edit().remove("access-token").apply();
                showLogin();
                invalidateOptionsMenu();
                return true;
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (findViewById(R.id.name).getVisibility() != View.VISIBLE)
            menu.findItem(R.id.actionLogout).setVisible(false);
        return true;
    }

    private void showLogin() {
        mLoginButton.setVisibility(View.VISIBLE);
        mMessageView.setText(R.string.msgLoginRequired);
        mDownloadButton.setVisibility(View.GONE);
        findViewById(R.id.name).setVisibility(View.GONE);
        findViewById(R.id.email).setVisibility(View.GONE);
        mName = null;
        mEmail = null;
        mAfterLogin = true;
    }

    private void setAccountInfo() {
        TextView nameView = (TextView) findViewById(R.id.name);
        nameView.setText(mName);
        nameView.setVisibility(View.VISIBLE);
        TextView emailView = (TextView) findViewById(R.id.email);
        emailView.setText(mEmail);
        emailView.setVisibility(View.VISIBLE);
        invalidateOptionsMenu();
    }

    private void getCloudPlacesFileInfo() {
        Log.e(getClass().getName(), "getCloudPlacesFileInfo()");
        showProgress(true);
        FileTasks.getCloudPlacesFileInfo(new GetFileInfoTask.Callback() {
            @Override
            public void onDataLoaded(FileMetadata result) {
                mCloudPlacesFileInfo = new FileInfo(result.getSize(), result.getClientModified(), result.getRev());
                updateStatusMessage();
                showProgress(false);
                if (mAfterLogin) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setMessage(R.string.msgFoundOldPlacesFile);
                    builder.setPositiveButton(R.string.actionRestore, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            listPlacesFileVersions();
                        }
                    });
                    builder.setNegativeButton(R.string.actionOverwrite, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            uploadPlacesFile();
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.show();
                } else {
                    mDownloadButton.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(Exception e) {
                if (e instanceof FileNotFoundException) {
                    mCloudPlacesFileInfo = new FileInfo(0L, new Date(0L));
                    updateStatusMessage();
                    uploadPlacesFile();
                } else {
                    Log.e(getClass().getName(), "Failed to get places file details", e);
                }
                showProgress(false);
            }
        });
    }

    private void uploadPlacesFile() {
        showProgress(true);
        FileTasks.uploadPlacesFile(this, mLocalPlacesFileInfo.lastModified, new UploadFileTask.Callback() {
            @Override
            public void onUploadComplete(FileMetadata result) {
                mCloudPlacesFileInfo = new FileInfo(result.getSize(), result.getClientModified());
                mDownloadButton.setVisibility(View.VISIBLE);
                updateStatusMessage();
                showProgress(false);
            }

            @Override
            public void onError(Exception e) {
                Log.e(getClass().getName(), "Failed to sync places file", e);
                showProgress(false);
            }
        });
    }

    private void listPlacesFileVersions() {
        showProgress(true);
        FileTasks.getPlacesRevisions(new GetPreviousVersionsTask.Callback() {
            @Override
            public void onDataLoaded(ListRevisionsResult result) {
                showProgress(false);
                final List<FileMetadata> revisions = result.getEntries();
                StringBuilder[] versions = new StringBuilder[revisions.size()];
                for (int i = 0; i < versions.length; i++) {
                    Date modified = revisions.get(i).getClientModified();
                    versions[i] = new StringBuilder();
                    formatTime(versions[i], modified);
                }
                final int[] selection = {0};
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(R.string.titleSelectVersion);
                builder.setSingleChoiceItems(versions, 0, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        selection[0] = which;
                    }
                });
                builder.setPositiveButton(R.string.actionRestore, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        downloadPlacesFileVersion(revisions.get(selection[0]));
                    }
                });
                AlertDialog dialog = builder.create();
                dialog.show();
            }

            @Override
            public void onError(Exception e) {
                Log.e(getClass().getName(), "Failed to get revisions", e);
                showProgress(false);
            }
        });

    }

    private void downloadPlacesFileVersion(FileMetadata revision) {
        showProgress(true);
        FileTasks.downloadPlacesFile(this, revision, new DownloadFileTask.Callback() {
            @Override
            public void onDownloadComplete() {
                mLocalPlacesFileInfo = FileTasks.getLocalPlacesFileInfo(MainActivity.this);
                updateStatusMessage();
                showProgress(false);
            }

            @Override
            public void onError(Exception e) {
                Log.e(getClass().getName(), "Failed to restore places file", e);
                showProgress(false);
            }
        });
    }

    private void formatTime(StringBuilder sb, Date date) {
        sb.append(DateFormat.getDateFormat(MainActivity.this).format(date));
        sb.append(" ");
        sb.append(DateFormat.getTimeFormat(MainActivity.this).format(date));
    }

    private void updateStatusMessage() {
        StringBuilder sb = new StringBuilder("My places:\n");
        if (mLocalPlacesFileInfo == null) {
            sb.append("status unknown");
        } else {
            formatTime(sb, mLocalPlacesFileInfo.lastModified);
        }
        sb.append(" (");
        if (mCloudPlacesFileInfo == null) {
            sb.append("not logged in");
        } else if (mCloudPlacesFileInfo.lastModified.getTime() == 0L) {
            sb.append("never synced");
        } else if (mCloudPlacesFileInfo.lastModified.before(mLocalPlacesFileInfo.lastModified)) {
            formatTime(sb, mCloudPlacesFileInfo.lastModified);
        } else {
            sb.append("synced");
        }
        sb.append(")");
        mMessageView.setText(sb);
    }

    private void showProgress(boolean show) {
        mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        mMainLayout.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mLocalPlacesFileInfo = FileTasks.getLocalPlacesFileInfo(MainActivity.this);
            getCloudPlacesFileInfo();
        }
    };
}
