package com.mozillaonline.providers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

/**
 * Created by kangweodai on 18/05/16.
 */
public abstract class CompleteReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        DownloadManager manager = new DownloadManager(context.getContentResolver(),
                context.getPackageName());

        final long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        int status = (int) getLongs(manager, downloadId, DownloadManager.COLUMN_STATUS)[0];

        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            onSuccess();
        } else if (status == DownloadManager.STATUS_FAILED) {
            onFail();
        }
    }

    protected abstract void onSuccess();

    protected abstract void onFail();

    protected long[] getLongs(DownloadManager manager, long downloadId, String... columns) {

        if (columns == null) return null;

        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        long[] result = new long[columns.length];
        Cursor c = null;
        try {
            c = manager.query(query);
            if (c != null && c.moveToFirst()) {
                for (int i = 0; i < columns.length; i++) {
                    result[i] = c.getLong(c.getColumnIndexOrThrow(columns[i]));
                }
            }
        } finally {
            if (c != null) c.close();
        }

        return result;
    }
}
