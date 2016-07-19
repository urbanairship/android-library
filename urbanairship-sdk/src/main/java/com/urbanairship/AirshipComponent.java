/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Base class for Urban Airship components.
 */
public abstract class AirshipComponent {

    /**
     * Used by {@link AirshipService} to call {@link #onHandleIntent(UAirship, Intent)} for
     * the component.
     *
     * @hide
     */
    final Executor serviceExecutor = Executors.newSingleThreadExecutor();

    /**
     * Initialize the manager.
     * Called in {@link UAirship} during takeoff.
     *
     * @hide
     */
    protected void init() {}

    /**
     * Tear down the manager.
     * Called in {@link UAirship} during land.
     *
     * @hide
     */
    protected void tearDown() {}

    /**
     * Called when {@link AirshipService} receives an intent for the component.
     *
     * @param airship The airship instance.
     * @param intent The intent.
     *
     * @hide
     */
    @WorkerThread
    protected void onHandleIntent(@NonNull UAirship airship, @NonNull  Intent intent) {
    }

    /**
     * Called by {@link AirshipService} to determine if the component can
     * handle an intent. If {@code true}, then {@link #onHandleIntent(UAirship, Intent)}
     * will be called in a worker thread with the delivered intent.
     *
     * @param airship The airship instance.
     * @param action The intent action.
     *
     * @return {@code true} if the component can handle the intent, otherwise {@code false}.
     *
     * @hide
     */
    protected boolean acceptsIntentAction(@NonNull UAirship airship, @NonNull String action) {
        return false;
    }
}
