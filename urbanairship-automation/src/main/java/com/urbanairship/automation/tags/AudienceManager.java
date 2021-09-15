/* Copyright Airship and Contributors */

package com.urbanairship.automation.tags;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.AttributeMutation;
import com.urbanairship.channel.TagGroupsMutation;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.contacts.Contact;
import com.urbanairship.contacts.ContactChangeListener;
import com.urbanairship.util.Clock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

/**
 * In-App Automation helper class that manages tag group audience data.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class AudienceManager  {
    // Device tag group
    private static final String DEVICE_GROUP = "device";

    // Data store keys
    private static final String PREFER_LOCAL_DATA_TIME_KEY = "com.urbanairship.iam.tags.TAG_PREFER_LOCAL_DATA_TIME";

    private static final String ENABLED_KEY = "com.urbanairship.iam.tags.FETCH_ENABLED";

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
    private final AudienceHistorian historian;
    private final AirshipChannel airshipChannel;
    private final TagGroupLookupApiClient client;
    private final Clock clock;
    private final Contact contact;
    private final TagGroupLookupResponseCache cache;

    private RequestTagsCallback requestTagsCallback;

    /**
     * Default constructor.
     *
     * @param runtimeConfig The runtime config.
     * @param airshipChannel The Airship channel.
     * @param contact The contact.
     * @param dataStore The preference data store.
     */
    public AudienceManager(@NonNull AirshipRuntimeConfig runtimeConfig,
                           @NonNull AirshipChannel airshipChannel,
                           @NonNull Contact contact,
                           @NonNull PreferenceDataStore dataStore) {
        this(new TagGroupLookupApiClient(runtimeConfig), airshipChannel, contact,
                new TagGroupLookupResponseCache(dataStore, Clock.DEFAULT_CLOCK), new AudienceHistorian(
                        airshipChannel, contact, Clock.DEFAULT_CLOCK),
                dataStore, Clock.DEFAULT_CLOCK);
    }

    @VisibleForTesting
    AudienceManager(@NonNull TagGroupLookupApiClient client, @NonNull AirshipChannel airshipChannel,
                    @NonNull Contact contact, @NonNull final TagGroupLookupResponseCache cache,
                    @NonNull AudienceHistorian historian, @NonNull PreferenceDataStore dataStore, @NonNull Clock clock) {
        this.client = client;
        this.airshipChannel = airshipChannel;
        this.contact = contact;
        this.cache = cache;
        this.historian = historian;
        this.dataStore = dataStore;
        this.clock = clock;

        this.historian.init();

        contact.addContactChangeListener(new ContactChangeListener() {
            @Override
            public void onContactChanged() {
                cache.clear();
            }
        });
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
        cache.setMaxAgeTime(duration, unit);
    }

    /**
     * Gets the max cache age in milliseconds.
     *
     * @return The max cache age in milliseconds.
     */
    public long getCacheMaxAgeTimeMilliseconds() {
        return cache.getMaxAgeTimeMilliseconds();
    }

    /**
     * Sets the cache stale read time. If the cache fails to refresh, the cache will still
     * be used if it's newer than the specified duration.
     *
     * @param duration The duration.
     * @param unit The time unit.
     */
    public void setCacheStaleReadTime(@IntRange(from = TagGroupLookupResponseCache.MIN_MAX_AGE_TIME_MS) long duration, @NonNull TimeUnit unit) {
        cache.setStaleReadTime(duration, unit);
    }

    /**
     * Gets the cache stale age read time in milliseconds.
     *
     * @return The cache stale age read time in milliseconds.
     */
    public long getCacheStaleReadTimeMilliseconds() {
        return cache.getStaleReadTimeMilliseconds();
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
     * @return The prefer local tag data time in milliseconds.
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
        if (TagGroupUtils.containsAll(cache.getRequestTags(), tags)) {
            cachedResponse = cache.getResponse();
        }

        long cacheCreateDate = cache.getCreateDate();

        if (cachedResponse != null && cacheMaxAgeTime > clock.currentTimeMillis() - cacheCreateDate) {
            return new TagGroupResult(true, generateTags(tags, cachedResponse, cacheCreateDate));
        }

        // Refresh the cache
        try {
            refreshCache(tags, cachedResponse);
            cachedResponse = cache.getResponse();
            cacheCreateDate = cache.getCreateDate();
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
     * Helper method to generate the tags from the response. Local cache data will be applied.
     *
     * @param response The response.
     * @return The tag groups.
     */
    @NonNull
    private Map<String, Set<String>> generateTags(Map<String, Set<String>> requestedTags, TagGroupResponse response, long cacheTime) {
        Map<String, Set<String>> currentTags = new HashMap<>(response.tags);

        List<TagGroupsMutation> mutations = getTagGroupOverrides(cacheTime - getPreferLocalTagDataTime());
        for (TagGroupsMutation mutation : mutations) {
            mutation.apply(currentTags);
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
        if (cachedResponse != null && !requestTags.equals(cache.getRequestTags())) {
            cachedResponse = null;
        }

        TagGroupResponse response = client.lookupTagGroups(airshipChannel.getId(), requestTags, cachedResponse);

        if (response == null) {
            Logger.error("Failed to refresh the cache.");
            return;
        }

        if (response.status != 200) {
            Logger.error("Failed to refresh the cache. Status: %s", response);
            return;
        }

        Logger.verbose("Refreshed tag group with response: %s", response);
        cache.setResponse(response, requestTags);
    }

    /**
     * Gets any tag overrides - tags that are pending or tags that have been sent up since
     * {@link #getPreferLocalTagDataTime()}.
     *
     * @return A list of tag mutation overrides.
     */
    @NonNull
    public List<TagGroupsMutation> getTagOverrides() {
        return getTagGroupOverrides(clock.currentTimeMillis() - getPreferLocalTagDataTime());
    }

    @NonNull
    private List<TagGroupsMutation> getTagGroupOverrides(long since) {
        List<TagGroupsMutation> mutations = new ArrayList<>();
        mutations.addAll(historian.getTagGroupHistory(since));

        // Pending Tags
        mutations.addAll(contact.getPendingTagUpdates());
        mutations.addAll(airshipChannel.getPendingTagUpdates());

        // Channel tags
        if (airshipChannel.getChannelTagRegistrationEnabled()) {
            mutations.add(TagGroupsMutation.newSetTagsMutation(DEVICE_GROUP, airshipChannel.getTags()));
        }

        return TagGroupsMutation.collapseMutations(mutations);
    }

    /**
     * Gets any attribute overrides - attributes that are pending or attributes that have been sent up since
     * {@link #DEFAULT_PREFER_LOCAL_DATA_TIME_MS}.
     *
     * @return A list of tag mutation overrides.
     */
    @NonNull
    public List<AttributeMutation> getAttributeOverrides() {
        List<AttributeMutation> mutations = new ArrayList<>();
        mutations.addAll(historian.getAttributeHistory(clock.currentTimeMillis() - DEFAULT_PREFER_LOCAL_DATA_TIME_MS));

        // Pending
        mutations.addAll(contact.getPendingAttributeUpdates());
        mutations.addAll(airshipChannel.getPendingAttributeUpdates());

        return AttributeMutation.collapseMutations(mutations);
    }
}
