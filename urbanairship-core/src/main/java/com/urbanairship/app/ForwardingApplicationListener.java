/* Copyright Airship and Contributors */

package com.urbanairship.app;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity listener that forwards application events to a list of listeners.
 */
public class ForwardingApplicationListener implements ApplicationListener {

    private final List<ApplicationListener> listeners = new ArrayList<>();

    /**
     * Adds a listener.
     *
     * @param listener The added listener.
     */
    public void addListener(@NonNull ApplicationListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a listener.
     *
     * @param listener The removed listener.
     */
    public void removeListener(@NonNull ApplicationListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    @Override
    public void onForeground(long time) {
        for (ApplicationListener listener : new ArrayList<>(listeners)) {
            listener.onForeground(time);
        }
    }

    @Override
    public void onBackground(long time) {
        for (ApplicationListener listener : new ArrayList<>(listeners)) {
            listener.onBackground(time);
        }
    }

}
