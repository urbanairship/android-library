/* Copyright Airship and Contributors */

package com.urbanairship.http;

import com.urbanairship.util.UAHttpStatusUtil;

import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Model object containing response information from a request.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Response {

    /**
     * Status code for 429 - too many requests.
     */
    public static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final String responseBody;
    private final Map<String, List<String>> responseHeaders;
    private final int status;
    private final String responseMessage;
    private final long lastModified;

    private Response(Builder builder) {
        this.status = builder.status;
        this.responseBody = builder.responseBody;
        this.responseHeaders = builder.responseHeaders;
        this.responseMessage = builder.responseMessage;
        this.lastModified = builder.lastModified;
    }

    protected Response(Response response) {
        this.status = response.status;
        this.responseBody = response.responseBody;
        this.responseHeaders = response.responseHeaders;
        this.responseMessage = response.responseMessage;
        this.lastModified = response.lastModified;
    }

    @Override
    public String toString() {
        return "Response{" +
                "responseBody='" + responseBody + '\'' +
                ", responseHeaders=" + responseHeaders +
                ", status=" + status +
                ", responseMessage='" + responseMessage + '\'' +
                ", lastModified=" + lastModified +
                '}';
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
     * True if the status is 200-299, otherwise false.
     *
     * @return {@code true} if the status is 200-299, otherwise {@code false}.
     */
    public boolean isSuccessful() {
        return UAHttpStatusUtil.inSuccessRange(status);
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
            return new Response(this);
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
