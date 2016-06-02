/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import com.urbanairship.analytics.Analytics;

import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ApplicationMetricsTest extends BaseTestCase {

    private ApplicationMetrics metrics;

    @Before
    public void setup() {
        PreferenceDataStore preferenceDataStore = new PreferenceDataStore(TestApplication.getApplication(), mock(UrbanAirshipResolver.class));
        metrics = new ApplicationMetrics(TestApplication.getApplication(), preferenceDataStore);
        metrics.init();
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
        // Send the foreground broadcast to update last open time
        LocalBroadcastManager.getInstance(TestApplication.getApplication())
                             .sendBroadcast(new Intent(Analytics.ACTION_APP_FOREGROUND));

        // Make sure the time is greater than 0
        assertTrue("Last open time should of updated", metrics.getLastOpenTimeMillis() > 0);
    }
}
