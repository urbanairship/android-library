package com.urbanairship.push;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.content.WakefulBroadcastReceiver;

/**
 * Service to process incoming push notifications.
 */
public class PushService extends Service {

    static String ACTION_PROCESS_PUSH = "ACTION_PROCESS_PUSH";

    private int lastStartId = 0;
    private int pushes = 0;
    private Handler handler;


    @Override
    public int onStartCommand(final Intent intent, int flag, int startId) {
        this.lastStartId = startId;
        if (intent != null && ACTION_PROCESS_PUSH.equals(intent.getAction()) && intent.getExtras() != null) {
            Bundle extras = intent.getExtras();

            PushMessage message = PushMessage.fromIntent(intent);
            String providerClass = extras.getString(PushProviderBridge.EXTRA_PROVIDER_CLASS);

            if (message == null || providerClass == null) {
                if (pushes <= 0) {
                    stopSelf(lastStartId);
                }
                return START_NOT_STICKY;
            }

            this.pushes++;

            if (handler == null) {
                handler = new Handler(Looper.getMainLooper());
            }

            final IncomingPushRunnable pushRunnable = new IncomingPushRunnable.Builder(getApplicationContext())
                    .setLongRunning(true)
                    .setMessage(message)
                    .setProviderClass(providerClass)
                    .build();

            PushManager.PUSH_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    // Process the push
                    pushRunnable.run();

                    // Finish on the main thread
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            pushes--;
                            if (pushes <= 0) {
                                stopSelf(lastStartId);
                            }

                            //noinspection deprecation
                            WakefulBroadcastReceiver.completeWakefulIntent(intent);
                        }
                    });
                }
            });

        } else {
            if (pushes <= 0) {
                stopSelf(lastStartId);
            }

            if (intent != null && intent.getExtras() != null) {
                //noinspection deprecation
                WakefulBroadcastReceiver.completeWakefulIntent(intent);
            }
        }

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
