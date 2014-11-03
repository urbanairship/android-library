package com.urbanairship.analytics;

import com.urbanairship.RobolectricGradleTestRunner;
import com.urbanairship.TestApplication;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricGradleTestRunner.class)
public class AnalyticsPreferencesTest {

    private AnalyticsPreferences preferences;

    @Before
    public void setUp() {
        preferences = new AnalyticsPreferences(TestApplication.getApplication().preferenceDataStore);
    }

    /**
     * Test the max total db size preference
     */
    @Test
    public void testMaxTotalDbSize() {
        // Test a value between the min and max
        preferences.setMaxTotalDbSize(AnalyticsPreferences.MAX_TOTAL_DB_SIZE_BYTES - 1);
        assertEquals("Preference should allow values between the min and max",
                preferences.getMaxTotalDbSize(), AnalyticsPreferences.MAX_TOTAL_DB_SIZE_BYTES - 1);
    }

    /**
     * Test the max batch size preference
     */
    @Test
    public void testMaxBatchSize() {
        // Test a value between the min and max
        preferences.setMaxBatchSize(AnalyticsPreferences.MAX_BATCH_SIZE_BYTES - 1);
        assertEquals("Preference should allow values between the min and max",
                preferences.getMaxBatchSize(), AnalyticsPreferences.MAX_BATCH_SIZE_BYTES - 1);
    }

    /**
     * Test the max wait time preference
     */
    @Test
    public void testMaxWait() {
        // Test a value between the min and max
        preferences.setMaxWait(AnalyticsPreferences.MAX_WAIT_MS - 1);
        assertEquals("Preference should allow values between the min and max",
                preferences.getMaxWait(), AnalyticsPreferences.MAX_WAIT_MS - 1);
    }

    /**
     * Test that the min batch interval time preference clamps the values between
     * its max and min.
     */
    @Test
    public void testMinBatchInterval() {
        // Test a value between the min and max
        preferences.setMinBatchInterval(AnalyticsPreferences.MAX_BATCH_INTERVAL_MS - 1);
        assertEquals("Preference should allow values between the min and max",
                preferences.getMinBatchInterval(), AnalyticsPreferences.MAX_BATCH_INTERVAL_MS - 1);
    }


    @Test
    public void TestGetNextSendTime() {
        Assert.assertEquals("Last send time should default to 0", 0, preferences.getLastSendTime());

        preferences.setLastSendTime(100);
        Assert.assertEquals("Last send time is not saving properly", 100, preferences.getLastSendTime());
    }
}
