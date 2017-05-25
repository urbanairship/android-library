/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Global activity monitor.
 */
public class ActivityMonitor implements Application.ActivityLifecycleCallbacks {

    // Brief delay, to give the app a chance to perform screen rotation cleanup
    private static final long BACKGROUND_DELAY_MS = 200;

    private static ActivityMonitor singleton;

    private final Handler handler;
    private final List<Listener> listeners = new ArrayList<>();
    private final Runnable backgroundRunnable;

    private int startedActivities = 0;
    private long backgroundTime;
    private boolean isForeground;

    public ActivityMonitor() {
        this.handler = new Handler(Looper.getMainLooper());
        this.backgroundRunnable = new Runnable() {
            @Override
            public void run() {
                isForeground = false;
                for (Listener listener : new ArrayList<>(listeners)) {
                    listener.onBackground(backgroundTime);
                }
            }
        };
    }

    /**
     * Creates and retrieves the shared activity monitor instance.
     *
     * @param context The application context.
     * @return The singleton.
     */
    public static ActivityMonitor shared(@NonNull Context context) {
        if (singleton != null) {
            return singleton;
        }

        singleton = new ActivityMonitor();
        ((Application) context.getApplicationContext()).registerActivityLifecycleCallbacks(singleton);
        return singleton;
    }

    /**
     * Adds a listener to the activity monitor.
     *
     * @param listener The added listener.
     */
    public void addListener(@NonNull final Listener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a listener from the activity monitor.
     *
     * @param listener The removed listener.
     */
    public void removeListener(Listener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Determines if the application is in the foreground.
     *
     * @return <code>true</code> if the application is in the foreground, otherwise
     * <code>false</code>.
     */
    public boolean isAppForegrounded() {
        return isForeground;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {}

    @Override
    public void onActivityStarted(Activity activity) {
        handler.removeCallbacks(backgroundRunnable);
        startedActivities++;
        if (!isForeground) {
            isForeground = true;
            long timeStamp = System.currentTimeMillis();
            for (Listener listener : new ArrayList<>(listeners)) {
                if (listener != null) {
                    listener.onForeground(timeStamp);
                }
            }
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onActivityResumed(activity);
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onActivityPaused(activity);
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        if (startedActivities > 0) {
            startedActivities--;
        }

        if (startedActivities == 0 && isForeground) {
            backgroundTime = System.currentTimeMillis() + BACKGROUND_DELAY_MS;
            handler.postDelayed(backgroundRunnable, BACKGROUND_DELAY_MS);
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {}

    @Override
    public void onActivityDestroyed(Activity activity) {}

    /**
     * Listener class for activity updates.
     */
    public static abstract class Listener {

        public Listener() {}

        /**
         * Called when the app is foregrounded.
         */
        public void onForeground(long time) {}

        /**
         * Called when the app is backgrounded.
         */
        public void onBackground(long time) {}

        /**
         * Called when an activity is paused.
         *
         * @param activity The paused activity.
         */
        public void onActivityPaused(Activity activity) {}

        /**
         * Called when an activity is resumed.
         *
         * @param activity The resumed activity.
         */
        public void onActivityResumed(Activity activity) {}
    }
}
