/* Copyright Airship and Contributors */

package com.urbanairship.automation.tags;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.Clock;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Cache for storing tag group lookup responses.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class TagGroupLookupResponseCache {
    private static final String RESPONSE_KEY = "com.urbanairship.iam.tags.TAG_CACHE_RESPONSE";
    private static final String CREATE_DATE_KEY = "com.urbanairship.iam.tags.TAG_CACHE_CREATE_DATE";
    private static final String REQUESTED_TAGS_KEY = "com.urbanairship.iam.tags.TAG_CACHE_REQUESTED_TAGS";
    private static final String MAX_AGE_TIME_KEY = "com.urbanairship.iam.tags.TAG_CACHE_MAX_AGE_TIME";
    private static final String STALE_READ_TIME_KEY = "com.urbanairship.iam.tags.TAG_STALE_READ_TIME";

    private final PreferenceDataStore dataStore;
    private final Clock clock;

    /**
     * Min cache age time.
     */
    public static final long MIN_MAX_AGE_TIME_MS = 60000; // 1 minute

    /**
     * Default cache max age time.
     */
    public static final long DEFAULT_MAX_AGE_TIME_MS = 600000; // 10 minutes

    /**
     * Default cache stale read time.
     */
    public static final long DEFAULT_STALE_READ_TIME_MS = 3600000; // 1 hour

    /**
     * Default constructor.
     *
     * @param dataStore The preference data store.
     */
    public TagGroupLookupResponseCache(@NonNull PreferenceDataStore dataStore, @NonNull Clock clock) {
        this.dataStore = dataStore;
        this.clock = clock;
    }

    /**
     * Sets the max cache age. If the cache is older than the specified duration,
     * it will be refreshed.
     *
     * @param duration The duration.
     * @param unit The time unit.
     */
    public void setMaxAgeTime(@IntRange(from = 0) long duration, @NonNull TimeUnit unit) {
        dataStore.put(MAX_AGE_TIME_KEY, unit.toMillis(duration));
    }

    /**
     * Gets the max cache age in milliseconds.
     *
     * @return The max cache age in milliseconds.
     */
    public long getMaxAgeTimeMilliseconds() {
        long maxAge = dataStore.getLong(MAX_AGE_TIME_KEY, DEFAULT_MAX_AGE_TIME_MS);
        return Math.max(maxAge, MIN_MAX_AGE_TIME_MS);
    }

    /**
     * Sets the cache stale read time. If the cache fails to refresh, the cache will still
     * be used if it's newer than the specified duration.
     *
     * @param duration The duration.
     * @param unit The time unit.
     */
    public void setStaleReadTime(@IntRange(from = MIN_MAX_AGE_TIME_MS) long duration, @NonNull TimeUnit unit) {
        dataStore.put(STALE_READ_TIME_KEY, unit.toMillis(duration));
    }

    /**
     * Gets the cache stale age read time in milliseconds.
     *
     * @return The cache stale age read time in milliseconds.
     */
    public long getStaleReadTimeMilliseconds() {
        return dataStore.getLong(STALE_READ_TIME_KEY, DEFAULT_STALE_READ_TIME_MS);
    }

    /**
     * Sets the cached response.
     *
     * @param response The response to cache.
     */
    public void setResponse(@NonNull TagGroupResponse response, @NonNull Map<String, Set<String>> requestedTags) {
        dataStore.put(RESPONSE_KEY, response);
        dataStore.put(CREATE_DATE_KEY, clock.currentTimeMillis());
        dataStore.put(REQUESTED_TAGS_KEY, JsonValue.wrapOpt(requestedTags));
    }

    /**
     * Clears the cache
     */
    public void clear() {
        dataStore.remove(RESPONSE_KEY);
        dataStore.remove(CREATE_DATE_KEY);
        dataStore.remove(REQUESTED_TAGS_KEY);
    }

    /**
     * Gets the cached response.
     *
     * @return The cached response, or null if not available.
     */
    @Nullable
    public TagGroupResponse getResponse() {
        JsonValue value = dataStore.getJsonValue(RESPONSE_KEY);
        if (value.isNull()) {
            return null;
        }
        return TagGroupResponse.fromJsonValue(value);
    }

    /**
     * Gets the cache creation date.
     *
     * @return The cache creation date if available, otherwise {@code -1} will be returned.
     */
    public long getCreateDate() {
        return dataStore.getLong(CREATE_DATE_KEY, -1);
    }

    /**
     * Gets the cache creation date.
     *
     * @return The cache creation date if available, otherwise {@code -1} will be returned.
     */
    public Map<String, Set<String>> getRequestTags() {
        return TagGroupUtils.parseTags(dataStore.getJsonValue(REQUESTED_TAGS_KEY));
    }
}
