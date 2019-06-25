/* Copyright Airship and Contributors */

package com.urbanairship.app;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.Predicate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Global activity monitor.
 */
public class GlobalActivityMonitor implements ActivityMonitor {

    // Brief delay, to give the app a chance to perform screen rotation cleanup
    private static final long BACKGROUND_DELAY_MS = 200;

    private static GlobalActivityMonitor singleton;

    private final Handler handler;
    private final Runnable backgroundRunnable;

    private int startedActivities = 0;
    private long backgroundTime;
    private boolean isForeground;
    private List<Activity> resumedActivities = new ArrayList<>();

    @NonNull
    private final ForwardingApplicationListener forwardingApplicationListener = new ForwardingApplicationListener();

    @NonNull
    private final ForwardingActivityListener forwardingActivityListener = new ForwardingActivityListener() {

        @Override
        public void onActivityResumed(Activity activity) {
            resumedActivities.add(activity);
            super.onActivityResumed(activity);
        }

        @Override
        public void onActivityPaused(Activity activity) {
            resumedActivities.remove(activity);
            super.onActivityPaused(activity);
        }

        @Override
        public void onActivityStarted(Activity activity) {
            handler.removeCallbacks(backgroundRunnable);
            startedActivities++;
            if (!isForeground) {
                isForeground = true;
                forwardingApplicationListener.onForeground(System.currentTimeMillis());
            }

            super.onActivityStarted(activity);
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

            super.onActivityStopped(activity);
        }
    };

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public GlobalActivityMonitor() {
        this.handler = new Handler(Looper.getMainLooper());
        this.backgroundRunnable = new Runnable() {
            @Override
            public void run() {
                isForeground = false;
                forwardingApplicationListener.onBackground(backgroundTime);
            }
        };
    }

    /**
     * Registers for activity lifecycle callbacks.
     *
     * @param context The application context.
     * @hide
     */
    @VisibleForTesting
    void registerListener(@NonNull Context context) {
        ((Application) context.getApplicationContext()).registerActivityLifecycleCallbacks(forwardingActivityListener);
    }

    /**
     * Unregisters for activity lifecycle callbacks.
     *
     * @param context The application context.
     */
    @VisibleForTesting
    void unregisterListener(@NonNull Context context) {
        ((Application) context.getApplicationContext()).unregisterActivityLifecycleCallbacks(forwardingActivityListener);
    }

    /**
     * Creates and retrieves the shared activity monitor instance.
     *
     * @param context The application context.
     * @return The singleton.
     */
    @NonNull
    public static GlobalActivityMonitor shared(@NonNull Context context) {
        if (singleton == null) {
            synchronized (GlobalActivityMonitor.class) {
                if (singleton == null) {
                    singleton = new GlobalActivityMonitor();
                    singleton.registerListener(context);
                }
            }
            return singleton;
        }

        return singleton;
    }

    @Override
    public void addActivityListener(@NonNull ActivityListener listener) {
        forwardingActivityListener.addListener(listener);
    }

    @Override
    public void removeActivityListener(@NonNull ActivityListener listener) {
        forwardingActivityListener.removeListener(listener);
    }

    @Override
    public void addApplicationListener(@NonNull ApplicationListener listener) {
        forwardingApplicationListener.addListener(listener);
    }

    @Override
    public void removeApplicationListener(@NonNull ApplicationListener listener) {
        forwardingApplicationListener.removeListener(listener);
    }

    @Override
    public boolean isAppForegrounded() {
        return isForeground;
    }

    @Override
    @NonNull
    @MainThread
    public List<Activity> getResumedActivities() {
        return Collections.unmodifiableList(resumedActivities);
    }

    @Override
    @NonNull
    @MainThread
    public List<Activity> getResumedActivities(@NonNull Predicate<Activity> filter) {
        List<Activity> activities = new ArrayList<>();

        for (Activity activity : resumedActivities) {
            if (filter.apply(activity)) {
                activities.add(activity);
            }
        }

        return activities;
    }

}
