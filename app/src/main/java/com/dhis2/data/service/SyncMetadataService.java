package com.dhis2.data.service;

import android.app.Notification;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.dhis2.App;
import com.dhis2.R;
import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;

import javax.inject.Inject;

import io.reactivex.functions.Consumer;

public class SyncMetadataService extends JobService implements SyncView {
    private final static int NOTIFICATION_ID = 0xdeadbeef;
    private final static int NOTIFICATION_ID_EVENT = 0xDEADBEEE;
    private final static int NOTIFICATION_ID_TEI = 0xDEADBEED;

    @Inject
    SyncPresenter syncPresenter;

    @Inject
    NotificationManagerCompat notificationManager;

    // @NonNull
    SyncResult syncResult;
    private JobParameters job;

    @Override
    public void onCreate() {
        super.onCreate();

        ((App) getApplicationContext()).userComponent()
                .plus(new MetadataServiceModule()).inject(this);


    }

    @Override
    public void onDestroy() {
        syncPresenter.onDetach();
        super.onDestroy();
    }

    @Override
    public boolean onStartJob(JobParameters job) {
        this.job = job;
        syncPresenter.onAttach(this);
        syncResult = SyncResult.idle();
        if (!syncResult.inProgress()) {
            syncPresenter.sync();
        }
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters job) {
        return true;
    }


    @NonNull
    @Override
    public Consumer<SyncResult> update(SyncState syncState) {
        return result -> {
            Notification notification;
            syncResult = result;

            if (result.inProgress()) {
                notification = new NotificationCompat.Builder(getApplicationContext())
                        .setSmallIcon(R.drawable.ic_sync_black)
                        .setContentTitle(getTextForNotification(syncState))
                        .setContentText(getString(R.string.sync_text))
                        .setProgress(0, 0, true)
                        .setOngoing(true)
                        .build();
            } else if (result.isSuccess()) {
                syncPresenter.onDetach();
                jobFinished(job, true);
                notification = new NotificationCompat.Builder(getApplicationContext())
                        .setSmallIcon(R.drawable.ic_done_black)
                        .setContentTitle(getTextForNotification(syncState) + " " + getString(R.string.sync_complete_title))
                        .setContentText(getString(R.string.sync_complete_text))
                        .build();
            } else if (!result.isSuccess()) { // NOPMD
                syncPresenter.onDetach();
                jobFinished(job, true);
                notification = new NotificationCompat.Builder(getApplicationContext())
                        .setSmallIcon(R.drawable.ic_sync_error_black)
                        .setContentTitle(getTextForNotification(syncState) + " " + getString(R.string.sync_error_title))
                        .setContentText(getString(R.string.sync_error_text))
                        .build();
            } else {
                throw new IllegalStateException();
            }
            notificationManager.notify(getNotId(syncState), notification);
        };
    }


    public String getTextForNotification(SyncState syncState) {
        switch (syncState) {
            case METADATA:
                return getString(R.string.sync_metadata);
            case EVENTS:
                return getString(R.string.sync_events);
            default:
                return getString(R.string.sync_tei);
        }
    }

    public int getNotId(SyncState syncState) {
        switch (syncState) {
            case METADATA:
                return NOTIFICATION_ID;
            case EVENTS:
                return NOTIFICATION_ID_EVENT;
            case TEI:
                return NOTIFICATION_ID_TEI;
        }
        return -1;
    }
}