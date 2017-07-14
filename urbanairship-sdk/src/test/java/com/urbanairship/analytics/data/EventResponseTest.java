/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics.data;

import com.urbanairship.BaseTestCase;
import com.urbanairship.analytics.data.EventResponse;
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
    Map<String, List<String>> responseHeaders;

    @Before
    public void setUp() {
        Response response = Mockito.mock(Response.class);
        responseHeaders = new HashMap<>();
        when(response.getResponseHeaders()).thenReturn(responseHeaders);

        eventResponse = new EventResponse(response);
    }

    /**
     * Test that the max total db size constrains the values between its max and min.
     */
    @Test
    public void testMaxTotalDbSize() {
        List<String> stringList = new ArrayList<>();

        // Test a value at the max
        stringList.add(0, String.valueOf(EventResponse.MAX_TOTAL_DB_SIZE_BYTES));
        responseHeaders.put("X-UA-Max-Total", stringList);
        assertEquals("Should constrain to the max",
                eventResponse.getMaxTotalSize(),
                EventResponse.MAX_TOTAL_DB_SIZE_BYTES);

        // Test a value above the max
        stringList.add(0, String.valueOf(EventResponse.MAX_TOTAL_DB_SIZE_BYTES / 1024 + 1));
        responseHeaders.put("X-UA-Max-Total", stringList);
        assertEquals("Should constrain to the max",
                eventResponse.getMaxTotalSize(),
                EventResponse.MAX_TOTAL_DB_SIZE_BYTES);

        // Test a value below the max
        stringList.add(0, String.valueOf(EventResponse.MAX_TOTAL_DB_SIZE_BYTES / 1024 - 1));
        responseHeaders.put("X-UA-Max-Total", stringList);
        assertEquals("Should allow values between the min and max",
                eventResponse.getMaxTotalSize(),
                EventResponse.MAX_TOTAL_DB_SIZE_BYTES - 1024);

        // Test a value at the min
        stringList.add(0, String.valueOf(EventResponse.MIN_TOTAL_DB_SIZE_BYTES / 1024));
        responseHeaders.put("X-UA-Max-Total", stringList);
        assertEquals("Should constrain to the min",
                eventResponse.getMaxTotalSize(),
                EventResponse.MIN_TOTAL_DB_SIZE_BYTES);

        // Test a value below the min
        stringList.add(0, String.valueOf(EventResponse.MIN_TOTAL_DB_SIZE_BYTES / 1024 - 1));
        responseHeaders.put("X-UA-Max-Total", stringList);
        assertEquals("Should constrain to the min",
                eventResponse.getMaxTotalSize(),
                EventResponse.MIN_TOTAL_DB_SIZE_BYTES);

        // Test a value above the min
        stringList.add(0, String.valueOf(EventResponse.MIN_TOTAL_DB_SIZE_BYTES / 1024 + 1));
        responseHeaders.put("X-UA-Max-Total", stringList);
        assertEquals("Should allow values between the min and max",
                eventResponse.getMaxTotalSize(),
                EventResponse.MIN_TOTAL_DB_SIZE_BYTES + 1024);
    }

    /**
     * Test that the max batch size constrains the values between its max and min.
     */
    @Test
    public void testMaxBatchSize() {
        List<String> stringList = new ArrayList<>();

        // Test a value at the max
        stringList.add(0, String.valueOf(EventResponse.MAX_BATCH_SIZE_BYTES /1024));
        responseHeaders.put("X-UA-Max-Batch", stringList);
        assertEquals("Should constrain to the max",
                eventResponse.getMaxBatchSize(),
                EventResponse.MAX_BATCH_SIZE_BYTES);

        // Test a value above the max
        stringList.add(0, String.valueOf(EventResponse.MAX_BATCH_SIZE_BYTES / 1024 + 1));
        responseHeaders.put("X-UA-Max-Batch", stringList);
        assertEquals("Should constrain to the max",
                eventResponse.getMaxBatchSize(),
                EventResponse.MAX_BATCH_SIZE_BYTES);

        // Test a value below the max
        stringList.add(0, String.valueOf(EventResponse.MAX_BATCH_SIZE_BYTES / 1024 - 1));
        responseHeaders.put("X-UA-Max-Batch", stringList);
        assertEquals("Should allow values between the min and max",
                eventResponse.getMaxBatchSize(),
                EventResponse.MAX_BATCH_SIZE_BYTES - 1024);

        // Test a value at the min
        stringList.add(0, String.valueOf(EventResponse.MIN_BATCH_SIZE_BYTES / 1024));
        responseHeaders.put("X-UA-Max-Batch", stringList);
        assertEquals("Should constrain to the min",
                eventResponse.getMaxBatchSize(),
                EventResponse.MIN_BATCH_SIZE_BYTES);

        // Test a value below the min
        stringList.add(0, String.valueOf(EventResponse.MIN_BATCH_SIZE_BYTES / 1024 - 1));
        responseHeaders.put("X-UA-Max-Batch", stringList);
        assertEquals("Should constrain to the min",
                eventResponse.getMaxBatchSize(),
                EventResponse.MIN_BATCH_SIZE_BYTES);

        // Test a value above the min
        stringList.add(0, String.valueOf(EventResponse.MIN_BATCH_SIZE_BYTES / 1024 + 1));
        responseHeaders.put("X-UA-Max-Batch", stringList);
        assertEquals("Should allow values between the min and max",
                eventResponse.getMaxBatchSize(),
                EventResponse.MIN_BATCH_SIZE_BYTES + 1024);
    }

    /**
     * Test that the min batch interval time constrains the values between its max and min.
     */
    @Test
    public void testMinBatchInterval() {
        List<String> stringList = new ArrayList<>();

        // Test a value at the max
        stringList.add(0, String.valueOf(EventResponse.MAX_BATCH_INTERVAL_MS));
        responseHeaders.put("X-UA-Min-Batch-Interval", stringList);
        assertEquals("Should constrain to the max",
                eventResponse.getMinBatchInterval(),
                EventResponse.MAX_BATCH_INTERVAL_MS);

        // Test a value above the max
        stringList.add(0, String.valueOf(EventResponse.MAX_BATCH_INTERVAL_MS + 1));
        responseHeaders.put("X-UA-Min-Batch-Interval", stringList);
        assertEquals("Should constrain to the max",
                eventResponse.getMinBatchInterval(),
                EventResponse.MAX_BATCH_INTERVAL_MS);

        // Test a value below the max
        stringList.add(0, String.valueOf(EventResponse.MAX_BATCH_INTERVAL_MS - 1));
        responseHeaders.put("X-UA-Min-Batch-Interval", stringList);
        assertEquals("Should allow values between the min and max",
                eventResponse.getMinBatchInterval(),
                EventResponse.MAX_BATCH_INTERVAL_MS - 1);

        // Test a value at the min
        stringList.add(0, String.valueOf(EventResponse.MIN_BATCH_INTERVAL_MS));
        responseHeaders.put("X-UA-Min-Batch-Interval", stringList);
        assertEquals("Should constrain to the min",
                eventResponse.getMinBatchInterval(),
                EventResponse.MIN_BATCH_INTERVAL_MS);

        // Test a value below the min
        stringList.add(0, String.valueOf(EventResponse.MIN_BATCH_INTERVAL_MS - 1));
        responseHeaders.put("X-UA-Min-Batch-Interval", stringList);
        assertEquals("Should constrain to the min",
                eventResponse.getMinBatchInterval(),
                EventResponse.MIN_BATCH_INTERVAL_MS);

        // Test a value above the min
        stringList.add(0, String.valueOf(EventResponse.MIN_BATCH_INTERVAL_MS + 1));
        responseHeaders.put("X-UA-Min-Batch-Interval", stringList);
        assertEquals("Should allow values between the min and max",
                eventResponse.getMinBatchInterval(),
                EventResponse.MIN_BATCH_INTERVAL_MS + 1);
    }
}
