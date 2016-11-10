package mobi.maptrek.plugin.cloudsync.dropbox;

import android.os.AsyncTask;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.ListRevisionsResult;

/**
 * Async task to list previous versions of item
 */
public class GetPreviousVersionsTask extends AsyncTask<String, Void, ListRevisionsResult> {

    private final DbxClientV2 mDbxClient;
    private final Callback mCallback;
    private Exception mException;

    public interface Callback {
        void onDataLoaded(ListRevisionsResult result);

        void onError(Exception e);
    }

    public GetPreviousVersionsTask(DbxClientV2 dbxClient, Callback callback) {
        mDbxClient = dbxClient;
        mCallback = callback;
    }

    @Override
    protected void onPostExecute(ListRevisionsResult result) {
        super.onPostExecute(result);

        if (mException != null) {
            mCallback.onError(mException);
        } else {
            mCallback.onDataLoaded(result);
        }
    }

    @Override
    protected ListRevisionsResult doInBackground(String... params) {
        try {
            return mDbxClient.files().listRevisions(params[0], 20);
        } catch (DbxException e) {
            mException = e;
        }

        return null;
    }
}
