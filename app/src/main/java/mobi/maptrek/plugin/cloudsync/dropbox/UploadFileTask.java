package mobi.maptrek.plugin.cloudsync.dropbox;

import android.os.AsyncTask;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.UploadBuilder;
import com.dropbox.core.v2.files.WriteMode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

/**
 * Task to upload a file to Dropbox
 */
public class UploadFileTask extends AsyncTask<UploadFileTask.UploadInfo, Void, FileMetadata> {

    private final DbxClientV2 mDbxClient;
    private final Callback mCallback;
    private Exception mException;

    public interface Callback {
        void onUploadComplete(FileMetadata result);

        void onError(Exception e);
    }

    public static class UploadInfo {
        final String path;
        final Date modified;
        final InputStream stream;

        public UploadInfo(String path, Date modified, InputStream stream) {
            this.path = path;
            this.modified = modified;
            this.stream = stream;
        }
    }

    public UploadFileTask(DbxClientV2 dbxClient, Callback callback) {
        mDbxClient = dbxClient;
        mCallback = callback;
    }

    @Override
    protected void onPostExecute(FileMetadata result) {
        if (mException != null) {
            mCallback.onError(mException);
        } else {
            mCallback.onUploadComplete(result);
        }
    }

    @Override
    protected FileMetadata doInBackground(UploadInfo... params) {
        UploadInfo info = params[0];
        try {
            UploadBuilder uploadBuilder = mDbxClient.files().uploadBuilder(info.path);
            uploadBuilder.withClientModified(info.modified);
            uploadBuilder.withMode(WriteMode.OVERWRITE);
            uploadBuilder.withMute(true);
            // Upload the file
            return uploadBuilder.start().uploadAndFinish(info.stream);
        } catch (DbxException | IOException e) {
            mException = e;
        }

        return null;
    }
}