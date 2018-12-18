/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.support.annotation.WorkerThread;

import com.urbanairship.Logger;

/**
 * Helper class that keeps track of the schedule's adapter, coordinator, and schedule. Provides safe wrapper
 * methods around the adapter and coordinator calls.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
final class AdapterWrapper {

    static class DisplayException extends Exception {
        /**
         * Default constructor.
         *
         * @param message The exception message.
         * @param e The root exception.
         */
        DisplayException(String message, Exception e) {
            super(message, e);
        }
    }


    public final String scheduleId;
    public final InAppMessage message;

    public final InAppMessageAdapter adapter;
    public final DisplayCoordinator coordinator;

    public boolean displayed = false;

    AdapterWrapper(@NonNull String scheduleId, @NonNull InAppMessage message, @NonNull InAppMessageAdapter adapter, @NonNull DisplayCoordinator coordinator) {
        this.scheduleId = scheduleId;
        this.message = message;
        this.adapter = adapter;
        this.coordinator = coordinator;
    }

    /**
     * Prepares the adapter.
     *
     * @param context The context.
     * @return The prepare result.
     */
    @InAppMessageAdapter.PrepareResult
    int prepare(Context context) {
        try {
            Logger.debug("AdapterWrapper - Preparing schedule: %s message: %s", scheduleId, message.getId());
            return adapter.onPrepare(context);
        } catch (Exception e) {
            Logger.error(e, "AdapterWrapper - Exception during prepare(Context).");
            return InAppMessageAdapter.RETRY;
        }
    }

    /**
     * Checks if the adapter and coordinator are ready for display.
     *
     * @param activity The activity.
     * @return {@code true} if the coordinator and adapter are ready for display, otherwise {@code false}.
     */
    boolean isReady(@NonNull Activity activity) {
        try {
            return adapter.isReady(activity) && coordinator.isReady(message, displayed);
        } catch (Exception e) {
            Logger.error(e, "AdapterWrapper - Exception during isReady(Activity).");
            return false;
        }
    }

    /**
     * Displays the in-app message.
     *
     * @param activity The activity.
     * @throws DisplayException if the adapter throws an exception.
     */
    void display(@NonNull Activity activity) throws DisplayException {
        Logger.debug("AdapterWrapper - Displaying schedule: %s message: %s", scheduleId, message.getId());
        try {
            DisplayHandler displayHandler = new DisplayHandler(scheduleId);
            adapter.onDisplay(activity, displayed, displayHandler);
            coordinator.onDisplayStarted(activity, message);
            displayed = true;
        } catch (Exception e) {
            throw new DisplayException("Adapter onDisplay(Activity, boolean, DisplayHandler) unexpected exception", e);
        }
    }

    /**
     * Checks with the coordinator if display is still allowed.
     *
     * @param activity The activity.
     * @return {@code true} if the message is allowed to display, otherwise {@code false}.
     */
    boolean isDisplayAllowed(@NonNull Activity activity) {
        try {
            return coordinator.onAllowDisplay(activity, message);
        } catch (Exception e) {
            Logger.error(e, "AdapterWrapper - Exception during isDisplayAllowed(Activity).");
            return false;
        }
    }

    /**
     * Notifies the coordinator the display is finished.
     */
    @MainThread
    void displayFinished() {
        Logger.debug("AdapterWrapper - Display finished: %s message: %s", scheduleId, message.getId());
        try {
            coordinator.onDisplayFinished(message);
        } catch (Exception e) {
            Logger.error(e, "AdapterWrapper - Exception during onDisplayFinished().");
        }
    }

    /**
     * Cleans up the adapter.
     */
    @WorkerThread
    void adapterFinished() {
        Logger.debug("AdapterWrapper - Adapter finished: %s message: %s", scheduleId, message.getId());
        try {
            adapter.onFinish();
        } catch (Exception e) {
            Logger.error(e, "AdapterWrapper - Exception during finish().");
        }
    }
}
