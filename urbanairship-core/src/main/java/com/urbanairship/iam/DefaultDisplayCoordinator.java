package com.urbanairship.iam;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.IntRange;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;

import com.urbanairship.ActivityMonitor;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

/**
 * Default display coordinator. Only allows a single in-app message to be displayed at
 * a given time.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class DefaultDisplayCoordinator extends DisplayCoordinator {

    private final ActivityMonitor activityMonitor;
    private InAppMessage currentMessage = null;
    private boolean isLocked = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isInitialized = false;
    private WeakReference<Activity> currentActivity;
    private long displayInterval = InAppMessageManager.DEFAULT_DISPLAY_INTERVAL_MS;

    private final Runnable postDisplayRunnable = new Runnable() {
        @Override
        public void run() {
            if (currentMessage == null) {
                isLocked = false;
                notifyDisplayReady();
            }
        }
    };

    /**
     * Default constructor.
     *
     * @param activityMonitor The activity monitor.
     */
    public DefaultDisplayCoordinator(ActivityMonitor activityMonitor) {
        this.activityMonitor = activityMonitor;
    }

    /**
     * Sets the in-app message display interval. Defaults to {@link InAppMessageManager#DEFAULT_DISPLAY_INTERVAL_MS}.
     *
     * @param time The display interval.
     * @param timeUnit The time unit.
     */
    void setDisplayInterval(@IntRange(from = 0) long time, @NonNull TimeUnit timeUnit) {
        this.displayInterval = timeUnit.toMillis(time);
    }

    /**
     * Gets the display interval in milliseconds.
     *
     * @return The display interval in milliseconds.
     */
    long getDisplayInterval() {
        return this.displayInterval;
    }

    @MainThread
    private void init() {
        // Add the activity listener
        activityMonitor.addListener(new ActivityMonitor.SimpleListener() {
            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                // If the activity is not changing configuration, then it's either being
                // dismissed or another activity is starting on top of it.
                if (currentMessage != null && getCurrentActivity() == activity && !activity.isChangingConfigurations()) {
                    currentMessage = null;
                    currentActivity = null;
                    mainHandler.postDelayed(postDisplayRunnable, displayInterval);
                }
            }
        });

        isInitialized = true;
    }

    @MainThread
    @Override
    public boolean isReady(@NonNull InAppMessage message, boolean isRedisplay) {
        if (!isInitialized) {
            init();
        }

        if (currentMessage != null) {
            return false;
        }

        return isRedisplay || !isLocked;
    }

    @MainThread
    @Override
    public void onDisplayStarted(@NonNull Activity activity, @NonNull InAppMessage message) {
        setCurrentMessage(activity, message);
    }

    @MainThread
    @Override
    public boolean onAllowDisplay(@NonNull Activity activity, @NonNull InAppMessage message) {
        if (currentMessage == null || message == currentMessage) {
            setCurrentMessage(activity, message);
            return true;
        }

        return false;
    }

    @MainThread
    @Override
    public void onDisplayFinished(@NonNull InAppMessage message) {
        if (currentMessage == message) {
            currentMessage = null;
            currentActivity = null;
            mainHandler.postDelayed(postDisplayRunnable, displayInterval);
        }
    }

    /**
     * Get the current in-app message activity.
     *
     * @return The current activity.
     */
    @MainThread
    @Nullable
    private Activity getCurrentActivity() {
        if (currentActivity != null) {
            return currentActivity.get();
        }

        return null;
    }

    @MainThread
    private void setCurrentMessage(Activity activity, InAppMessage message) {
        currentMessage = message;
        currentActivity = new WeakReference<>(activity);
        isLocked = true;
        mainHandler.removeCallbacks(postDisplayRunnable);
    }

}
