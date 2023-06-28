/* Copyright Airship and Contributors */

package com.urbanairship.analytics.data;

import com.urbanairship.BaseTestCase;
import com.urbanairship.http.Response;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class EventResponseTest extends BaseTestCase {

    private EventResponse eventResponse;
    Map<String, String> responseHeaders = new HashMap<>();

    @Before
    public void setUp() {
        eventResponse = new EventResponse(responseHeaders);
    }

    /**
     * Test that the max total db size constrains the values between its max and min.
     */
    @Test
    public void testMaxTotalDbSize() {

        // Test a value at the max
        responseHeaders.put("X-UA-Max-Total", String.valueOf(EventResponse.MAX_TOTAL_DB_SIZE_BYTES));
        assertEquals("Should constrain to the max",
                eventResponse.getMaxTotalSize(),
                EventResponse.MAX_TOTAL_DB_SIZE_BYTES);

        // Test a value above the max
        responseHeaders.put("X-UA-Max-Total", String.valueOf(EventResponse.MAX_TOTAL_DB_SIZE_BYTES / 1024 + 1));
        assertEquals("Should constrain to the max",
                eventResponse.getMaxTotalSize(),
                EventResponse.MAX_TOTAL_DB_SIZE_BYTES);

        // Test a value below the max
        responseHeaders.put("X-UA-Max-Total", String.valueOf(EventResponse.MAX_TOTAL_DB_SIZE_BYTES / 1024 - 1));
        assertEquals("Should allow values between the min and max",
                eventResponse.getMaxTotalSize(),
                EventResponse.MAX_TOTAL_DB_SIZE_BYTES - 1024);

        // Test a value at the min
        responseHeaders.put("X-UA-Max-Total", String.valueOf(EventResponse.MIN_TOTAL_DB_SIZE_BYTES / 1024));
        assertEquals("Should constrain to the min",
                eventResponse.getMaxTotalSize(),
                EventResponse.MIN_TOTAL_DB_SIZE_BYTES);

        // Test a value below the min
        responseHeaders.put("X-UA-Max-Total", String.valueOf(EventResponse.MIN_TOTAL_DB_SIZE_BYTES / 1024 - 1));
        assertEquals("Should constrain to the min",
                eventResponse.getMaxTotalSize(),
                EventResponse.MIN_TOTAL_DB_SIZE_BYTES);

        // Test a value above the min
        responseHeaders.put("X-UA-Max-Total", String.valueOf(EventResponse.MIN_TOTAL_DB_SIZE_BYTES / 1024 + 1));
        assertEquals("Should allow values between the min and max",
                eventResponse.getMaxTotalSize(),
                EventResponse.MIN_TOTAL_DB_SIZE_BYTES + 1024);
    }

    /**
     * Test that the max batch size constrains the values between its max and min.
     */
    @Test
    public void testMaxBatchSize() {
        // Test a value at the max
        responseHeaders.put("X-UA-Max-Batch", String.valueOf(EventResponse.MAX_BATCH_SIZE_BYTES / 1024));
        assertEquals("Should constrain to the max",
                eventResponse.getMaxBatchSize(),
                EventResponse.MAX_BATCH_SIZE_BYTES);

        // Test a value above the max
        responseHeaders.put("X-UA-Max-Batch", String.valueOf(EventResponse.MAX_BATCH_SIZE_BYTES / 1024 + 1));
        assertEquals("Should constrain to the max",
                eventResponse.getMaxBatchSize(),
                EventResponse.MAX_BATCH_SIZE_BYTES);

        // Test a value below the max
        responseHeaders.put("X-UA-Max-Batch", String.valueOf(EventResponse.MAX_BATCH_SIZE_BYTES / 1024 - 1));
        assertEquals("Should allow values between the min and max",
                eventResponse.getMaxBatchSize(),
                EventResponse.MAX_BATCH_SIZE_BYTES - 1024);

        // Test a value at the min
        responseHeaders.put("X-UA-Max-Batch", String.valueOf(EventResponse.MIN_BATCH_SIZE_BYTES / 1024));
        assertEquals("Should constrain to the min",
                eventResponse.getMaxBatchSize(),
                EventResponse.MIN_BATCH_SIZE_BYTES);

        // Test a value below the min
        responseHeaders.put("X-UA-Max-Batch", String.valueOf(EventResponse.MIN_BATCH_SIZE_BYTES / 1024 - 1));
        assertEquals("Should constrain to the min",
                eventResponse.getMaxBatchSize(),
                EventResponse.MIN_BATCH_SIZE_BYTES);

        // Test a value above the min
        responseHeaders.put("X-UA-Max-Batch", String.valueOf(EventResponse.MIN_BATCH_SIZE_BYTES / 1024 + 1));
        assertEquals("Should allow values between the min and max",
                eventResponse.getMaxBatchSize(),
                EventResponse.MIN_BATCH_SIZE_BYTES + 1024);
    }

    /**
     * Test that the min batch interval time constrains the values between its max and min.
     */
    @Test
    public void testMinBatchInterval() {
        // Test a value at the max
        responseHeaders.put("X-UA-Min-Batch-Interval", String.valueOf(EventResponse.MAX_BATCH_INTERVAL_MS));
        assertEquals("Should constrain to the max",
                eventResponse.getMinBatchInterval(),
                EventResponse.MAX_BATCH_INTERVAL_MS);

        // Test a value above the max
        responseHeaders.put("X-UA-Min-Batch-Interval", String.valueOf(EventResponse.MAX_BATCH_INTERVAL_MS + 1));
        assertEquals("Should constrain to the max",
                eventResponse.getMinBatchInterval(),
                EventResponse.MAX_BATCH_INTERVAL_MS);

        // Test a value below the max
        responseHeaders.put("X-UA-Min-Batch-Interval", String.valueOf(EventResponse.MAX_BATCH_INTERVAL_MS - 1));
        assertEquals("Should allow values between the min and max",
                eventResponse.getMinBatchInterval(),
                EventResponse.MAX_BATCH_INTERVAL_MS - 1);

        // Test a value at the min
        responseHeaders.put("X-UA-Min-Batch-Interval", String.valueOf(EventResponse.MIN_BATCH_INTERVAL_MS));
        assertEquals("Should constrain to the min",
                eventResponse.getMinBatchInterval(),
                EventResponse.MIN_BATCH_INTERVAL_MS);

        // Test a value below the min
        responseHeaders.put("X-UA-Min-Batch-Interval", String.valueOf(EventResponse.MIN_BATCH_INTERVAL_MS - 1));
        assertEquals("Should constrain to the min",
                eventResponse.getMinBatchInterval(),
                EventResponse.MIN_BATCH_INTERVAL_MS);

        // Test a value above the min
        responseHeaders.put("X-UA-Min-Batch-Interval", String.valueOf(EventResponse.MIN_BATCH_INTERVAL_MS + 1));
        assertEquals("Should allow values between the min and max",
                eventResponse.getMinBatchInterval(),
                EventResponse.MIN_BATCH_INTERVAL_MS + 1);
    }

}
