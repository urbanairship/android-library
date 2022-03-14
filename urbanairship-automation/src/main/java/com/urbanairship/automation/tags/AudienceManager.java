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
    private final Clock clock;
    private final Contact contact;

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
        this(airshipChannel, contact, new AudienceHistorian(
                        airshipChannel, contact, Clock.DEFAULT_CLOCK),
                dataStore, Clock.DEFAULT_CLOCK);
    }

    @VisibleForTesting
    AudienceManager(@NonNull AirshipChannel airshipChannel,
                    @NonNull Contact contact,
                    @NonNull AudienceHistorian historian, @NonNull PreferenceDataStore dataStore, @NonNull Clock clock) {
        this.airshipChannel = airshipChannel;
        this.contact = contact;
        this.historian = historian;
        this.dataStore = dataStore;
        this.clock = clock;

        this.historian.init();
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
     * Gets any tag overrides - tags that are pending or tags that have been sent up since
     * {@link #DEFAULT_PREFER_LOCAL_DATA_TIME_MS}.
     *
     * @return A list of tag mutation overrides.
     */
    @NonNull
    public List<TagGroupsMutation> getTagOverrides() {
        return getTagGroupOverrides(clock.currentTimeMillis() - DEFAULT_PREFER_LOCAL_DATA_TIME_MS);
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
