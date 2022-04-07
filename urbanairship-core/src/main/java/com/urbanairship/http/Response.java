/* Copyright Airship and Contributors */

package com.urbanairship.http;

import android.net.Uri;

import com.urbanairship.Logger;
import com.urbanairship.util.Clock;
import com.urbanairship.util.DateUtils;
import com.urbanairship.util.UAHttpStatusUtil;

import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * Model object containing response information from a request.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Response<T> {

    /**
     * Status code for 429 - too many requests.
     */
    public static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final String responseBody;
    private final Map<String, List<String>> responseHeaders;
    private final int status;
    private final long lastModified;
    private final T result;

    private Response(Builder<T> builder) {
        this.status = builder.status;
        this.responseBody = builder.responseBody;
        this.responseHeaders = builder.responseHeaders;
        this.lastModified = builder.lastModified;
        this.result = builder.result;
    }

    protected Response(@NonNull Response<T> response) {
        this.status = response.status;
        this.responseBody = response.responseBody;
        this.responseHeaders = response.responseHeaders;
        this.lastModified = response.lastModified;
        this.result = response.result;
    }

    /**
     * Gets the result.
     *
     * @return The channel result.
     */
    public T getResult() {
        return result;
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

    @Override
    @NonNull
    public String toString() {
        return "Response{" +
                "responseBody='" + responseBody + '\'' +
                ", responseHeaders=" + responseHeaders +
                ", status=" + status +
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
     * True if the status is 500-599, otherwise false.
     *
     * @return {@code true} if the status is 500-599, otherwise {@code false}.
     */
    public boolean isServerError() {
        return UAHttpStatusUtil.inServerErrorRange(status);
    }

    /**
     * True if the status is 400-499, otherwise false.
     *
     * @return {@code true} if the status is 400-499, otherwise {@code false}.
     */
    public boolean isClientError() {
        return UAHttpStatusUtil.inClientErrorRange(status);
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
     * Returns the location header if set.
     *
     * @return The location header if set, otherwise null.
     */
    @Nullable
    public Uri getLocationHeader() {
        String location = getResponseHeader("Location");
        if (location == null) {
            return null;
        }

        try {
            return Uri.parse(location);
        } catch (Exception e) {
            Logger.error("Failed to parse location header.");
            return null;
        }
    }

    /**
     * Returns the retry-after header if set.
     *
     * @param timeUnit The resulting time unit.
     * @param defaultValue The default value.
     * @return The retry-after in the time unit if set, otherwise the defaultValue.
     */
    public long getRetryAfterHeader(@NonNull TimeUnit timeUnit, long defaultValue) {
        return getRetryAfterHeader(timeUnit, defaultValue, Clock.DEFAULT_CLOCK);
    }

    @VisibleForTesting
    public long getRetryAfterHeader(@NonNull TimeUnit timeUnit, long defaultValue, @NonNull Clock clock) {
        String retryAfter = getResponseHeader("Retry-After");
        if (retryAfter == null) {
            return defaultValue;
        }

        try {
            long retryDate = DateUtils.parseIso8601(retryAfter);
            long milliseconds = retryDate - clock.currentTimeMillis();
            return timeUnit.convert(milliseconds, TimeUnit.MILLISECONDS);
        } catch (ParseException ignored) {
        }

        try {
            long seconds = Long.parseLong(retryAfter);
            return timeUnit.convert(seconds, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }

        Logger.error("Invalid RetryAfter header %s", retryAfter);
        return defaultValue;
    }

    /**
     * True if the status is 429, otherwise false.
     *
     * @return {@code true} if the status is 429, otherwise {@code false}.
     */
    public boolean isTooManyRequestsError() {
        return status == HTTP_TOO_MANY_REQUESTS;
    }

    /**
     * Builds a Request Response.
     */
    public static class Builder<T> {

        private String responseBody;
        private Map<String, List<String>> responseHeaders;
        private final int status;
        private long lastModified = 0;
        private T result;

        /**
         * Creates a new response builder.
         *
         * @param status The response status code.
         */
        public Builder(int status) {
            this.status = status;
        }

        /**
         * Set the response body.
         *
         * @param responseBody The response body string.
         * @return The builder with the response body set.
         */
        @NonNull
        public Builder<T> setResponseBody(@Nullable String responseBody) {
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
        public Builder<T> setResponseHeaders(@Nullable Map<String, List<String>> responseHeaders) {
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
        public Builder<T> setLastModified(long lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        /**
         * Sets the parsed result
         *
         * @param result The parsed result.
         * @return The builder.
         */
        @NonNull
        public Builder<T> setResult(T result) {
            this.result = result;
            return this;
        }

        /**
         * Creates a response.
         *
         * @return The response.
         */
        @NonNull
        public Response<T> build() {
            return new Response<>(this);
        }

    }

}
