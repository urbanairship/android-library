/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;


import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.job.Job;
import com.urbanairship.job.JobDispatcher;

/**
 * {@link PushProvider} callback methods.
 * @hide
 */
public abstract class PushProviderBridge {

    final static String EXTRA_REGISTRATION_ID = "com.urbanairship.EXTRA_REGISTRATION_ID";
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

        Job messageJob = Job.newBuilder()
                            .setAction(ChannelJobHandler.ACTION_UPDATE_PUSH_REGISTRATION)
                            .setTag(ChannelJobHandler.ACTION_UPDATE_PUSH_REGISTRATION)
                            .setNetworkAccessRequired(true)
                            .setAirshipComponent(PushManager.class)
                            .setExtras(extras)
                            .build();

        JobDispatcher.shared(context).wakefulDispatch(messageJob);
    }

    /**
     * Utility method to notify the Urban Airship SDK that registration is finished.
     *
     * @param context The application context.
     * @param provider The provider's class.
     * @param registrationId The registration Id.
     */
    public static void registrationFinished(@NonNull Context context, @NonNull Class<? extends PushProvider> provider, @Nullable String registrationId) {
        Bundle extras = new Bundle();
        extras.putString(EXTRA_REGISTRATION_ID, registrationId);
        extras.putString(EXTRA_PROVIDER_CLASS, provider.toString());

        Job messageJob = Job.newBuilder()
                            .setAction(ChannelJobHandler.ACTION_REGISTRATION_FINISHED)
                            .setAirshipComponent(PushManager.class)
                            .setExtras(extras)
                            .build();

        JobDispatcher.shared(context).wakefulDispatch(messageJob);
    }

    /**
     * Utility method to notify the Urban Airship SDK of a new push message.
     *
     * @param context The application context.
     * @param provider The provider's class.
     * @param pushBundle The push message bundle.
     */
    public static void receivedPush(@NonNull Context context, @NonNull Class<? extends PushProvider> provider, @NonNull Bundle pushBundle) {
        Bundle extras = new Bundle();
        extras.putBundle(EXTRA_PUSH_BUNDLE, pushBundle);
        extras.putString(EXTRA_PROVIDER_CLASS, provider.toString());

        Job messageJob = Job.newBuilder()
                            .setAction(PushJobHandler.ACTION_RECEIVE_MESSAGE)
                            .setAirshipComponent(PushManager.class)
                            .setExtras(extras)
                            .build();

        JobDispatcher.shared(context).wakefulDispatch(messageJob);
    }
}
