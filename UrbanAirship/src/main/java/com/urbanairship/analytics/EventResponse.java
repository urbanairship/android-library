/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

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
    public Integer getMaxTotalSize() {
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
    public Integer getMaxBatchSize() {
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
    public Integer getMaxWait() {
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
    public Integer getMinBatchInterval() {
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
