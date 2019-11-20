/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;
import com.urbanairship.util.UAHttpStatusUtil;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * Tag group registrar.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class TagGroupRegistrar {

    /**
     * Tag group registrar listener.
     */
    public interface Listener {

        /**
         * Called when a mutation is uploaded.
         *
         * @param mutation The mutation.
         */
        void onMutationUploaded(@NonNull TagGroupsMutation mutation);

    }

    /**
     * Key for storing the pending tag group mutations in the {@link PreferenceDataStore}.
     */
    private static final String NAMED_USER_PENDING_TAG_GROUP_MUTATIONS_KEY = "com.urbanairship.nameduser.PENDING_TAG_GROUP_MUTATIONS_KEY";

    /**
     * Key for storing the pending tag group mutations in the {@link PreferenceDataStore}.
     */
    private static final String CHANNEL_PENDING_TAG_GROUP_MUTATIONS_KEY = "com.urbanairship.push.PENDING_TAG_GROUP_MUTATIONS";

    // Old keys to migrate
    private static final String CHANNEL_PENDING_ADD_TAG_GROUPS_KEY = "com.urbanairship.push.PENDING_ADD_TAG_GROUPS";
    private static final String CHANNEL_PENDING_REMOVE_TAG_GROUPS_KEY = "com.urbanairship.push.PENDING_REMOVE_TAG_GROUPS";
    private static final String NAMED_USER_PENDING_ADD_TAG_GROUPS_KEY = "com.urbanairship.nameduser.PENDING_ADD_TAG_GROUPS_KEY";
    private static final String NAMED_USER_PENDING_REMOVE_TAG_GROUPS_KEY = "com.urbanairship.nameduser.PENDING_REMOVE_TAG_GROUPS_KEY";

    /**
     * Named user type.
     */
    public static final int NAMED_USER = 1;

    /**
     * Channel type.
     */
    public static final int CHANNEL = 0;

    @IntDef({ NAMED_USER, CHANNEL })
    @Retention(RetentionPolicy.SOURCE)
    public @interface TagGroupType {}

    private final TagGroupApiClient client;
    private final PendingTagGroupMutationStore namedUserStore;
    private final PendingTagGroupMutationStore channelStore;

    private final List<Listener> listeners = new ArrayList<>();

    /**
     * Default constructor.
     *
     * @param platform The platform.
     * @param configOptions The config options.
     * @param dataStore The data store.
     */
    public TagGroupRegistrar(@UAirship.Platform int platform, @NonNull AirshipConfigOptions configOptions, @NonNull PreferenceDataStore dataStore) {
        this(new TagGroupApiClient(platform, configOptions),
                new PendingTagGroupMutationStore(dataStore, NAMED_USER_PENDING_TAG_GROUP_MUTATIONS_KEY),
                new PendingTagGroupMutationStore(dataStore, CHANNEL_PENDING_TAG_GROUP_MUTATIONS_KEY));
    }

    @VisibleForTesting
    TagGroupRegistrar(@NonNull TagGroupApiClient client, @NonNull PendingTagGroupMutationStore channelStore, @NonNull PendingTagGroupMutationStore namedUserStore) {
        this.namedUserStore = namedUserStore;
        this.channelStore = channelStore;
        this.client = client;
    }

    /**
     * Adds mutations for the specified type.
     *
     * @param type The mutation type.
     * @param mutations The mutations.
     */
    public void addMutations(@TagGroupType int type, @NonNull List<TagGroupsMutation> mutations) {
        getMutationStore(type).add(mutations);
    }

    /**
     * Clears mutations for the specified type.
     *
     * @param type The mutation type.
     */
    public void clearMutations(@TagGroupType int type) {
        getMutationStore(type).clear();
    }

    /**
     * Uploads mutations for the specified type and identifier.
     *
     * @param type The type.
     * @param identifier The identifier. Either the channel ID for {@link #CHANNEL} type, or the named user ID.
     * @return {@code true} if uploads are complete, otherwise {@code false}.
     */
    @WorkerThread
    public boolean uploadMutations(@TagGroupType int type, @NonNull String identifier) {
        PendingTagGroupMutationStore mutationStore = getMutationStore(type);

        while (true) {
            // Collapse mutations before we try to send any updates
            mutationStore.collapseMutations();

            TagGroupsMutation mutation = mutationStore.peek();
            if (mutation == null) {
                break;
            }

            Response response = client.updateTagGroups(type, identifier, mutation);

            // No response, 5xx, or 429
            if (response == null || UAHttpStatusUtil.inServerErrorRange(response.getStatus()) || response.getStatus() == Response.HTTP_TOO_MANY_REQUESTS) {
                Logger.debug("Failed to update tag groups, will retry later.");
                return false;
            }

            notifyListeners(mutation);
            mutationStore.pop();

            int status = response.getStatus();
            Logger.debug("Update tag groups finished with status: %s", status);
        }

        return true;
    }

    /**
     * Adds a listener.
     *
     * @param listener The listener.
     */
    public void addListener(@NonNull Listener listener) {
        synchronized (listeners) {
            this.listeners.add(listener);
        }
    }

    /**
     * Removes a listener.
     *
     * @param listener The listener.
     */
    public void removeListener(@NonNull Listener listener) {
        synchronized (listeners) {
            this.listeners.remove(listener);
        }
    }

    private void notifyListeners(@NonNull TagGroupsMutation mutation) {
        synchronized (listeners) {
            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onMutationUploaded(mutation);
            }
        }
    }

    /**
     * Performs any data store migrations.
     */
    public void migrateKeys() {
        channelStore.migrateTagGroups(CHANNEL_PENDING_ADD_TAG_GROUPS_KEY, CHANNEL_PENDING_REMOVE_TAG_GROUPS_KEY);
        namedUserStore.migrateTagGroups(NAMED_USER_PENDING_ADD_TAG_GROUPS_KEY, NAMED_USER_PENDING_REMOVE_TAG_GROUPS_KEY);
    }

    /**
     * Gets the pending tag groups mutations.
     *
     * @param type The tag group type.
     * @return The pending tag groups mutations.
     */
    @NonNull
    public List<TagGroupsMutation> getPendingMutations(@TagGroupType int type) {
        return getMutationStore(type).getMutations();
    }

    /**
     * Helper method to get the mutation store for the specified type.
     *
     * @param type The type.
     * @return The mutation store for the type.
     */
    @NonNull
    private PendingTagGroupMutationStore getMutationStore(@TagGroupType int type) {
        switch (type) {
            case CHANNEL:
                return channelStore;
            case NAMED_USER:
                return namedUserStore;
        }
        throw new IllegalArgumentException("Invalid type");
    }

}