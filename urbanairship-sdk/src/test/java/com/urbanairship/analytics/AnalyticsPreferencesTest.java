/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.analytics;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class AnalyticsPreferencesTest extends BaseTestCase {

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

    /**
     * Test associated identifiers.
     */
    @Test
    public void testAssociatedIdentifiers() {
        Map<String, String> ids = new HashMap<>();
        ids.put("custom key", "custom value");
        ids.put("fun key", "fun value");
        AssociatedIdentifiers theIds = new AssociatedIdentifiers(ids);
        preferences.setIdentifiers(theIds);

        AssociatedIdentifiers someIds = preferences.getIdentifiers();
        assertEquals("custom value", someIds.getIds().get("custom key"));
        assertEquals("fun value", someIds.getIds().get("fun key"));
        assertEquals(someIds.getIds().size(), 2);
    }

    /**
     * Test empty associated identifiers.
     */
    @Test
    public void testEmptyAssociatedIdentifiers() {
        Map<String, String> ids = new HashMap<>();
        AssociatedIdentifiers theIds = new AssociatedIdentifiers(ids);
        preferences.setIdentifiers(theIds);

        AssociatedIdentifiers emptyIds = preferences.getIdentifiers();
        assertEquals(emptyIds.getIds().size(), 0);
    }
}
