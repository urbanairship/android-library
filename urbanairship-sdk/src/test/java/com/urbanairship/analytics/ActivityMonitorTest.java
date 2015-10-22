package com.urbanairship.analytics;

import android.app.Activity;

import com.urbanairship.BaseTestCase;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.Robolectric;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * This class tests the monitoring the activities going into the foreground
 *
 * @author Urban Airship
 */
public class ActivityMonitorTest extends BaseTestCase {

    private ActivityMonitor activityMonitor;
    private boolean isForeground;
    private long foregroundTimeMS;
    private long backgroundTimeMS;


    @Before
    public void setUp() {
        isForeground = false;
        foregroundTimeMS = 0;
        backgroundTimeMS = 0;
        activityMonitor = new ActivityMonitor();

        activityMonitor.setListener(new ActivityMonitor.Listener() {

            @Override
            public void onForeground(long timeMS) {
                isForeground = true;
                foregroundTimeMS = timeMS;
            }

            @Override
            public void onBackground(long timeMS) {
                isForeground = false;
                backgroundTimeMS = timeMS;
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
        activityMonitor.activityStarted(activity, 100);
        activityMonitor.activityStarted(activity, 200);
        activityMonitor.activityStarted(activity, 300);

        Robolectric.flushForegroundThreadScheduler();

        // Should only trigger a foreground on the first start
        assertEquals(foregroundTimeMS, 100);
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
        activityMonitor.activityStarted(activity, 100);
        activityMonitor.activityStopped(activity, 200);

        Robolectric.flushForegroundThreadScheduler();

        assertFalse(isForeground);
        assertEquals(backgroundTimeMS, 200);
    }

    /**
     * This test verifies removing an activity after multiple adds calls the onBackground delegate call
     */
    @Test
    public void testRemoveAfterAddMultipleActivity() {
        Activity activity = new Activity();
        activityMonitor.activityStarted(activity, 100);
        activityMonitor.activityStarted(activity, 200);

        activityMonitor.activityStopped(activity, 300);

        Robolectric.flushForegroundThreadScheduler();

        assertFalse(isForeground);
        assertEquals(backgroundTimeMS, 300);
    }

    /**
     * This test verifies the multiple activities behavior calls the expected delegate calls
     */
    @Test
    public void testMultipleActivities() {
        Activity activityOne = new Activity();
        Activity activityTwo = new Activity();

        activityMonitor.activityStarted(activityOne, 100);
        activityMonitor.activityStarted(activityTwo, 200);

        Robolectric.flushForegroundThreadScheduler();

        assertTrue(isForeground);
        assertEquals(foregroundTimeMS, 100);

        activityMonitor.activityStopped(activityOne, 300);

        Robolectric.flushForegroundThreadScheduler();

        assertTrue(isForeground);

        activityMonitor.activityStopped(activityTwo, 400);
        Robolectric.flushForegroundThreadScheduler();

        assertFalse(isForeground);
        assertEquals(backgroundTimeMS, 400);
    }
}