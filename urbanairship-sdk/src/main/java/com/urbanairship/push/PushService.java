package com.urbanairship.push;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.urbanairship.CancelableOperation;


/**
 * Service to process incoming push notifications.
 */
public class PushService extends Service {

    static String ACTION_PROCESS_PUSH = "ACTION_PROCESS_PUSH";

    private int lastStartId = 0;
    private int pushes = 0;

    @Override
    public int onStartCommand(final Intent intent, int flag, int startId) {

        this.lastStartId = startId;

        if (intent != null && ACTION_PROCESS_PUSH.equals(intent.getAction()) && intent.getExtras() != null) {
            Bundle extras = intent.getExtras();
            Bundle pushBundle = extras.getBundle(PushProviderBridge.EXTRA_PUSH_BUNDLE);
            String providerClass = extras.getString(PushProviderBridge.EXTRA_PROVIDER_CLASS);

            this.pushes++;

            IncomingPushRunnable pushRunnable = new IncomingPushRunnable.Builder(getApplicationContext())
                    .setLongRunning(true)
                    .setMessage(new PushMessage(pushBundle))
                    .setProviderClass(providerClass)
                    .setOnFinish(new CancelableOperation(Looper.getMainLooper()) {
                        @Override
                        protected void onRun() {
                            pushes--;
                            if (pushes <= 0) {
                                stopSelf(lastStartId);
                            }

                            WakefulBroadcastReceiver.completeWakefulIntent(intent);
                        }
                    })
                    .build();


            PushManager.PUSH_EXECUTOR.execute(pushRunnable);

        } else {
            if (pushes <= 0) {
                stopSelf(lastStartId);
            }

            WakefulBroadcastReceiver.completeWakefulIntent(intent);
        }

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
