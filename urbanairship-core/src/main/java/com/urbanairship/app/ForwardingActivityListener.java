/* Copyright Airship and Contributors */

package com.urbanairship.app;

import android.app.Activity;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Activity listener that forwards activity events to a list of listeners.
 */
public class ForwardingActivityListener implements ActivityListener {

    private final List<ActivityListener> listeners = new ArrayList<>();

    /**
     * Adds a listener.
     *
     * @param listener The added listener.
     */
    public void addListener(@NonNull ActivityListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a listener.
     *
     * @param listener The removed listener.
     */
    public void removeListener(@NonNull ActivityListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
        for (ActivityListener listener : new ArrayList<>(listeners)) {
            listener.onActivityCreated(activity, bundle);
        }
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        for (ActivityListener listener : new ArrayList<>(listeners)) {
            listener.onActivityStarted(activity);
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        for (ActivityListener listener : new ArrayList<>(listeners)) {
            listener.onActivityResumed(activity);
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        for (ActivityListener listener : new ArrayList<>(listeners)) {
            listener.onActivityPaused(activity);
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        for (ActivityListener listener : new ArrayList<>(listeners)) {
            listener.onActivityStopped(activity);
        }
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @Nullable Bundle bundle) {
        for (ActivityListener listener : new ArrayList<>(listeners)) {
            listener.onActivitySaveInstanceState(activity, bundle);
        }
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        for (ActivityListener listener : new ArrayList<>(listeners)) {
            listener.onActivityDestroyed(activity);
        }
    }

}
