/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;


import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
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

    final static String EXTRA_PROVIDER_CLASS = "com.urbanairship.EXTRA_PROVIDER_CLASS";
    final static String EXTRA_PUSH_BUNDLE = "com.urbanairship.EXTRA_PUSH_BUNDLE";

    /**
     * Triggers a registration update.
     *
     * @param context The application context.
     * @param provider The provider's class.
     */
    public static void requestRegistrationUpdate(@NonNull Context context, @NonNull Class<? extends PushProvider> provider) {
        Bundle extras = new Bundle();
        extras.putString(EXTRA_PROVIDER_CLASS, provider.toString());

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(PushManagerJobHandler.ACTION_UPDATE_PUSH_REGISTRATION)
                                 .setTag(PushManagerJobHandler.ACTION_UPDATE_PUSH_REGISTRATION)
                                 .setNetworkAccessRequired(true)
                                 .setAirshipComponent(PushManager.class)
                                 .setExtras(extras)
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

        if (Build.VERSION.SDK_INT >= 26) {
            IncomingPushRunnable pushRunnable = new IncomingPushRunnable.Builder(context)
                    .setLongRunning(false)
                    .setMessage(pushMessage)
                    .setProviderClass(provider.toString())
                    .setOnFinish(callback)
                    .build();

            PushManager.PUSH_EXECUTOR.execute(pushRunnable);
        } else {
            Intent intent = new Intent(context, PushService.class)
                    .setAction(PushService.ACTION_PROCESS_PUSH)
                    .putExtra(EXTRA_PUSH_BUNDLE, pushMessage.getPushBundle())
                    .putExtra(EXTRA_PROVIDER_CLASS, provider.toString());

            WakefulBroadcastReceiver.startWakefulService(context, intent);
            callback.run();
        }
    }
}
