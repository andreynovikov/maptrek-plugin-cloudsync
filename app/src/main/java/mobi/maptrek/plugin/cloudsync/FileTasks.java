package mobi.maptrek.plugin.cloudsync;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Date;

import mobi.maptrek.plugin.cloudsync.dropbox.DropboxClientFactory;
import mobi.maptrek.plugin.cloudsync.dropbox.GetFileInfoTask;
import mobi.maptrek.plugin.cloudsync.dropbox.UploadFileTask;

class FileTasks {
    private static final String COLUMN_LAST_MODIFIED = "_last_modified";

    private static final String waypointsFile = "/waypoints.sqlitedb";
    private static final Uri waypointsUri = Uri.parse("content://mobi.maptrek.files/databases" + waypointsFile);

    static FileInfo getLocalPlacesFileInfo(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = contentResolver.query(waypointsUri, null, null, null, null);
        if (cursor == null)
            return null;
        int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
        int lastModifiedIndex = cursor.getColumnIndex(COLUMN_LAST_MODIFIED);
        cursor.moveToFirst();
        long size = cursor.getLong(sizeIndex);
        long lastModified = cursor.getLong(lastModifiedIndex);
        cursor.close();
        return new FileInfo(size, new Date(lastModified));
    }

    static void getCloudPlacesFileInfo(GetFileInfoTask.Callback callback) {
        new GetFileInfoTask(DropboxClientFactory.getClient(), callback).execute(waypointsFile);
    }

    static void uploadPlacesFile(Context context, Date lastModified, UploadFileTask.Callback callback) {
        ContentResolver contentResolver = context.getContentResolver();
        InputStream inputStream;
        try {
            inputStream = contentResolver.openInputStream(waypointsUri);
        } catch (FileNotFoundException e) {
            Log.e(FileTasks.class.getName(), "Failed to get places file contents", e);
            return;
        }
        new UploadFileTask(DropboxClientFactory.getClient(), callback)
                .execute(new UploadFileTask.UploadInfo(waypointsFile, lastModified, inputStream));
    }
}
