package mobi.maptrek.plugin.cloudsync;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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
import android.widget.Toast;

import com.dropbox.core.android.Auth;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListRevisionsResult;
import com.dropbox.core.v2.users.FullAccount;

import java.io.FileNotFoundException;
import java.util.Date;
import java.util.List;

import mobi.maptrek.plugin.cloudsync.dropbox.DropboxClientFactory;
import mobi.maptrek.plugin.cloudsync.dropbox.GetCurrentAccountTask;
import mobi.maptrek.plugin.cloudsync.dropbox.GetFileInfoTask;
import mobi.maptrek.plugin.cloudsync.dropbox.GetPreviousVersionsTask;
import mobi.maptrek.plugin.cloudsync.dropbox.UploadFileTask;

public class MainActivity extends Activity {

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

        mMessageView = (TextView) findViewById(R.id.message);

        mLoginButton = (Button) findViewById(R.id.loginButton);
        mLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Auth.startOAuth2Authentication(MainActivity.this, getString(R.string.app_key));
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
    }

    private void initAndLoadData(String accessToken, boolean forceUpdate) {
        DropboxClientFactory.init(accessToken);
        if (forceUpdate || mName == null || mEmail == null) {
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
        FileTasks.getCloudPlacesFileInfo(new GetFileInfoTask.Callback() {
            @Override
            public void onDataLoaded(FileMetadata result) {
                mCloudPlacesFileInfo = new FileInfo(result.getSize(), result.getClientModified(), result.getRev());
                updateStatusMessage();
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
            }
        });
    }

    private void uploadPlacesFile() {
        FileTasks.uploadPlacesFile(this, mLocalPlacesFileInfo.lastModified, new UploadFileTask.Callback() {
            @Override
            public void onUploadComplete(FileMetadata result) {
                mCloudPlacesFileInfo = new FileInfo(result.getSize(), result.getClientModified());
                mDownloadButton.setVisibility(View.VISIBLE);
                updateStatusMessage();
            }

            @Override
            public void onError(Exception e) {
                Log.e(getClass().getName(), "Failed to sync places file", e);
            }
        });
    }

    private void listPlacesFileVersions() {
        FileTasks.getPlacesRevisions(new GetPreviousVersionsTask.Callback() {
            @Override
            public void onDataLoaded(ListRevisionsResult result) {
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
            }
        });

    }

    private void downloadPlacesFileVersion(FileMetadata revision) {
        Toast.makeText(this, revision.getRev() + " " + mCloudPlacesFileInfo.revision, Toast.LENGTH_LONG).show();
        /*
            # Restore the file on Dropbox to a certain revision
            print("Restoring " + BACKUPPATH + " to revision " + rev + " on Dropbox...")
            dbx.files_restore(BACKUPPATH, rev)

            # Download the specific revision of the file at BACKUPPATH to LOCALFILE
            print("Downloading current " + BACKUPPATH + " from Dropbox, overwriting " + LOCALFILE + "...")
            dbx.files_download_to_file(LOCALFILE, BACKUPPATH, rev)
         */
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
}
