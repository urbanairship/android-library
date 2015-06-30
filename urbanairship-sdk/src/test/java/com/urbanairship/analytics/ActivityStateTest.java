package com.urbanairship.analytics;

import com.urbanairship.BaseTestCase;
import com.urbanairship.analytics.ActivityMonitor.Source;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * This class tests the activity's active state
 *
 * @author Urban Airship
 */

public class ActivityStateTest extends BaseTestCase {

    private ActivityState icsState;
    private ActivityState preIcsState;

    @Before
    public void setUp() {
        this.icsState = new ActivityState("ics_activity", 4, 14, true);
        this.preIcsState = new ActivityState("pre_ics_activity", 4, 13, true);
    }

    /**
     * This test verifies the initial state of activities
     */
    @Test
    public void testIsForegroundNoActivity() {
        assertFalse(icsState.isForeground());
        assertFalse(preIcsState.isForeground());
    }

    /**
     * This test verifies the activity state is active after adding manually on Pre-ICS
     */
    @Test
    public void testStartedPreIcs() {
        preIcsState.setStarted(Source.MANUAL_INSTRUMENTATION, 100);
        assertTrue(preIcsState.isForeground());
        assertEquals(100, preIcsState.getLastModifiedTime());

        // Auto should have no effect on isActive
        preIcsState.setStopped(Source.AUTO_INSTRUMENTATION, 150);
        assertTrue(preIcsState.isForeground());
        assertEquals(150, preIcsState.getLastModifiedTime());
    }

    /**
     * This test verifies the activity state is not active adding manually on Pre-ICS
     */
    @Test
    public void testStoppedPreIcs() {
        preIcsState.setStopped(Source.MANUAL_INSTRUMENTATION, 100);
        assertFalse(preIcsState.isForeground());
        assertEquals(100, preIcsState.getLastModifiedTime());

        preIcsState.setStarted(Source.MANUAL_INSTRUMENTATION, 150);
        preIcsState.setStopped(Source.MANUAL_INSTRUMENTATION, 200);
        assertFalse(preIcsState.isForeground());
        assertEquals(200, preIcsState.getLastModifiedTime());

        // Auto should have no effect on isActive
        preIcsState.setStarted(Source.AUTO_INSTRUMENTATION, 250);
        assertFalse(preIcsState.isForeground());
        assertEquals(250, preIcsState.getLastModifiedTime());
    }

    /**
     * This test verifies the activity state is active after adding automatically on ICS
     */
    @Test
    public void testStartedIcs() {
        icsState.setStarted(Source.AUTO_INSTRUMENTATION, 100);
        assertTrue(icsState.isForeground());
        assertEquals(100, icsState.getLastModifiedTime());

        // Manual should have no effect on isActive
        icsState.setStopped(Source.MANUAL_INSTRUMENTATION, 200);
        assertTrue(icsState.isForeground());
        assertEquals(200, icsState.getLastModifiedTime());
    }

    /**
     * This test verifies the activity state is active after removing automatically on ICS
     */
    @Test
    public void testStoppedIcs() {
        icsState.setStopped(Source.AUTO_INSTRUMENTATION, 100);
        assertFalse(icsState.isForeground());
        assertEquals(100, icsState.getLastModifiedTime());

        icsState.setStarted(Source.AUTO_INSTRUMENTATION, 100);
        icsState.setStopped(Source.AUTO_INSTRUMENTATION, 200);
        assertFalse(icsState.isForeground());
        assertEquals(200, icsState.getLastModifiedTime());

        // Manual should have no effect on isActive
        icsState.setStarted(Source.MANUAL_INSTRUMENTATION, 100);
        assertFalse(icsState.isForeground());
        assertEquals(100, icsState.getLastModifiedTime());
    }
}