/* Copyright Airship and Contributors */

package com.urbanairship.iam.tags;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushManager;
import com.urbanairship.channel.TagGroupRegistrar;
import com.urbanairship.util.Clock;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * In-App Message Manager helper class that manages tag group audience data.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class TagGroupManager {

    // Device tag group
    private static final String DEVICE_GROUP = "device";

    // Data store keys
    private static final String CACHE_RESPONSE_KEY = "com.urbanairship.iam.tags.TAG_CACHE_RESPONSE";
    private static final String CACHE_CREATE_DATE_KEY = "com.urbanairship.iam.tags.TAG_CACHE_CREATE_DATE";
    private static final String CACHE_REQUESTED_TAGS_KEY = "com.urbanairship.iam.tags.TAG_CACHE_REQUESTED_TAGS";
    private static final String CACHE_MAX_AGE_TIME_KEY = "com.urbanairship.iam.tags.TAG_CACHE_MAX_AGE_TIME";
    private static final String CACHE_STALE_READ_TIME_KEY = "com.urbanairship.iam.tags.TAG_CACHE_STALE_READ_TIME";
    private static final String PREFER_LOCAL_DATA_TIME_KEY = "com.urbanairship.iam.tags.TAG_PREFER_LOCAL_DATA_TIME";
    private static final String ENABLED_KEY = "com.urbanairship.iam.tags.FETCH_ENABLED";

    /**
     * Min cache age time.
     */
    public static final long MIN_CACHE_MAX_AGE_TIME_MS = 60000; // 1 minute

    /**
     * Default cache max age time.
     */
    public static final long DEFAULT_CACHE_MAX_AGE_TIME_MS = 600000; // 10 minutes

    /**
     * Default cache stale read time.
     */
    public static final long DEFAULT_CACHE_STALE_READ_TIME_MS = 3600000; // 1 hour

    /**
     * Default prefer local data time.
     */
    public static final long DEFAULT_PREFER_LOCAL_DATA_TIME_MS = 600000; // 10 minutes

    /**
     * Callback used to get the request tags when refreshing the cache.
     */
    public interface RequestTagsCallback {

        /**
         * Called to get the tags to request.
         *
         * @return The tags to request.
         */
        @NonNull
        Map<String, Set<String>> getTags() throws Exception;

    }

    private final PreferenceDataStore dataStore;
    private final TagGroupHistorian historian;
    private final AirshipChannel airshipChannel;
    private final TagGroupLookupApiClient client;
    private final Clock clock;

    private RequestTagsCallback requestTagsCallback;

    /**
     * Default constructor.
     *
     * @param configOptions The airship config options.
     * @param airshipChannel The Airship channel.
     * @param tagGroupRegistrar The tag group registrar.
     * @param dataStore The preference data store.
     */
    public TagGroupManager(@NonNull AirshipConfigOptions configOptions, @NonNull AirshipChannel airshipChannel,
                           @NonNull TagGroupRegistrar tagGroupRegistrar, @NonNull PreferenceDataStore dataStore) {
        this(new TagGroupLookupApiClient(configOptions), airshipChannel,
                new TagGroupHistorian(tagGroupRegistrar, dataStore, Clock.DEFAULT_CLOCK),
                dataStore, Clock.DEFAULT_CLOCK);
    }

    @VisibleForTesting
    TagGroupManager(@NonNull TagGroupLookupApiClient client, @NonNull AirshipChannel airshipChannel,
                    @NonNull TagGroupHistorian historian, @NonNull PreferenceDataStore dataStore,
                    @NonNull Clock clock) {
        this.client = client;
        this.airshipChannel = airshipChannel;
        this.historian = historian;
        this.dataStore = dataStore;
        this.clock = clock;

        this.historian.init();
        updateMaxRecordAge();
    }

    /**
     * Enables/disables the tag group manager. If disabled, getTags() will return an error result.
     */
    public void setEnabled(boolean enabled) {
        dataStore.put(ENABLED_KEY, enabled);
    }

    /**
     * Gets if the instance is enabled or disabled.
     *
     * @return {@code true} if enabled, otherwise {@code false}.
     */
    public boolean isEnabled() {
        return dataStore.getBoolean(ENABLED_KEY, true);
    }

    /**
     * Sets the max cache age. If the cache is older than the specified duration,
     * it will be refreshed.
     *
     * @param duration The duration.
     * @param unit The time unit.
     */
    public void setCacheMaxAgeTime(@IntRange(from = 0) long duration, @NonNull TimeUnit unit) {
        dataStore.put(CACHE_MAX_AGE_TIME_KEY, unit.toMillis(duration));
    }

    /**
     * Gets the max cache age in milliseconds.
     *
     * @return The max cache age in milliseconds.
     */
    public long getCacheMaxAgeTimeMilliseconds() {
        long maxAge = dataStore.getLong(CACHE_MAX_AGE_TIME_KEY, DEFAULT_CACHE_MAX_AGE_TIME_MS);
        return Math.max(maxAge, MIN_CACHE_MAX_AGE_TIME_MS);
    }

    /**
     * Sets the cache stale read time. If the cache fails to refresh, the cache will still
     * be used if it's newer than the specified duration.
     *
     * @param duration The duration.
     * @param unit The time unit.
     */
    public void setCacheStaleReadTime(@IntRange(from = MIN_CACHE_MAX_AGE_TIME_MS) long duration, @NonNull TimeUnit unit) {
        dataStore.put(CACHE_STALE_READ_TIME_KEY, unit.toMillis(duration));
        updateMaxRecordAge();
    }

    /**
     * Gets the cache stale age read time in milliseconds.
     *
     * @return The cache stale age read time in milliseconds.
     */
    public long getCacheStaleReadTimeMilliseconds() {
        return dataStore.getLong(CACHE_STALE_READ_TIME_KEY, DEFAULT_CACHE_STALE_READ_TIME_MS);
    }

    /**
     * Sets the request tags callback. The callback is used to gather the
     * tags to request when refreshing the tag group cache.
     *
     * @param callback The callback.
     */
    public void setRequestTagsCallback(@Nullable RequestTagsCallback callback) {
        this.requestTagsCallback = callback;
    }

    /**
     * Gets the prefer local tag data time.
     * <p>
     *
     * @return The cache stale age read time in milliseconds.
     */
    public long getPreferLocalTagDataTime() {
        return dataStore.getLong(PREFER_LOCAL_DATA_TIME_KEY, DEFAULT_PREFER_LOCAL_DATA_TIME_MS);
    }

    /**
     * Sets the prefer local tag data time.
     * <p>
     * Any local data that is newer than the cached - the specified amount will be applied to the
     * get tags result.
     *
     * @param duration The duration.
     * @param unit The time unit.
     */
    public void setPreferLocalTagDataTime(@IntRange(from = 0) long duration, @NonNull TimeUnit unit) {
        dataStore.put(PREFER_LOCAL_DATA_TIME_KEY, unit.toMillis(duration));
        updateMaxRecordAge();
    }

    /**
     * Gets the current tags for the channel.
     * <p>
     * Only the tags that are being requested will be returned. If the request that fetched
     * the tags does not contain the requested tags, a new request will be made. When fetching tags,
     * the {@link RequestTagsCallback} will be called to generate the set of tags to be requested.
     *
     * @param tags The requested tags.
     * @return The tag result.
     */
    @NonNull
    @WorkerThread
    public synchronized TagGroupResult getTags(@NonNull Map<String, Set<String>> tags) {
        if (requestTagsCallback == null) {
            throw new IllegalStateException("RequestTagsCallback not set");
        }

        if (!isEnabled()) {
            return new TagGroupResult(false, null);
        }

        // Empty tags
        if (tags.isEmpty()) {
            return new TagGroupResult(true, tags);
        }

        // Requesting only `device` tag groups when channel tag registration is enabled
        if (tags.size() == 1 && tags.containsKey(DEVICE_GROUP) && airshipChannel.getChannelTagRegistrationEnabled()) {
            Map<String, Set<String>> deviceTags = new HashMap<>();
            deviceTags.put(DEVICE_GROUP, airshipChannel.getTags());
            return new TagGroupResult(true, deviceTags);
        }

        // Requires a channel
        if (airshipChannel.getId() == null) {
            return new TagGroupResult(false, null);
        }

        long cacheStaleReadTime = getCacheStaleReadTimeMilliseconds();
        long cacheMaxAgeTime = getCacheMaxAgeTimeMilliseconds();

        TagGroupResponse cachedResponse = null;
        if (TagGroupUtils.containsAll(getCachedRequestTags(), tags)) {
            cachedResponse = getCachedResponse();
        }

        long cacheCreateDate = getCacheCreateDate();

        if (cachedResponse != null && cacheMaxAgeTime > clock.currentTimeMillis() - cacheCreateDate) {
            return new TagGroupResult(true, generateTags(tags, cachedResponse, cacheCreateDate));
        }

        // Refresh the cache
        try {
            refreshCache(tags, cachedResponse);
            cachedResponse = getCachedResponse();
            cacheCreateDate = getCacheCreateDate();
        } catch (Exception e) {
            Logger.error(e, "Failed to refresh tags.");
        }

        if (cachedResponse == null) {
            return new TagGroupResult(false, null);
        }

        if (cacheStaleReadTime <= 0 || cacheStaleReadTime > clock.currentTimeMillis() - cacheCreateDate) {
            return new TagGroupResult(true, generateTags(tags, cachedResponse, cacheCreateDate));
        }

        return new TagGroupResult(false, null);
    }

    /**
     * Sets the cached response.
     *
     * @param response The response to cache.
     */
    private void setCachedResponse(@NonNull TagGroupResponse response, @NonNull Map<String, Set<String>> requestedTags) {
        dataStore.put(CACHE_RESPONSE_KEY, response);
        dataStore.put(CACHE_CREATE_DATE_KEY, clock.currentTimeMillis());
        dataStore.put(CACHE_REQUESTED_TAGS_KEY, JsonValue.wrapOpt(requestedTags));
    }

    /**
     * Gets the cached response.
     *
     * @return The cached response, or null if not available.
     */
    @Nullable
    private TagGroupResponse getCachedResponse() {
        JsonValue value = dataStore.getJsonValue(CACHE_RESPONSE_KEY);
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
    private long getCacheCreateDate() {
        return dataStore.getLong(CACHE_CREATE_DATE_KEY, -1);
    }

    /**
     * Gets the cache creation date.
     *
     * @return The cache creation date if available, otherwise {@code -1} will be returned.
     */
    private Map<String, Set<String>> getCachedRequestTags() {
        return TagGroupUtils.parseTags(dataStore.getJsonValue(CACHE_REQUESTED_TAGS_KEY));
    }

    /**
     * Updates the historian's max record age.
     */
    private void updateMaxRecordAge() {
        long age = getCacheStaleReadTimeMilliseconds() + getPreferLocalTagDataTime();
        this.historian.setMaxRecordAge(age, TimeUnit.MILLISECONDS);
    }

    /**
     * Helper method to generate the tags from the response. Local cache data will be applied.
     *
     * @param response The response.
     * @return The tag groups.
     */
    @NonNull
    private Map<String, Set<String>> generateTags(Map<String, Set<String>> requestedTags, TagGroupResponse response, long cacheTime) {
        Map<String, Set<String>> currentTags = new HashMap<>(response.tags);

        this.historian.applyLocalData(currentTags, cacheTime - getPreferLocalTagDataTime());

        // Override the device tags if needed
        if (requestedTags.containsKey("device") && airshipChannel.getChannelTagRegistrationEnabled()) {
            currentTags.put("device", airshipChannel.getTags());
        }

        // Only return the requested tags if available
        return TagGroupUtils.intersect(requestedTags, currentTags);
    }

    /**
     * Refreshes the cache.
     *
     * @param tags The requested tags.
     * @param cachedResponse The cached response.
     */
    private void refreshCache(Map<String, Set<String>> tags, @Nullable TagGroupResponse cachedResponse) throws Exception {
        Map<String, Set<String>> requestTags;
        if (requestTagsCallback != null) {
            requestTags = TagGroupUtils.union(tags, requestTagsCallback.getTags());
        } else {
            requestTags = tags;
        }

        // Only use the cached response if it the requested tags are the same
        if (cachedResponse != null && !requestTags.equals(getCachedRequestTags())) {
            cachedResponse = null;
        }

        TagGroupResponse response = client.lookupTagGroups(airshipChannel.getId(), UAirship.shared().getPlatformType(), requestTags, cachedResponse);

        if (response == null) {
            Logger.error("Failed to refresh the cache.");
            return;
        }

        if (response.status != 200) {
            Logger.error("Failed to refresh the cache. Status: %s", response);
            return;
        }

        Logger.verbose("Refreshed tag group with response: %s", response);
        setCachedResponse(response, requestTags);
    }

}
