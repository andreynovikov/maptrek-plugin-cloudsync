package mobi.maptrek.plugin.cloudsync.dropbox;

import android.os.AsyncTask;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Task to download a file from Dropbox
 */
public class DownloadFileTask extends AsyncTask<DownloadFileTask.DownloadInfo, Void, DownloadFileTask.DownloadInfo> {

    private final DbxClientV2 mDbxClient;
    private final Callback mCallback;
    private Exception mException;

    public interface Callback {
        void onDownloadComplete();

        void onError(Exception e);
    }

    public static class DownloadInfo {
        final FileMetadata metadata;
        final OutputStream stream;

        public DownloadInfo(FileMetadata metadata, OutputStream stream) {
            this.metadata = metadata;
            this.stream = stream;
        }
    }


    public DownloadFileTask(DbxClientV2 dbxClient, Callback callback) {
        mDbxClient = dbxClient;
        mCallback = callback;
    }

    @Override
    protected void onPostExecute(DownloadInfo result) {
        try {
            result.stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (mException != null) {
            mCallback.onError(mException);
        } else {
            mCallback.onDownloadComplete();
        }
    }

    @Override
    protected DownloadInfo doInBackground(DownloadInfo... params) {
        DownloadInfo info = params[0];
        try {
            // Download the file
            mDbxClient.files().download(info.metadata.getPathLower(), info.metadata.getRev()).download(info.stream);
        } catch (DbxException | IOException e) {
            mException = e;
        }

        return info;
    }
}