package com.urbanairship.iam;

import android.app.Activity;
import android.support.annotation.CallSuper;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.view.Display;

/**
 * Handles display coordination for in-app messages.
 */
public abstract class DisplayCoordinator {

    /**
     * Display ready callback.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    interface OnDisplayReadyCallback {
        /**
         * Called when {@link #notifyDisplayReady()} is called.
         */
        void onReady();
    }

    private OnDisplayReadyCallback displayReadyCallback;

    /**
     * Sets the display ready callback. Will be notified when {@link #notifyDisplayReady()}
     * is called.
     *
     * @param displayReadyCallback THe display ready callback.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    void setDisplayReadyCallback(@Nullable OnDisplayReadyCallback displayReadyCallback) {
        this.displayReadyCallback = displayReadyCallback;
    }

    /**
     * Notifies the {@link InAppMessageManager} that the coordinator's display is ready.
     */
    @MainThread
    @CallSuper
    public final void notifyDisplayReady() {
        OnDisplayReadyCallback callback = this.displayReadyCallback;
        if (callback != null) {
            callback.onReady();
        }
    }

    /**
     * Called to check if a message is ready to display.
     *
     * @param message The in-app message.
     * @param isRedisplay {@code true} if the message was already displayed, otherwise {@code false}.
     * @return {@code true} to allow the message to display, otherwise {@code false}.
     */
    @MainThread
    public abstract boolean isReady(@NonNull InAppMessage message, boolean isRedisplay);

    /**
     * Notifies the coordinator that message display has begun.
     *
     * @param activity The activity.
     * @param message The in-app message.
     */
    @MainThread
    public abstract void onDisplayStarted(@NonNull Activity activity, @NonNull InAppMessage message);

    /**
     * Called when a message checks to see if it should still display. This is normally called from
     * the activity's onStart method.
     *
     * @param activity The activity.
     * @param message The in-app message.
     */
    @MainThread
    public abstract boolean onAllowDisplay(@NonNull Activity activity, @NonNull InAppMessage message);

    /**
     * Notifies the coordinator that message display has finished.
     *
     * @param message The in-app message.
     */
    @MainThread
    public abstract void onDisplayFinished(@NonNull InAppMessage message);
}