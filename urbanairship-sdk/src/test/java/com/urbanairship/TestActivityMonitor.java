/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship;

import android.app.Activity;

import org.robolectric.Robolectric;

/**
 * Test Activity Monitor.
 */
public class TestActivityMonitor extends ActivityMonitor {

    /**
     * Registers the test activity monitor with the application.
     */
    public void register() {
        registerListener(TestApplication.getApplication());
    }

    /**
     * Unregisters the test activity monitor with the application.
     */
    public void unregister() {
        unregisterListener(TestApplication.getApplication());
    }


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

    public void startActivity(Activity activity) {
        this.activityLifecycleCallbacks.onActivityStarted(activity);
        Robolectric.flushForegroundThreadScheduler();
    }

    public void resumeActivity(Activity activity) {
        this.activityLifecycleCallbacks.onActivityResumed(activity);
        Robolectric.flushForegroundThreadScheduler();
    }

    public void pauseActivity(Activity activity) {
        this.activityLifecycleCallbacks.onActivityPaused(activity);
        Robolectric.flushForegroundThreadScheduler();
    }

    public void stopActivity(Activity activity) {
        this.activityLifecycleCallbacks.onActivityStopped(activity);
        Robolectric.flushForegroundThreadScheduler();
    }
}
