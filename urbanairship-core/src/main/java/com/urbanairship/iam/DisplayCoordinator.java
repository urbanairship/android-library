/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

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
     * Called to check if a the display is ready.
     *
     * @return {@code true} if the display is ready, otherwise {@code false}.
     */
    @MainThread
    public abstract boolean isReady();

    /**
     * Notifies the coordinator that message display has begun.
     *
     * @param message The in-app message.
     */
    @MainThread
    public abstract void onDisplayStarted(@NonNull InAppMessage message);

    /**
     * Notifies the coordinator that message display has finished.
     *
     * @param message The in-app message.
     */
    @MainThread
    public abstract void onDisplayFinished(@NonNull InAppMessage message);

}