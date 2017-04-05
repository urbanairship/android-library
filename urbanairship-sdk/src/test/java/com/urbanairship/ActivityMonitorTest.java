/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.app.Activity;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.Robolectric;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * This class tests the monitoring the activities going into the foreground
 *
 */
public class ActivityMonitorTest extends BaseTestCase {

    private ActivityMonitor activityMonitor;
    private boolean isForeground;


    @Before
    public void setUp() {
        isForeground = false;
        activityMonitor = new ActivityMonitor();

        activityMonitor.addListener(new ActivityMonitor.Listener() {
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

    /**
     * This test verifies adding an activity calls the onForeground delegate call
     *
     * @throws Exception
     */
    @Test
    public void testActivityStarted() throws Exception {
        Activity activity = new Activity();
        activityMonitor.onActivityStarted(activity);

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
        Activity activity = new Activity();
        activityMonitor.onActivityStarted(activity);
        activityMonitor.onActivityStopped(activity);

        Robolectric.flushForegroundThreadScheduler();

        assertFalse(isForeground);
    }

    /**
     * This test verifies removing an activity after multiple adds doesn't call the onBackground delegate call
     */
    @Test
    public void testRemoveAfterAddMultipleActivity() {
        Activity activity = new Activity();
        activityMonitor.onActivityStarted(activity);
        activityMonitor.onActivityStarted(activity);

        activityMonitor.onActivityStopped(activity);

        Robolectric.flushForegroundThreadScheduler();

        assertTrue(isForeground);
    }

    /**
     * This test verifies the multiple activities behavior calls the expected delegate calls
     */
    @Test
    public void testMultipleActivities() {
        Activity activityOne = new Activity();
        Activity activityTwo = new Activity();

        activityMonitor.onActivityStarted(activityOne);
        activityMonitor.onActivityStarted(activityTwo);

        Robolectric.flushForegroundThreadScheduler();

        assertTrue(isForeground);

        activityMonitor.onActivityStopped(activityOne);

        Robolectric.flushForegroundThreadScheduler();

        assertTrue(isForeground);

        activityMonitor.onActivityStopped(activityTwo);
        Robolectric.flushForegroundThreadScheduler();

        assertFalse(isForeground);
    }
}