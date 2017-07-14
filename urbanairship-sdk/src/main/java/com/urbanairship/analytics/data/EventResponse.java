/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics.data;

import com.urbanairship.http.Response;
import com.urbanairship.util.UAMathUtil;

import java.util.List;

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

    private final Response response;

    public EventResponse(Response response) {
        this.response = response;
    }

    /**
     * Returns the response status code.
     *
     * @return The response status code as an int.
     */
    public int getStatus() {
        return response.getStatus();
    }

    /**
     * Returns the maximum total size.
     *
     * @return The maximum total size as an Integer
     */
    int getMaxTotalSize() {
        if (response.getResponseHeaders() != null) {
            List<String> headerList = response.getResponseHeaders().get("X-UA-Max-Total");
            if (headerList != null && headerList.size() > 0) {
                return UAMathUtil.constrain(Integer.parseInt(headerList.get(0)) * 1024,
                        MIN_TOTAL_DB_SIZE_BYTES,
                        MAX_TOTAL_DB_SIZE_BYTES);
            }
        }
        return MIN_TOTAL_DB_SIZE_BYTES;
    }

    /**
     * Returns the maximum batch size.
     *
     * @return The maximum batch size as an Integer.
     */
    int getMaxBatchSize() {
        if (response.getResponseHeaders() != null) {
            List<String> headerList = response.getResponseHeaders().get("X-UA-Max-Batch");
            if (headerList != null && headerList.size() > 0) {
                return UAMathUtil.constrain(Integer.parseInt(headerList.get(0)) * 1024,
                        MIN_BATCH_SIZE_BYTES,
                        MAX_BATCH_SIZE_BYTES);
            }
        }
        return MIN_BATCH_SIZE_BYTES;
    }

    /**
     * Returns the minimum batch interval.
     *
     * @return The minimum batch interval as an Integer.
     */
    int getMinBatchInterval() {
        if (response.getResponseHeaders() != null) {
            List<String> headerList = response.getResponseHeaders().get("X-UA-Min-Batch-Interval");
            if (headerList != null && headerList.size() > 0) {
                return UAMathUtil.constrain(Integer.parseInt(headerList.get(0)),
                        MIN_BATCH_INTERVAL_MS,
                        MAX_BATCH_INTERVAL_MS);
            }
        }
        return MIN_BATCH_INTERVAL_MS;
    }
}
