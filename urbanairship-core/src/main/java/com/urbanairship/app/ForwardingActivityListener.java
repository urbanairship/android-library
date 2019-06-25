/* Copyright Airship and Contributors */

package com.urbanairship.app;

import android.app.Activity;
import android.os.Bundle;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

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
    public void onActivityCreated(Activity activity, Bundle bundle) {
        for (ActivityListener listener : new ArrayList<>(listeners)) {
            listener.onActivityCreated(activity, bundle);
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        for (ActivityListener listener : new ArrayList<>(listeners)) {
            listener.onActivityStarted(activity);
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        for (ActivityListener listener : new ArrayList<>(listeners)) {
            listener.onActivityResumed(activity);
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        for (ActivityListener listener : new ArrayList<>(listeners)) {
            listener.onActivityPaused(activity);
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        for (ActivityListener listener : new ArrayList<>(listeners)) {
            listener.onActivityStopped(activity);
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
        for (ActivityListener listener : new ArrayList<>(listeners)) {
            listener.onActivitySaveInstanceState(activity, bundle);
        }
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        for (ActivityListener listener : new ArrayList<>(listeners)) {
            listener.onActivityDestroyed(activity);
        }
    }

}
