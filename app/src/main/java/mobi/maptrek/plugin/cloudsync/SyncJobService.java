package mobi.maptrek.plugin.cloudsync;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import com.dropbox.core.v2.files.FileMetadata;

import mobi.maptrek.plugin.cloudsync.dropbox.DropboxClientFactory;
import mobi.maptrek.plugin.cloudsync.dropbox.UploadFileTask;

public class SyncJobService extends JobService {
    private static final String TAG = JobService.class.getName();

    private JobTask mTask;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        Log.e(TAG, "onStartJob()");
        mTask = new JobTask(this);
        mTask.execute(jobParameters);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.e(TAG, "onStopJob()");
        return mTask.cancel(true);
    }

    private static class JobTask extends AsyncTask<JobParameters, Void, JobParameters> {
        private final JobService mJobService;

        JobTask(JobService jobService) {
            mJobService = jobService;
        }

        @Override
        protected JobParameters doInBackground(final JobParameters... params) {
            Log.e(TAG, "doInBackground()");
            SharedPreferences prefs = mJobService.getSharedPreferences("dropbox", Context.MODE_PRIVATE);
            String accessToken = prefs.getString("access-token", null);
            if (accessToken == null)
                return params[0];
            if (isCancelled())
                return params[0];
            DropboxClientFactory.init(accessToken);
            FileInfo fileInfo = FileTasks.getLocalPlacesFileInfo(mJobService);
            if (fileInfo == null)
                return params[0];
            if (isCancelled())
                return params[0];
            Log.e(TAG, "Uploading...");
            FileTasks.uploadPlacesFile(mJobService, fileInfo.lastModified, new UploadFileTask.Callback() {
                @Override
                public void onUploadComplete(FileMetadata result) {
                    Log.e(TAG, "Upload complete");
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Failed to sync places file", e);
                    cancel(true);
                }
            });
            return params[0];
        }

        @Override
        protected void onPostExecute(JobParameters jobParameters) {
            mJobService.jobFinished(jobParameters, false);
            Log.e(TAG, "jobFinished()");
        }

        @Override
        protected void onCancelled(JobParameters jobParameters) {
            super.onCancelled();
            mJobService.jobFinished(jobParameters, true);
            Log.e(TAG, "onCancelled()");
        }
    }
}