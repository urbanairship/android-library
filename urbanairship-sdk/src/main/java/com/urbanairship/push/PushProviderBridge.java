/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;


import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.urbanairship.Logger;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;


/**
 * {@link PushProvider} callback methods.
 *
 * @hide
 */
public abstract class PushProviderBridge {


    final static String EXTRA_PROVIDER_CLASS = "EXTRA_PROVIDER_CLASS";
    final static String EXTRA_PUSH = "EXTRA_PUSH";

    /**
     * Triggers a registration update.
     *
     * @param context The application context.
     */
    public static void requestRegistrationUpdate(@NonNull Context context) {
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(PushManagerJobHandler.ACTION_UPDATE_PUSH_REGISTRATION)
                                 .setId(JobInfo.CHANNEL_UPDATE_PUSH_TOKEN)
                                 .setNetworkAccessRequired(true)
                                 .setAirshipComponent(PushManager.class)
                                 .build();

        JobDispatcher.shared(context).dispatch(jobInfo);
    }

    /**
     * Utility method to notify the Urban Airship SDK of a new push message.
     *
     * @param context The application context.
     * @param provider The provider's class.
     * @param pushMessage The push message.
     * @param callback A runnable to be called when the push is finished processing.
     */
    @WorkerThread
    public static void receivedPush(@NonNull Context context, @NonNull Class<? extends PushProvider> provider, @NonNull PushMessage pushMessage, @NonNull final Runnable callback) {
        Logger.info("Received push: "  + pushMessage);

        // If older than Android O or a high priority message try to start the push service
        if (Build.VERSION.SDK_INT < 26 || PushMessage.PRIORITY_HIGH.equals(pushMessage.getExtra(PushMessage.EXTRA_DELIVERY_PRIORITY, null))) {

            Intent intent = new Intent(context, PushService.class)
                    .setAction(PushService.ACTION_PROCESS_PUSH)
                    .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, pushMessage.getPushBundle())
                    .putExtra(EXTRA_PROVIDER_CLASS, provider.toString());

            try {
                WakefulBroadcastReceiver.startWakefulService(context, intent);
                callback.run();
                return;
            } catch (SecurityException | IllegalStateException e) {
                Logger.error("Unable to run push in the push service.", e);
                WakefulBroadcastReceiver.completeWakefulIntent(intent);
            }
        }

        // Otherwise fallback to running push in th executor
        IncomingPushRunnable pushRunnable = new IncomingPushRunnable.Builder(context)
                .setLongRunning(false)
                .setMessage(pushMessage)
                .setProviderClass(provider.toString())
                .setOnFinish(callback)
                .build();

        PushManager.PUSH_EXECUTOR.execute(pushRunnable);
    }
}
