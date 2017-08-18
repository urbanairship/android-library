/* Copyright 2017 Urban Airship and Contributors */

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
        this.activityLifecycleCallbacks.onActivityStarted(activity);
        Robolectric.flushForegroundThreadScheduler();
    }

    /**
     * Starts an activity.
     */
    public void startActivity(Activity activity) {
        this.activityLifecycleCallbacks.onActivityStarted(activity);
        Robolectric.flushForegroundThreadScheduler();
    }

    public void resumeActivity(Activity activity) {
        this.activityLifecycleCallbacks.onActivityResumed(activity);
        Robolectric.flushForegroundThreadScheduler();
    }

    /**
     * Stops an activity.
     */
    public void stopActivity() {
        Activity activity = new Activity();
        this.activityLifecycleCallbacks.onActivityStopped(activity);
        Robolectric.flushForegroundThreadScheduler();
    }
}
