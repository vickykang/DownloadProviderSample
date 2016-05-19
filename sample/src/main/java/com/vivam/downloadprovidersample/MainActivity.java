package com.vivam.downloadprovidersample;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mozillaonline.providers.DownloadManager;
import com.mozillaonline.providers.downloads.Downloads;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "MainActivity";

    private static final String URL = "http://downloads.jianshu.io/apps/haruki/JianShu-1.10.5.apk";

    private static final int STATUS_INSTALLING = 1 << 5;

    private static final int STATUS_INSTALLED = 1 << 6;

    private static final int MSG_CHANGED = 1;

    private TextView mUrlTextView;
    private Button mDownloadButton;
    private Button mRemoveButton;
    private ProgressBar mProgressBar;
    private TextView mProgressTextView;

    private DownloadManager mDownloadManager;

    private BroadcastReceiver mReceiver;
    private ContentObserver mObserver;

    private Handler mHandler;

    private long downloadId = -1;
    private int status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();

        mDownloadManager = new DownloadManager(getContentResolver(),
                getApplication().getPackageName());
        mReceiver = new DownloadCompletedReceiver();
        registerReceiver(mReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        mHandler = new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MSG_CHANGED) {
                    refresh(downloadId);
                }
            }
        };

        mObserver = new DownloadObserver(mHandler);
        getContentResolver().registerContentObserver(Downloads.CONTENT_URI, true, mObserver);

        initData();
    }

    private void initViews() {
        mUrlTextView = (TextView) findViewById(R.id.tv_url);
        mDownloadButton = (Button) findViewById(R.id.btn_download);
        mRemoveButton = (Button) findViewById(R.id.btn_remove);
        mProgressBar = (ProgressBar) findViewById(R.id.pb_downloading);
        mProgressTextView = (TextView) findViewById(R.id.tv_progress);
        Button wifiButton = (Button) findViewById(R.id.btn_wifi);

        mUrlTextView.setText(URL);

        mDownloadButton.setOnClickListener(this);
        mRemoveButton.setOnClickListener(this);
        wifiButton.setOnClickListener(this);
    }

    private void initData() {
        Cursor cursor = null;
        try {
            cursor = mDownloadManager.query(new DownloadManager.Query());

            if (cursor != null && cursor.moveToFirst()) {
                downloadId = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));

                if (status == DownloadManager.STATUS_PENDING
                        | status == DownloadManager.STATUS_RUNNING
                        | status == DownloadManager.STATUS_PAUSED) {
                    refresh(downloadId);
                } else {
                    notifyStatusChange(status);
                }
            }

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<ApplicationInfo> apps = getInstalledApps();
    }

    private void refresh(long downloadId) {
        long[] bytesAndState = getBytesAndState(downloadId);

        status = (int) bytesAndState[2];
        notifyStatusChange(status);

        long total = bytesAndState[0];
        long current = bytesAndState[1];if (total > 0) {
            if (current < 0) current = 0;
            int progress = (int) ((double) current * 100 / (double) total);
            mProgressBar.setProgress(progress);
            mProgressTextView.setText(progress + " %");
        }
    }

    private void notifyStatusChange(int status) {
        switch (status) {
            case DownloadManager.STATUS_PENDING:
            case DownloadManager.STATUS_RUNNING:
                mDownloadButton.setText("Pause");
                mRemoveButton.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.VISIBLE);
                mProgressTextView.setVisibility(View.VISIBLE);
                break;

            case DownloadManager.STATUS_PAUSED:
                mDownloadButton.setText("Resume");
                mRemoveButton.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.VISIBLE);
                mProgressTextView.setVisibility(View.VISIBLE);
                break;

            case DownloadManager.STATUS_SUCCESSFUL:
                mDownloadButton.setText("Successful");
                mDownloadButton.setEnabled(false);
                mRemoveButton.setVisibility(View.GONE);
                mProgressBar.setVisibility(View.GONE);
                mProgressTextView.setVisibility(View.GONE);
                break;

            case DownloadManager.STATUS_FAILED:
                mDownloadButton.setText("Retry");
                mRemoveButton.setVisibility(View.GONE);
                mProgressBar.setVisibility(View.GONE);
                mProgressTextView.setVisibility(View.GONE);
                break;

            case STATUS_INSTALLING:
                mDownloadButton.setText("Installing");
                mRemoveButton.setVisibility(View.GONE);
                break;

            case STATUS_INSTALLED:
                mDownloadButton.setText("Installed");
                mRemoveButton.setVisibility(View.GONE);
                mProgressBar.setVisibility(View.GONE);
                mProgressTextView.setVisibility(View.GONE);
                break;

            default:
                mDownloadButton.setText("Download");
                mDownloadButton.setEnabled(true);
                mRemoveButton.setVisibility(View.GONE);
                mProgressBar.setVisibility(View.GONE);
                mProgressTextView.setVisibility(View.GONE);
                break;
        }
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();

        if (id == R.id.btn_download) {
            switch (status) {
                case DownloadManager.STATUS_PENDING:
                case DownloadManager.STATUS_RUNNING:
                    pause();
                    break;

                case DownloadManager.STATUS_PAUSED:
                    resume();
                    break;

                case DownloadManager.STATUS_FAILED:


                default:
                    download(URL);
                    break;
            }

        } else if (id == R.id.btn_remove) {
            remove();
        } else if (id == R.id.btn_wifi) {
            setWifiEnable();
        }
    }

    private void setWifiEnable() {
        mDownloadManager.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
    }

    private void download(String url) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setAllowedOverRoaming(false);
        request.setShowRunningNotification(true);
        request.setDestinationInExternalFilesDir(this, null, "");

        downloadId = mDownloadManager.enqueue(request);
    }

    private void pause() {
        if (downloadId > 0) {
            mDownloadManager.pauseDownload(downloadId);
        }
    }

    private void resume() {
        if (downloadId > 0) {
            mDownloadManager.resumeDownload(downloadId);
        }
    }

    private void remove() {
        if (downloadId > 0) {
            mDownloadManager.remove(downloadId);
            downloadId = -1;
            status = 0;
            notifyStatusChange(status);
        }
    }

    /**
     * 获取指定downloadId的总bytes，已经下载的bytes以及下载状态
     * @param downloadId
     * @return
     */
    private long[] getBytesAndState(long downloadId) {
        return getLongs(downloadId,
                DownloadManager.COLUMN_TOTAL_SIZE_BYTES,
                DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                DownloadManager.COLUMN_STATUS);
    }

    private long[] getLongs(long downloadId, @NonNull String... columns) {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        long[] result = new long[columns.length];
        Cursor c = null;
        try {
            c = mDownloadManager.query(query);
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

    /**
     * 获取由本应用中心安装的应用
     */
    private List<ApplicationInfo> getInstalledApps() {

        List<ApplicationInfo> result = new ArrayList<>();

        PackageManager pkgManager = getPackageManager();
        List<ApplicationInfo> apps = pkgManager.getInstalledApplications(
                PackageManager.GET_META_DATA);

        for (ApplicationInfo app : apps) {
            if (getApplication().getPackageName().equals(
                    pkgManager.getInstallerPackageName(app.packageName))) {
                Log.i(TAG, app.packageName);
                result.add(app);
            }
        }

        return result;
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mReceiver);
        getContentResolver().unregisterContentObserver(mObserver);
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /**
     * 监听DownloadProvider数据库变化
     */
    private class DownloadObserver extends ContentObserver {

        private Handler mHandler;

        public DownloadObserver(Handler handler) {
            super(handler);
            mHandler = handler;
        }

        @Override
        public void onChange(boolean selfChange) {
            this.onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            mHandler.removeMessages(MSG_CHANGED);
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_CHANGED), 500);
        }
    }

    private class DownloadCompletedReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            final long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            int status = (int) getLongs(downloadId, DownloadManager.COLUMN_STATUS)[0];

            switch (status) {
                case DownloadManager.STATUS_SUCCESSFUL:
                    Uri uri = mDownloadManager.getUriForDownloadedFile(downloadId);

                    Log.i(TAG, "uri: " + uri.toString());

                    install(uri);
                    status = STATUS_INSTALLING;
                    notifyStatusChange(status);
                    break;

                case DownloadManager.STATUS_FAILED:
                    Toast.makeText(getApplication(), "下载失败", Toast.LENGTH_SHORT).show();
                    break;
            }

            // 下载完成，删除本次下载
            mDownloadManager.remove(downloadId);
        }
    }

    private void install(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
