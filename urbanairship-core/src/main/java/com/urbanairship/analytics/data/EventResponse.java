/* Copyright Airship and Contributors */

package com.urbanairship.analytics.data;

import com.urbanairship.util.UAMathUtil;

import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;

/**
 * Model object containing response information from a request.
 */
class EventResponse {

    static final int MAX_TOTAL_DB_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB
    static final int MIN_TOTAL_DB_SIZE_BYTES = 10 * 1024;       // 10 KB

    static final int MAX_BATCH_SIZE_BYTES = 500 * 1024; // 500 KB
    static final int MIN_BATCH_SIZE_BYTES = 10 * 1024;  // 10 KB

    static final int MIN_BATCH_INTERVAL_MS = 60 * 1000;             // 60 seconds
    static final int MAX_BATCH_INTERVAL_MS = 7 * 24 * 3600 * 1000;  // 7 days

    @NonNull
    private final Map<String, String> headers;

    public EventResponse(@NonNull Map<String, String> headers) {
        this.headers = headers;
    }

    /**
     * Returns the maximum total size.
     *
     * @return The maximum total size as an Integer
     */
    int getMaxTotalSize() {
        String maxTotalSize = headers.get("X-UA-Max-Total");
        if (maxTotalSize != null) {
            return UAMathUtil.constrain(Integer.parseInt(maxTotalSize) * 1024,
                    MIN_TOTAL_DB_SIZE_BYTES,
                    MAX_TOTAL_DB_SIZE_BYTES);
        }
        return MIN_TOTAL_DB_SIZE_BYTES;
    }

    /**
     * Returns the maximum batch size.
     *
     * @return The maximum batch size as an Integer.
     */
    int getMaxBatchSize() {
        String maxBatchSize = headers.get("X-UA-Max-Batch");
        if (maxBatchSize != null) {
            return UAMathUtil.constrain(Integer.parseInt(maxBatchSize) * 1024,
                    MIN_BATCH_SIZE_BYTES,
                    MAX_BATCH_SIZE_BYTES);
        }
        return MIN_BATCH_SIZE_BYTES;
    }

    /**
     * Returns the minimum batch interval.
     *
     * @return The minimum batch interval as an Integer.
     */
    int getMinBatchInterval() {
        String minBatchInterval = headers.get("X-UA-Min-Batch-Interval");
        if (minBatchInterval != null) {
            return UAMathUtil.constrain(Integer.parseInt(minBatchInterval),
                    MIN_BATCH_INTERVAL_MS,
                    MAX_BATCH_INTERVAL_MS);
        }
        return MIN_BATCH_INTERVAL_MS;
    }

}
