package com.urbanairship.analytics;

import android.app.Activity;

import com.urbanairship.BaseTestCase;
import com.urbanairship.analytics.ActivityMonitor.Source;

import org.junit.Before;
import org.junit.Test;

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

    private int currentSdkVersion = 4;
    private boolean analyticsEnabled = true;

    @Before
    public void setUp() {
        isForeground = false;
        foregroundTimeMS = 0;
        backgroundTimeMS = 0;
        // The minSdkVersion is just passed to activity states.  It only changes the
        // logging behavior inside activity state when it detects an invalid use of
        // activityStarted/activityStopped.  We currently do not test logging, and if
        // we did we would test it in the activityState class.
        activityMonitor = new ActivityMonitor(4, currentSdkVersion, analyticsEnabled);

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
    public void testAddActivityManualInstrumentation() throws Exception {
        // Pre-ICS - Manual instrumentation calls should cause the app to go into foreground
        Activity activity = new Activity();
        activityMonitor.activityStarted(activity, Source.MANUAL_INSTRUMENTATION, 100);
        assertEquals(foregroundTimeMS, 100);
        assertTrue(isForeground);

        // Set up activity monitor in a ICS environment
        currentSdkVersion = 14;
        setUp();

        // ICS+ - Manual instrumentation calls are ignored for foreground state changes
        activityMonitor.activityStarted(activity, Source.MANUAL_INSTRUMENTATION, 200);
        assertFalse(isForeground);
    }

    /**
     * This test verifies adding an activity calls the onForeground delegate call
     *
     * @throws Exception
     */
    @Test
    public void testAddActivityAutoInstrumentation() throws Exception {
        // Pre-ICS - Automatic instrumentation calls are ignored for foreground state changes
        Activity activity = new Activity();
        activityMonitor.activityStarted(activity, Source.AUTO_INSTRUMENTATION, 100);
        assertFalse(isForeground);


        // Set up activity monitor in a ICS environment
        currentSdkVersion = 14;
        setUp();

        // ICS+ - Automatic instrumentation calls should cause the app to go into foreground
        activityMonitor.activityStarted(activity, Source.AUTO_INSTRUMENTATION, 200);
        assertTrue(isForeground);
        assertEquals(foregroundTimeMS, 200);
    }

    /**
     * This test verifies removing an activity calls the onBackground delegate call
     *
     * @throws Exception
     */
    @Test
    public void testRemoveActivityManualInstrumentation() throws Exception {
        // Pre-ICS - Manual instrumentation calls should cause the app to go into background
        Activity activity = new Activity();
        activityMonitor.activityStarted(activity, Source.MANUAL_INSTRUMENTATION, 100);

        activityMonitor.activityStopped(activity, Source.MANUAL_INSTRUMENTATION, 200);
        activityMonitor.updateForegroundState();
        assertFalse(isForeground);
        assertEquals(backgroundTimeMS, 200);


        // Set up activity monitor in a ICS environment
        currentSdkVersion = 14;
        setUp();

        // ICS+ - Manual instrumentation calls are ignored for background state changes
        activityMonitor.activityStarted(activity, Source.AUTO_INSTRUMENTATION, 100);

        activityMonitor.activityStopped(activity, Source.MANUAL_INSTRUMENTATION, 200);
        activityMonitor.updateForegroundState();
        assertTrue(isForeground);
        assertEquals(foregroundTimeMS, 100);

    }

    /**
     * This test verifies removing an activity calls the onBackground delegate call
     *
     * @throws Exception
     */
    @Test
    public void testRemoveActivityAutoInstrumentation() throws Exception {
        // Pre-ICS - Automatic instrumentation calls are ignored for foreground state changes
        Activity activity = new Activity();
        activityMonitor.activityStarted(activity, Source.MANUAL_INSTRUMENTATION, 100);

        activityMonitor.activityStopped(activity, Source.AUTO_INSTRUMENTATION, 200);
        activityMonitor.updateForegroundState();
        assertTrue(isForeground);
        assertEquals(foregroundTimeMS, 100);


        // Set up activity monitor in a ICS environment
        currentSdkVersion = 14;
        setUp();

        // ICS+ - Automatic instrumentation calls should cause the app to go into background
        activityMonitor.activityStopped(activity, Source.AUTO_INSTRUMENTATION, 100);

        activityMonitor.activityStopped(activity, Source.AUTO_INSTRUMENTATION, 200);
        activityMonitor.updateForegroundState();
        assertFalse(isForeground);
    }

    /**
     * This test verifies removing an activity after multiple adds calls the onBackground delegate call
     */
    @Test
    public void testRemoveAfterAddMultipleActivity() {
        Activity activity = new Activity();
        activityMonitor.activityStarted(activity, Source.MANUAL_INSTRUMENTATION, 100);
        activityMonitor.activityStarted(activity, Source.MANUAL_INSTRUMENTATION, 200);

        activityMonitor.activityStopped(activity, Source.MANUAL_INSTRUMENTATION, 300);
        activityMonitor.updateForegroundState();
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

        activityMonitor.activityStarted(activityOne, Source.MANUAL_INSTRUMENTATION, 100);
        activityMonitor.activityStarted(activityTwo, Source.MANUAL_INSTRUMENTATION, 200);
        assertTrue(isForeground);
        assertEquals(foregroundTimeMS, 100);

        activityMonitor.activityStopped(activityOne, Source.MANUAL_INSTRUMENTATION, 300);
        activityMonitor.updateForegroundState();
        assertTrue(isForeground);

        activityMonitor.activityStopped(activityTwo, Source.MANUAL_INSTRUMENTATION, 400);
        activityMonitor.updateForegroundState();
        assertFalse(isForeground);
        assertEquals(backgroundTimeMS, 400);
    }
}