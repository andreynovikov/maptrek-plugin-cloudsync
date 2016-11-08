package mobi.maptrek.plugin.cloudsync.dropbox;

import android.os.AsyncTask;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.Metadata;

import java.io.FileNotFoundException;

/**
 * Task to get file info from Dropbox
 */
public class GetFileInfoTask extends AsyncTask<String, Void, FileMetadata> {

    private final DbxClientV2 mDbxClient;
    private final Callback mCallback;
    private Exception mException;

    public interface Callback {
        void onDataLoaded(FileMetadata result);

        void onError(Exception e);
    }

    public GetFileInfoTask(DbxClientV2 dbxClient, Callback callback) {
        mDbxClient = dbxClient;
        mCallback = callback;
    }

    @Override
    protected void onPostExecute(FileMetadata result) {
        if (result == null)
            mException = new FileNotFoundException();
        if (mException != null)
            mCallback.onError(mException);
        else
            mCallback.onDataLoaded(result);
    }

    @Override
    protected FileMetadata doInBackground(String... params) {
        String path = params[0];
        try {
            // Get file metadata
            Metadata metadata = mDbxClient.files().getMetadata(path);
            if (metadata instanceof FileMetadata)
                return (FileMetadata) metadata;
        } catch (DbxException e) {
            mException = e;
        }
        return null;
    }
}