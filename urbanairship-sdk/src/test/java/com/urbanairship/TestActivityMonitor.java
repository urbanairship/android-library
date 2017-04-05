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
        TestApplication.getApplication().registerActivityLifecycleCallbacks(this);
    }

    /**
     * Unregisters the test activity monitor with the application.
     */
    public void unregister() {
        TestApplication.getApplication().unregisterActivityLifecycleCallbacks(this);
    }

    /**
     * Starts an activity.
     */
    public void startActivity() {
        Activity activity = new Activity();
        this.onActivityStarted(activity);
        Robolectric.flushForegroundThreadScheduler();
    }

    /**
     * Stops an activity.
     */
    public void stopActivity() {
        Activity activity = new Activity();
        this.onActivityStopped(activity);
        Robolectric.flushForegroundThreadScheduler();
    }
}
