/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.http;

import android.annotation.SuppressLint;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;

import java.util.List;
import java.util.Map;

/**
 * Model object containing response information from a request.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Response {

    /**
     * Status code for 429 - too many requests.
     */
    public static final int HTTP_TOO_MANY_REQUESTS = 429;

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
    @SuppressLint("UnknownNullness")
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
    @Nullable
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
    @Nullable
    public Map<String, List<String>> getResponseHeaders() {
        return responseHeaders;
    }

    /**
     * Builder factory method.
     *
     * @param status The status.
     *
     * @return A new builder instance.
     */
    public static Builder newBuilder(int status) {
        return new Builder(status);
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
        @NonNull
        public Builder setResponseMessage(@Nullable String responseMessage) {
            this.responseMessage = responseMessage;
            return this;
        }

        /**
         * Set the response body.
         *
         * @param responseBody The response body string.
         * @return The builder with the response body set.
         */
        @NonNull
        public Builder setResponseBody(@Nullable String responseBody) {
            this.responseBody = responseBody;
            return this;
        }

        /**
         * Set the response headers.
         *
         * @param responseHeaders The response headers.
         * @return The builder with the response headers set.
         */
        @NonNull
        public Builder setResponseHeaders(@Nullable Map<String, List<String>> responseHeaders) {
            this.responseHeaders = responseHeaders;
            return this;
        }

        /**
         * Set the last modified time in milliseconds.
         *
         * @param lastModified The modified time in milliseconds.
         * @return The builder with the last modified time.
         */
        @NonNull
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
        public Response build() {
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
    public String getResponseHeader(@NonNull String key) {
        if (responseHeaders != null) {
            List<String> headersList = responseHeaders.get(key);
            if (headersList != null && headersList.size() > 0) {
                return headersList.get(0);
            }
        }

        return null;
    }
}
