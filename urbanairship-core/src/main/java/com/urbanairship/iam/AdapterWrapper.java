/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.content.Context;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.WorkerThread;

import com.urbanairship.Logger;
import com.urbanairship.iam.assets.Assets;

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

    public final InAppMessageSchedule schedule;
    public final InAppMessage message;
    public final InAppMessageAdapter adapter;
    public final DisplayCoordinator coordinator;

    public boolean displayed = false;

    AdapterWrapper(@NonNull InAppMessageSchedule schedule, @NonNull InAppMessageAdapter adapter, @NonNull DisplayCoordinator coordinator) {
        this.schedule = schedule;
        this.message = schedule.getInfo().getInAppMessage();
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
    int prepare(Context context, Assets assets) {
        try {
            Logger.debug("AdapterWrapper - Preparing schedule: %s message: %s", schedule.getId(), message.getId());
            return adapter.onPrepare(context, assets);
        } catch (Exception e) {
            Logger.error(e, "AdapterWrapper - Exception during prepare(Context).");
            return InAppMessageAdapter.RETRY;
        }
    }

    /**
     * Checks if the adapter and coordinator are ready for display.
     *
     * @param context The context.
     * @return {@code true} if the coordinator and adapter are ready for display, otherwise {@code false}.
     */
    boolean isReady(@NonNull Context context) {
        try {
            return adapter.isReady(context) && coordinator.isReady();
        } catch (Exception e) {
            Logger.error(e, "AdapterWrapper - Exception during isReady(Activity).");
            return false;
        }
    }

    /**
     * Displays the in-app message.
     *
     * @throws DisplayException if the adapter throws an exception.
     */
    void display(@NonNull Context context) throws DisplayException {
        Logger.debug("AdapterWrapper - Displaying schedule: %s message: %s", schedule.getId(), message.getId());
        displayed = true;

        try {
            DisplayHandler displayHandler = new DisplayHandler(schedule.getId());
            adapter.onDisplay(context, displayHandler);
            coordinator.onDisplayStarted(message);
        } catch (Exception e) {
            throw new DisplayException("Adapter onDisplay(Activity, boolean, DisplayHandler) unexpected exception", e);
        }
    }

    /**
     * Notifies the coordinator the display is finished.
     */
    @MainThread
    void displayFinished() {
        Logger.debug("AdapterWrapper - Display finished: %s message: %s", schedule.getId(), message.getId());
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
    void adapterFinished(@NonNull Context context) {
        Logger.debug("AdapterWrapper - Adapter finished: %s message: %s", schedule.getId(), message.getId());
        try {
            adapter.onFinish(context);
        } catch (Exception e) {
            Logger.error(e, "AdapterWrapper - Exception during finish().");
        }
    }

}
