/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;


import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.urbanairship.job.Job;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;

import java.util.UUID;

/**
 * {@link PushProvider} callback methods.
 *
 * @hide
 */
public abstract class PushProviderBridge {

    /**
     * Callback when the push provider methods finish.
     */
    public interface Callback {
        void onFinish();
    }

    final static String EXTRA_REGISTRATION_ID = "com.urbanairship.EXTRA_REGISTRATION_ID";
    final static String EXTRA_PROVIDER_CLASS = "com.urbanairship.EXTRA_PROVIDER_CLASS";
    final static String EXTRA_PUSH_BUNDLE = "com.urbanairship.EXTRA_PUSH_BUNDLE";

    /**
     * Triggers a registration update.
     *
     * @param context The application context.
     * @param provider The provider's class.
     * @param callback Callback when registration finishes updating.
     */
    public static void requestRegistrationUpdate(@NonNull Context context, @NonNull Class<? extends PushProvider> provider, final Callback callback) {
        Bundle extras = new Bundle();
        extras.putString(EXTRA_PROVIDER_CLASS, provider.toString());

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(ChannelJobHandler.ACTION_UPDATE_PUSH_REGISTRATION)
                                 .setTag(ChannelJobHandler.ACTION_UPDATE_PUSH_REGISTRATION)
                                 .setNetworkAccessRequired(true)
                                 .setAirshipComponent(PushManager.class)
                                 .setExtras(extras)
                                 .build();

        handleJobInfo(context, jobInfo, callback);
    }

    /**
     * Utility method to notify the Urban Airship SDK that registration is finished.
     *
     * @param context The application context.
     * @param provider The provider's class.
     * @param registrationId The registration Id.
     * @param callback Callback when the registration finishes.
     */
    public static void registrationFinished(@NonNull Context context, @NonNull Class<? extends PushProvider> provider, @Nullable String registrationId, final Callback callback) {
        Bundle extras = new Bundle();
        extras.putString(EXTRA_REGISTRATION_ID, registrationId);
        extras.putString(EXTRA_PROVIDER_CLASS, provider.toString());

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(ChannelJobHandler.ACTION_REGISTRATION_FINISHED)
                                 .setAirshipComponent(PushManager.class)
                                 .setExtras(extras)
                                 .build();

        handleJobInfo(context, jobInfo, callback);
    }

    /**
     * Utility method to notify the Urban Airship SDK of a new push message.
     *
     * @param context The application context.
     * @param provider The provider's class.
     * @param pushBundle The push message bundle.
     * @param callback Callback when the push finishing processing.
     */
    @WorkerThread
    public static void receivedPush(@NonNull Context context, @NonNull Class<? extends PushProvider> provider, @NonNull Bundle pushBundle, final Callback callback) {
        Bundle extras = new Bundle();
        extras.putBundle(EXTRA_PUSH_BUNDLE, pushBundle);
        extras.putString(EXTRA_PROVIDER_CLASS, provider.toString());

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(PushJobHandler.ACTION_RECEIVE_MESSAGE)
                                 .setTag(UUID.randomUUID().toString())
                                 .setAirshipComponent(PushManager.class)
                                 .setExtras(extras)
                                 .build();

        handleJobInfo(context, jobInfo, callback);
    }

    /**
     * Helper method to either dispatch the job info or run it directly.
     *
     * @param context The application context.
     * @param jobInfo The job info.
     * @param callback The job callback.
     */
    private static void handleJobInfo(Context context, JobInfo jobInfo, final Callback callback) {
        if (JobDispatcher.shared(context).wakefulDispatch(jobInfo)) {
            if (callback != null) {
                callback.onFinish();
            }
        } else {
            Job job = new Job(jobInfo, false);
            JobDispatcher.shared(context).runJob(job, new JobDispatcher.Callback() {
                @Override
                public void onFinish(Job job, @Job.JobResult int result) {
                    if (callback != null) {
                        callback.onFinish();
                    }
                }
            });
        }
    }
}
