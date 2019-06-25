/* Copyright Airship and Contributors */

package com.urbanairship;

import android.app.Activity;
import androidx.annotation.NonNull;

import com.urbanairship.app.ActivityListener;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.ApplicationListener;
import com.urbanairship.app.ForwardingActivityListener;
import com.urbanairship.app.ForwardingApplicationListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Test Activity Monitor.
 */
public class TestActivityMonitor implements ActivityMonitor {

    private int startedActivities = 0;

    private ForwardingApplicationListener applicationListener = new ForwardingApplicationListener();
    private ForwardingActivityListener activityListener = new ForwardingActivityListener() {
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
            startedActivities++;
            if (!isAppForegrounded) {
                isAppForegrounded = true;
                applicationListener.onForeground(System.currentTimeMillis());
            }

            super.onActivityStarted(activity);
        }

        @Override
        public void onActivityStopped(Activity activity) {
            if (startedActivities > 0) {
                startedActivities--;
            }

            if (startedActivities == 0 && isAppForegrounded) {
                isAppForegrounded = false;
                applicationListener.onBackground(System.currentTimeMillis());
            }

            super.onActivityStopped(activity);
        }
    };

    private boolean isAppForegrounded;
    private List<Activity> resumedActivities = new ArrayList<>();

    /**
     * Starts an activity.
     */
    public void startActivity() {
        Activity activity = new Activity();
        startActivity(activity);
    }

    /**
     * Stops an activity.
     */
    public void stopActivity() {
        Activity activity = new Activity();
        stopActivity(activity);
    }

    public void foreground(long timeStamp) {
        applicationListener.onForeground(timeStamp);
    }

    public void foreground() {
        isAppForegrounded = true;
        applicationListener.onForeground(0);
    }

    public void background() {
        isAppForegrounded = false;
        applicationListener.onBackground(0);
    }

    public void startActivity(Activity activity) {
        activityListener.onActivityStarted(activity);
    }

    public void resumeActivity(Activity activity) {
        activityListener.onActivityResumed(activity);
    }

    public void pauseActivity(Activity activity) {
        activityListener.onActivityPaused(activity);
    }

    public void stopActivity(Activity activity) {
        activityListener.onActivityStopped(activity);
    }

    @Override
    public void addActivityListener(@NonNull ActivityListener listener) {
        activityListener.addListener(listener);
    }

    @Override
    public void removeActivityListener(@NonNull ActivityListener listener) {
        activityListener.removeListener(listener);
    }

    @Override
    public void addApplicationListener(@NonNull ApplicationListener listener) {
        applicationListener.addListener(listener);
    }

    @Override
    public void removeApplicationListener(@NonNull ApplicationListener listener) {
        applicationListener.removeListener(listener);
    }

    @Override
    public boolean isAppForegrounded() {
        return isAppForegrounded;
    }

    @NonNull
    @Override
    public List<Activity> getResumedActivities() {
        return resumedActivities;
    }

    @NonNull
    @Override
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
