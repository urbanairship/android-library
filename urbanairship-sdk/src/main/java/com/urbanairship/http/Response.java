/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.http;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

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

    /**
     * Retrieves the first header value for a given key.
     *
     * @param key The key.
     * @return The first header value.
     */
    @Nullable
    public String getResponseHeader(String key) {
        if (responseHeaders != null) {
            List<String> headersList = responseHeaders.get(key);
            if (headersList != null && headersList.size() > 0) {
                return headersList.get(0);
            }
        }

        return null;
    }
}
