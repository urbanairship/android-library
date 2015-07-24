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

package com.urbanairship.http;

import android.support.annotation.NonNull;

import java.util.List;
import java.util.Map;

/**
 * Model object containing response information from a request.
 */
public class Response {

    private String responseBody;
    private Map<String, List<String>> responseHeaders;
    private int status;
    private String responseMessage;
    private long lastModified;

    private Response() {

    }

    /**
     * The Response as a string.
     *
     * @return The response as a string.
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("Response: ");
        builder.append("ResponseBody: ");

        if (responseBody != null) {
            builder.append(responseBody);
        }

        builder.append(" ResponseHeaders: ");

        if (responseHeaders != null) {
            builder.append(responseHeaders);
        }

        builder.append(" ResponseMessage: ");

        if (responseMessage != null) {
            builder.append(responseMessage);
        }

        builder.append(" Status: ").append(Integer.toString(status));

        return builder.toString();
    }

    /**
     * Returns the response status code.
     *
     * @return The response status code as an int.
     */
    public int getStatus() {
        return status;
    }

    /**
     * Returns the response body.
     *
     * @return The response body as a string.
     */
    public String getResponseBody() {
        return responseBody;
    }

    /**
     * Gets the last modified header value in milliseconds.
     *
     * @return the last modified header value in milliseconds, or 0 if does not exist.
     */
    public long getLastModifiedTime() {
        return lastModified;
    }

    /**
     * Returns the response headers.
     *
     * @return The response headers as a map.
     */
    public Map<String, List<String>> getResponseHeaders() {
        return responseHeaders;
    }

    /**
     * Builds a Request Response.
     */
    public static class Builder {

        private String responseBody;
        private Map<String, List<String>> responseHeaders;
        private final int status;
        private String responseMessage;
        private long lastModified = 0;

        /**
         * Creates a new response builder.
         *
         * @param status The response status code.
         */
        public Builder(int status) {
            this.status = status;
        }


        /**
         * Set the response message.
         *
         * @param responseMessage The response message string.
         * @return The builder with the response message set.
         */
        public Builder setResponseMessage(String responseMessage) {
            this.responseMessage = responseMessage;
            return this;
        }

        /**
         * Set the response body.
         *
         * @param responseBody The response body string.
         * @return The builder with the response body set.
         */
        public Builder setResponseBody(String responseBody) {
            this.responseBody = responseBody;
            return this;
        }

        /**
         * Set the response headers.
         *
         * @param responseHeaders The response headers.
         * @return The builder with the response headers set.
         */
        public Builder setResponseHeaders(Map<String, List<String>> responseHeaders) {
            this.responseHeaders = responseHeaders;
            return this;
        }

        /**
         * Set the last modified time in milliseconds.
         *
         * @param lastModified The modified time in milliseconds.
         * @return The builder with the last modified time.
         */
        public Builder setLastModified(long lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        /**
         * Creates a response.
         *
         * @return The response.
         */
        @NonNull
        public Response create() {
            Response response = new Response();
            response.status = status;
            response.responseBody = responseBody;
            response.responseHeaders = responseHeaders;
            response.responseMessage = responseMessage;
            response.lastModified = lastModified;

            return response;
        }

    }
}
