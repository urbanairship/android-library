/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.analytics;

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
        stringList.add(0, String.valueOf(AnalyticsPreferences.MAX_TOTAL_DB_SIZE_BYTES));
        responseHeaders.put("X-UA-Max-Total", stringList);
        assertEquals("Should constrain to the max",
                eventResponse.getMaxTotalSize(),
                AnalyticsPreferences.MAX_TOTAL_DB_SIZE_BYTES);

        // Test a value above the max
        stringList.add(0, String.valueOf(AnalyticsPreferences.MAX_TOTAL_DB_SIZE_BYTES + 1));
        responseHeaders.put("X-UA-Max-Total", stringList);
        assertEquals("Should constrain to the max",
                eventResponse.getMaxTotalSize(),
                AnalyticsPreferences.MAX_TOTAL_DB_SIZE_BYTES);

        // Test a value below the max
        stringList.add(0, String.valueOf(AnalyticsPreferences.MAX_TOTAL_DB_SIZE_BYTES - 1));
        responseHeaders.put("X-UA-Max-Total", stringList);
        assertEquals("Should allow values between the min and max",
                eventResponse.getMaxTotalSize(),
                AnalyticsPreferences.MAX_TOTAL_DB_SIZE_BYTES - 1);

        // Test a value at the min
        stringList.add(0, String.valueOf(AnalyticsPreferences.MIN_TOTAL_DB_SIZE_BYTES));
        responseHeaders.put("X-UA-Max-Total", stringList);
        assertEquals("Should constrain to the min",
                eventResponse.getMaxTotalSize(),
                AnalyticsPreferences.MIN_TOTAL_DB_SIZE_BYTES);

        // Test a value below the min
        stringList.add(0, String.valueOf(AnalyticsPreferences.MIN_TOTAL_DB_SIZE_BYTES - 1));
        responseHeaders.put("X-UA-Max-Total", stringList);
        assertEquals("Should constrain to the min",
                eventResponse.getMaxTotalSize(),
                AnalyticsPreferences.MIN_TOTAL_DB_SIZE_BYTES);

        // Test a value above the min
        stringList.add(0, String.valueOf(AnalyticsPreferences.MIN_TOTAL_DB_SIZE_BYTES + 1));
        responseHeaders.put("X-UA-Max-Total", stringList);
        assertEquals("Should allow values between the min and max",
                eventResponse.getMaxTotalSize(),
                AnalyticsPreferences.MIN_TOTAL_DB_SIZE_BYTES + 1);
    }

    /**
     * Test that the max batch size constrains the values between its max and min.
     */
    @Test
    public void testMaxBatchSize() {
        List<String> stringList = new ArrayList<>();

        // Test a value at the max
        stringList.add(0, String.valueOf(AnalyticsPreferences.MAX_BATCH_SIZE_BYTES));
        responseHeaders.put("X-UA-Max-Batch", stringList);
        assertEquals("Should constrain to the max",
                eventResponse.getMaxBatchSize(),
                AnalyticsPreferences.MAX_BATCH_SIZE_BYTES);

        // Test a value above the max
        stringList.add(0, String.valueOf(AnalyticsPreferences.MAX_BATCH_SIZE_BYTES + 1));
        responseHeaders.put("X-UA-Max-Batch", stringList);
        assertEquals("Should constrain to the max",
                eventResponse.getMaxBatchSize(),
                AnalyticsPreferences.MAX_BATCH_SIZE_BYTES);

        // Test a value below the max
        stringList.add(0, String.valueOf(AnalyticsPreferences.MAX_BATCH_SIZE_BYTES - 1));
        responseHeaders.put("X-UA-Max-Batch", stringList);
        assertEquals("Should allow values between the min and max",
                eventResponse.getMaxBatchSize(),
                AnalyticsPreferences.MAX_BATCH_SIZE_BYTES - 1);

        // Test a value at the min
        stringList.add(0, String.valueOf(AnalyticsPreferences.MIN_BATCH_SIZE_BYTES));
        responseHeaders.put("X-UA-Max-Batch", stringList);
        assertEquals("Should constrain to the min",
                eventResponse.getMaxBatchSize(),
                AnalyticsPreferences.MIN_BATCH_SIZE_BYTES);

        // Test a value below the min
        stringList.add(0, String.valueOf(AnalyticsPreferences.MIN_BATCH_SIZE_BYTES - 1));
        responseHeaders.put("X-UA-Max-Batch", stringList);
        assertEquals("Should constrain to the min",
                eventResponse.getMaxBatchSize(),
                AnalyticsPreferences.MIN_BATCH_SIZE_BYTES);

        // Test a value above the min
        stringList.add(0, String.valueOf(AnalyticsPreferences.MIN_BATCH_SIZE_BYTES + 1));
        responseHeaders.put("X-UA-Max-Batch", stringList);
        assertEquals("Should allow values between the min and max",
                eventResponse.getMaxBatchSize(),
                AnalyticsPreferences.MIN_BATCH_SIZE_BYTES + 1);
    }

    /**
     * Test that the max wait time constrains the values between its max and min.
     */
    @Test
    public void testMaxWait() {
        List<String> stringList = new ArrayList<>();

        // Test a value at the max
        stringList.add(0, String.valueOf(AnalyticsPreferences.MAX_WAIT_MS));
        responseHeaders.put("X-UA-Max-Wait", stringList);
        assertEquals("Should constrain to the max",
                eventResponse.getMaxWait(),
                AnalyticsPreferences.MAX_WAIT_MS);

        // Test a value above the max
        stringList.add(0, String.valueOf(AnalyticsPreferences.MAX_WAIT_MS + 1));
        responseHeaders.put("X-UA-Max-Wait", stringList);
        assertEquals("Should constrain to the max",
                eventResponse.getMaxWait(),
                AnalyticsPreferences.MAX_WAIT_MS);

        // Test a value below the max
        stringList.add(0, String.valueOf(AnalyticsPreferences.MAX_WAIT_MS - 1));
        responseHeaders.put("X-UA-Max-Wait", stringList);
        assertEquals("Should allow values between the min and max",
                eventResponse.getMaxWait(),
                AnalyticsPreferences.MAX_WAIT_MS - 1);

        // Test a value at the min
        stringList.add(0, String.valueOf(AnalyticsPreferences.MIN_WAIT_MS));
        responseHeaders.put("X-UA-Max-Wait", stringList);
        assertEquals("Should constrain to the min",
                eventResponse.getMaxWait(),
                AnalyticsPreferences.MIN_WAIT_MS);

        // Test a value below the min
        stringList.add(0, String.valueOf(AnalyticsPreferences.MIN_WAIT_MS - 1));
        responseHeaders.put("X-UA-Max-Wait", stringList);
        assertEquals("Should constrain to the min",
                eventResponse.getMaxWait(),
                AnalyticsPreferences.MIN_WAIT_MS);

        // Test a value above the min
        stringList.add(0, String.valueOf(AnalyticsPreferences.MIN_WAIT_MS + 1));
        responseHeaders.put("X-UA-Max-Wait", stringList);
        assertEquals("Should allow values between the min and max",
                eventResponse.getMaxWait(),
                AnalyticsPreferences.MIN_WAIT_MS + 1);
    }

    /**
     * Test that the min batch interval time constrains the values between its max and min.
     */
    @Test
    public void testMinBatchInterval() {
        List<String> stringList = new ArrayList<>();

        // Test a value at the max
        stringList.add(0, String.valueOf(AnalyticsPreferences.MAX_BATCH_INTERVAL_MS));
        responseHeaders.put("X-UA-Min-Batch-Interval", stringList);
        assertEquals("Should constrain to the max",
                eventResponse.getMinBatchInterval(),
                AnalyticsPreferences.MAX_BATCH_INTERVAL_MS);

        // Test a value above the max
        stringList.add(0, String.valueOf(AnalyticsPreferences.MAX_BATCH_INTERVAL_MS + 1));
        responseHeaders.put("X-UA-Min-Batch-Interval", stringList);
        assertEquals("Should constrain to the max",
                eventResponse.getMinBatchInterval(),
                AnalyticsPreferences.MAX_BATCH_INTERVAL_MS);

        // Test a value below the max
        stringList.add(0, String.valueOf(AnalyticsPreferences.MAX_BATCH_INTERVAL_MS - 1));
        responseHeaders.put("X-UA-Min-Batch-Interval", stringList);
        assertEquals("Should allow values between the min and max",
                eventResponse.getMinBatchInterval(),
                AnalyticsPreferences.MAX_BATCH_INTERVAL_MS - 1);

        // Test a value at the min
        stringList.add(0, String.valueOf(AnalyticsPreferences.MIN_BATCH_INTERVAL_MS));
        responseHeaders.put("X-UA-Min-Batch-Interval", stringList);
        assertEquals("Should constrain to the min",
                eventResponse.getMinBatchInterval(),
                AnalyticsPreferences.MIN_BATCH_INTERVAL_MS);

        // Test a value below the min
        stringList.add(0, String.valueOf(AnalyticsPreferences.MIN_BATCH_INTERVAL_MS - 1));
        responseHeaders.put("X-UA-Min-Batch-Interval", stringList);
        assertEquals("Should constrain to the min",
                eventResponse.getMinBatchInterval(),
                AnalyticsPreferences.MIN_BATCH_INTERVAL_MS);

        // Test a value above the min
        stringList.add(0, String.valueOf(AnalyticsPreferences.MIN_BATCH_INTERVAL_MS + 1));
        responseHeaders.put("X-UA-Min-Batch-Interval", stringList);
        assertEquals("Should allow values between the min and max",
                eventResponse.getMinBatchInterval(),
                AnalyticsPreferences.MIN_BATCH_INTERVAL_MS + 1);
    }
}
