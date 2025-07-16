/* Copyright Airship and Contributors */
package com.urbanairship.analytics.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class EventResponseTest {

    private val responseHeaders = mutableMapOf<String, String>()
    private val eventResponse: EventResponse = EventResponse(responseHeaders)

    /**
     * Test that the max total db size constrains the values between its max and min.
     */
    @Test
    public fun testMaxTotalDbSize() {
        // Test a value at the max
        responseHeaders["X-UA-Max-Total"] = EventResponse.MAX_TOTAL_DB_SIZE_BYTES.toString()
        assertEquals(
            "Should constrain to the max",
            eventResponse.maxTotalSize.toLong(),
            EventResponse.MAX_TOTAL_DB_SIZE_BYTES.toLong()
        )

        // Test a value above the max
        responseHeaders["X-UA-Max-Total"] = (EventResponse.MAX_TOTAL_DB_SIZE_BYTES / 1024 + 1).toString()
        assertEquals(
            "Should constrain to the max",
            eventResponse.maxTotalSize.toLong(),
            EventResponse.MAX_TOTAL_DB_SIZE_BYTES.toLong()
        )

        // Test a value below the max
        responseHeaders["X-UA-Max-Total"] = (EventResponse.MAX_TOTAL_DB_SIZE_BYTES / 1024 - 1).toString()
        assertEquals(
            "Should allow values between the min and max",
            eventResponse.maxTotalSize.toLong(),
            (EventResponse.MAX_TOTAL_DB_SIZE_BYTES - 1024).toLong()
        )

        // Test a value at the min
        responseHeaders["X-UA-Max-Total"] = (EventResponse.MIN_TOTAL_DB_SIZE_BYTES / 1024).toString()
        assertEquals(
            "Should constrain to the min",
            eventResponse.maxTotalSize.toLong(),
            EventResponse.MIN_TOTAL_DB_SIZE_BYTES.toLong()
        )

        // Test a value below the min
        responseHeaders["X-UA-Max-Total"] = (EventResponse.MIN_TOTAL_DB_SIZE_BYTES / 1024 - 1).toString()
        assertEquals(
            "Should constrain to the min",
            eventResponse.maxTotalSize.toLong(),
            EventResponse.MIN_TOTAL_DB_SIZE_BYTES.toLong()
        )

        // Test a value above the min
        responseHeaders["X-UA-Max-Total"] = (EventResponse.MIN_TOTAL_DB_SIZE_BYTES / 1024 + 1).toString()
        assertEquals(
            "Should allow values between the min and max",
            eventResponse.maxTotalSize.toLong(),
            (EventResponse.MIN_TOTAL_DB_SIZE_BYTES + 1024).toLong()
        )
    }

    /**
     * Test that the max batch size constrains the values between its max and min.
     */
    @Test
    public fun testMaxBatchSize() {
        // Test a value at the max
        responseHeaders["X-UA-Max-Batch"] = (EventResponse.MAX_BATCH_SIZE_BYTES / 1024).toString()
        assertEquals(
            "Should constrain to the max",
            eventResponse.maxBatchSize.toLong(),
            EventResponse.MAX_BATCH_SIZE_BYTES.toLong()
        )

        // Test a value above the max
        responseHeaders["X-UA-Max-Batch"] = (EventResponse.MAX_BATCH_SIZE_BYTES / 1024 + 1).toString()
        assertEquals(
            "Should constrain to the max",
            eventResponse.maxBatchSize.toLong(),
            EventResponse.MAX_BATCH_SIZE_BYTES.toLong()
        )

        // Test a value below the max
        responseHeaders["X-UA-Max-Batch"] = (EventResponse.MAX_BATCH_SIZE_BYTES / 1024 - 1).toString()
        assertEquals(
            "Should allow values between the min and max",
            eventResponse.maxBatchSize.toLong(),
            (EventResponse.MAX_BATCH_SIZE_BYTES - 1024).toLong()
        )

        // Test a value at the min
        responseHeaders["X-UA-Max-Batch"] = (EventResponse.MIN_BATCH_SIZE_BYTES / 1024).toString()
        assertEquals(
            "Should constrain to the min",
            eventResponse.maxBatchSize.toLong(),
            EventResponse.MIN_BATCH_SIZE_BYTES.toLong()
        )

        // Test a value below the min
        responseHeaders["X-UA-Max-Batch"] = (EventResponse.MIN_BATCH_SIZE_BYTES / 1024 - 1).toString()
        assertEquals(
            "Should constrain to the min",
            eventResponse.maxBatchSize.toLong(),
            EventResponse.MIN_BATCH_SIZE_BYTES.toLong()
        )

        // Test a value above the min
        responseHeaders["X-UA-Max-Batch"] = (EventResponse.MIN_BATCH_SIZE_BYTES / 1024 + 1).toString()
        assertEquals(
            "Should allow values between the min and max",
            eventResponse.maxBatchSize.toLong(),
            (EventResponse.MIN_BATCH_SIZE_BYTES + 1024).toLong()
        )
    }

    /**
     * Test that the min batch interval time constrains the values between its max and min.
     */
    @Test
    public fun testMinBatchInterval() {
        // Test a value at the max
        responseHeaders["X-UA-Min-Batch-Interval"] = EventResponse.MAX_BATCH_INTERVAL_MS.toString()
        assertEquals(
            "Should constrain to the max",
            eventResponse.minBatchInterval.toLong(),
            EventResponse.MAX_BATCH_INTERVAL_MS.toLong()
        )

        // Test a value above the max
        responseHeaders["X-UA-Min-Batch-Interval"] = (EventResponse.MAX_BATCH_INTERVAL_MS + 1).toString()
        assertEquals(
            "Should constrain to the max",
            eventResponse.minBatchInterval.toLong(),
            EventResponse.MAX_BATCH_INTERVAL_MS.toLong()
        )

        // Test a value below the max
        responseHeaders["X-UA-Min-Batch-Interval"] = (EventResponse.MAX_BATCH_INTERVAL_MS - 1).toString()
        assertEquals(
            "Should allow values between the min and max",
            eventResponse.minBatchInterval.toLong(),
            (EventResponse.MAX_BATCH_INTERVAL_MS - 1).toLong()
        )

        // Test a value at the min
        responseHeaders["X-UA-Min-Batch-Interval"] = EventResponse.MIN_BATCH_INTERVAL_MS.toString()
        assertEquals(
            "Should constrain to the min",
            eventResponse.minBatchInterval.toLong(),
            EventResponse.MIN_BATCH_INTERVAL_MS.toLong()
        )

        // Test a value below the min
        responseHeaders["X-UA-Min-Batch-Interval"] = (EventResponse.MIN_BATCH_INTERVAL_MS - 1).toString()
        assertEquals(
            "Should constrain to the min",
            eventResponse.minBatchInterval.toLong(),
            EventResponse.MIN_BATCH_INTERVAL_MS.toLong()
        )

        // Test a value above the min
        responseHeaders["X-UA-Min-Batch-Interval"] = (EventResponse.MIN_BATCH_INTERVAL_MS + 1).toString()
        assertEquals(
            "Should allow values between the min and max",
            eventResponse.minBatchInterval.toLong(),
            (EventResponse.MIN_BATCH_INTERVAL_MS + 1).toLong()
        )
    }
}
