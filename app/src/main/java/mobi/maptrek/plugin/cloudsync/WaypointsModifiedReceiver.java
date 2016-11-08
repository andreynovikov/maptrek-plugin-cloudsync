package mobi.maptrek.plugin.cloudsync;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.dropbox.core.v2.files.FileMetadata;

import mobi.maptrek.plugin.cloudsync.dropbox.DropboxClientFactory;
import mobi.maptrek.plugin.cloudsync.dropbox.UploadFileTask;

public class WaypointsModifiedReceiver extends BroadcastReceiver {
    private static final int JOB_ID = 1001;
    private static final String TAG = WaypointsModifiedReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        //boolean isWiFi = isConnected && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
        if (!isConnected) {
            Log.e(TAG, "No connection");
            JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, SyncJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                    .build();
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            jobScheduler.schedule(job);
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences("dropbox", Context.MODE_PRIVATE);
        String accessToken = prefs.getString("access-token", null);
        if (accessToken == null)
            return;

        DropboxClientFactory.init(accessToken);
        FileInfo fileInfo = FileTasks.getLocalPlacesFileInfo(context);
        if (fileInfo == null)
            return;

        Log.e(TAG, "Uploading...");
        FileTasks.uploadPlacesFile(context, fileInfo.lastModified, new UploadFileTask.Callback() {
            @Override
            public void onUploadComplete(FileMetadata result) {
                Log.e(TAG, "Upload complete");
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to sync places file", e);
            }
        });
    }
}
