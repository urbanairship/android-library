/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ApplicationMetricsTest extends BaseTestCase {

    private ApplicationMetrics metrics;
    private TestActivityMonitor activityMonitor;

    @Before
    public void setup() {
        PreferenceDataStore preferenceDataStore = new PreferenceDataStore(TestApplication.getApplication(), mock(UrbanAirshipResolver.class));
        activityMonitor = new TestActivityMonitor();
        activityMonitor.register();

        metrics = new ApplicationMetrics(TestApplication.getApplication(), preferenceDataStore, activityMonitor);
        metrics.init();
    }

    @After
    public void takeDown() {
        activityMonitor.unregister();
    }

    /**
     * Test last open returns -1 when no opens
     * have been tracked.
     */
    @Test
    public void testGetLastOpenNotSet() {
        assertEquals("Last open time should default to -1", -1, metrics.getLastOpenTimeMillis());
    }

    /**
     * Test when a foreground broadcast is sent the
     * last open time is updated.
     */
    @Test
    public void testLastOpenTimeTracking() {
        // Foreground the app to update last open time
        activityMonitor.startActivity();

        // Make sure the time is greater than 0
        assertTrue("Last open time should of updated", metrics.getLastOpenTimeMillis() > 0);
    }
}
