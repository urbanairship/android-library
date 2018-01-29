/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Global activity monitor.
 */
public class ActivityMonitor {

    // Brief delay, to give the app a chance to perform screen rotation cleanup
    private static final long BACKGROUND_DELAY_MS = 200;

    private static ActivityMonitor singleton;

    private final Handler handler;
    private final List<Listener> listeners = new ArrayList<>();
    private final Runnable backgroundRunnable;

    private int startedActivities = 0;
    private long backgroundTime;
    private boolean isForeground;
    private WeakReference<Activity> resumedActivityReference;

    protected Application.ActivityLifecycleCallbacks activityLifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(Activity activity, Bundle bundle) {
            for (Listener listener : new ArrayList<>(listeners)) {
                if (listener != null) {
                    listener.onActivityCreated(activity, bundle);
                }
            }
        }

        @Override
        public void onActivityStarted(Activity activity) {
            handler.removeCallbacks(backgroundRunnable);
            startedActivities++;
            if (!isForeground) {
                isForeground = true;
                long timeStamp = System.currentTimeMillis();
                for (Listener listener : new ArrayList<>(listeners)) {
                    listener.onForeground(timeStamp);
                }
            }

            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onActivityStarted(activity);
            }
        }

        @Override
        public void onActivityResumed(Activity activity) {
            resumedActivityReference = new WeakReference<>(activity);
            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onActivityResumed(activity);
            }
        }

        @Override
        public void onActivityPaused(Activity activity) {
            resumedActivityReference = null;
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

            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onActivityStopped(activity);
            }
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
            for (Listener listener : new ArrayList<>(listeners)) {
                if (listener != null) {
                    listener.onActivitySaveInstanceState(activity, bundle);
                }
            }
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            for (Listener listener : new ArrayList<>(listeners)) {
                if (listener != null) {
                    listener.onActivityDestroyed(activity);
                }
            }
        }
    };

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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
     * Registers for activity lifecycle callbacks.
     * @param context The application context.
     */
    @VisibleForTesting
    void registerListener(Context context) {
        ((Application) context.getApplicationContext()).registerActivityLifecycleCallbacks(activityLifecycleCallbacks);
    }

    /**
     * Unregisters for activity lifecycle callbacks.
     * @param context The application context.
     */
    @VisibleForTesting
    void unregisterListener(Context context) {
        ((Application) context.getApplicationContext()).unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks);
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
        singleton.registerListener(context);
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

    /**
     * Gets the current resumed activity.
     *
     * @return The resumed activity.
     */
    @Nullable
    public Activity getResumedActivity() {
        return resumedActivityReference == null ? null : resumedActivityReference.get();
    }

    /**
     * Listener class for activity updates.
     */
    public interface Listener extends Application.ActivityLifecycleCallbacks {

        /**
         * Called when the app is foregrounded.
         */
        void onForeground(long time);

        /**
         * Called when the app is backgrounded.
         */
        void onBackground(long time);
    }

    /**
     * A convenience class to extend when you only want to listen for a subset
     * of of activity events.
     */
    public static class SimpleListener implements Listener {

        @Override
        public void onForeground(long time) {}

        @Override
        public void onBackground(long time) {}

        @Override
        public void onActivityPaused(Activity activity) {}

        @Override
        public void onActivityCreated(Activity activity, Bundle bundle) {}

        @Override
        public void onActivityStarted(Activity activity) {}

        @Override
        public void onActivityResumed(Activity activity) {}

        @Override
        public void onActivityStopped(Activity activity) {}

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {}

        @Override
        public void onActivityDestroyed(Activity activity) {}
    }
}
