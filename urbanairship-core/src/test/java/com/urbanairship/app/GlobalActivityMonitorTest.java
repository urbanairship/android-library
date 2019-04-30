/* Copyright Airship and Contributors */

package com.urbanairship.app;

import android.app.Activity;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * This class tests the monitoring the activities going into the foreground
 */
public class GlobalActivityMonitorTest extends BaseTestCase {

    private GlobalActivityMonitor activityMonitor;
    private boolean isForeground;

    @Before
    public void setUp() {
        isForeground = false;
        activityMonitor = new GlobalActivityMonitor();

        activityMonitor.registerListener(TestApplication.getApplication());
        activityMonitor.addApplicationListener(new SimpleApplicationListener() {
            @Override
            public void onForeground(long timeMS) {
                isForeground = true;
            }

            @Override
            public void onBackground(long timeMS) {
                isForeground = false;
            }
        });
    }

    @After
    public void teardown() {
        activityMonitor.unregisterListener(TestApplication.getApplication());
    }

    /**
     * This test verifies adding an activity calls the onForeground delegate call
     *
     * @throws Exception
     */
    @Test
    public void testActivityStarted() throws Exception {
        Robolectric.buildActivity(Activity.class).create().start();
        Robolectric.flushForegroundThreadScheduler();

        assertTrue(isForeground);
    }

    /**
     * This test verifies removing an activity calls the onBackground delegate call
     *
     * @throws Exception
     */
    @Test
    public void testActivityStopped() throws Exception {
        Robolectric.buildActivity(Activity.class).create().start().stop();
        Robolectric.flushForegroundThreadScheduler();
        assertFalse(isForeground);
    }

    /**
     * This test verifies removing an activity after multiple adds doesn't call the onBackground delegate call
     */
    @Test
    public void testRemoveAfterAddMultipleActivity() {

        ActivityController activity1 = Robolectric.buildActivity(Activity.class).create().start();
        Robolectric.buildActivity(Activity.class).create().start();

        activity1.stop();

        Robolectric.flushForegroundThreadScheduler();

        assertTrue(isForeground);
    }

    /**
     * This test verifies the multiple activities behavior calls the expected delegate calls
     */
    @Test
    public void testMultipleActivities() {
        ActivityController activity1 = Robolectric.buildActivity(Activity.class).create().start();
        ActivityController activity2 = Robolectric.buildActivity(Activity.class).create().start();

        Robolectric.flushForegroundThreadScheduler();
        assertTrue(isForeground);

        activity1.stop();

        Robolectric.flushForegroundThreadScheduler();
        assertTrue(isForeground);

        activity2.stop();

        Robolectric.flushForegroundThreadScheduler();
        assertFalse(isForeground);
    }

}