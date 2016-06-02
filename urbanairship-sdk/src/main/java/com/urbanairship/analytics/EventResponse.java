/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.analytics;

import com.urbanairship.http.Response;
import com.urbanairship.util.UAMathUtil;

import java.util.List;

/**
 * Model object containing response information from a request.
 */
class EventResponse {

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
                return UAMathUtil.constrain(Integer.parseInt(headerList.get(0)),
                        AnalyticsPreferences.MIN_TOTAL_DB_SIZE_BYTES,
                        AnalyticsPreferences.MAX_TOTAL_DB_SIZE_BYTES);
            }
        }
        return AnalyticsPreferences.MIN_TOTAL_DB_SIZE_BYTES;
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
                return UAMathUtil.constrain(Integer.parseInt(headerList.get(0)),
                        AnalyticsPreferences.MIN_BATCH_SIZE_BYTES,
                        AnalyticsPreferences.MAX_BATCH_SIZE_BYTES);
            }
        }
        return AnalyticsPreferences.MIN_BATCH_SIZE_BYTES;
    }

    /**
     * Returns the maximum wait time.
     *
     * @return The maximum wait time as an Integer.
     */
    int getMaxWait() {
        if (response.getResponseHeaders() != null) {
            List<String> headerList = response.getResponseHeaders().get("X-UA-Max-Wait");
            if (headerList != null && headerList.size() > 0) {
                return UAMathUtil.constrain(Integer.parseInt(headerList.get(0)),
                        AnalyticsPreferences.MIN_WAIT_MS,
                        AnalyticsPreferences.MAX_WAIT_MS);
            }
        }
        return AnalyticsPreferences.MIN_WAIT_MS;
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
                        AnalyticsPreferences.MIN_BATCH_INTERVAL_MS,
                        AnalyticsPreferences.MAX_BATCH_INTERVAL_MS);
            }
        }
        return AnalyticsPreferences.MIN_BATCH_INTERVAL_MS;
    }
}
