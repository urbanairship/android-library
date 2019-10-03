/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.json.JsonValue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper class to manage pending tag group mutations.
 */
class PendingTagGroupMutationStore {

    private final PreferenceDataStore dataStore;
    private final String storeKey;

    /**
     * Default constructor.
     *
     * @param dataStore The preference data store.
     * @param storeKey The store key.
     */
    PendingTagGroupMutationStore(PreferenceDataStore dataStore, String storeKey) {
        this.dataStore = dataStore;
        this.storeKey = storeKey;
    }

    /**
     * Clears all the mutations.
     */
    void clear() {
        synchronized (this) {
            dataStore.remove(storeKey);
        }
    }

    /**
     * Adds new tag group mutations to the end of the store.
     *
     * @param tagGroupsMutations A list of tag group mutations.
     */
    void add(List<TagGroupsMutation> tagGroupsMutations) {
        synchronized (this) {
            List<TagGroupsMutation> mutations = getMutations();
            mutations.addAll(tagGroupsMutations);
            dataStore.put(storeKey, JsonValue.wrapOpt(mutations));
        }
    }

    /**
     * Pops the next tag group mutation off the store.
     *
     * @return The next tag group mutation or {@code null} if no mutations exist.
     */
    @Nullable
    TagGroupsMutation pop() {
        synchronized (this) {
            List<TagGroupsMutation> mutations = getMutations();
            if (mutations.isEmpty()) {
                return null;
            }

            TagGroupsMutation mutation = mutations.remove(0);
            dataStore.put(storeKey, JsonValue.wrapOpt(mutations));
            return mutation;
        }
    }

    /**
     * Peeks the top mutation.
     *
     * @return The top tag group mutation or {@code null} if no mutations exist.
     */
    @Nullable
    TagGroupsMutation peek() {
        synchronized (this) {
            List<TagGroupsMutation> mutations = getMutations();
            if (mutations.isEmpty()) {
                return null;
            }
            return mutations.get(0);
        }
    }

    /**
     * Collapses mutations down to a minimum set of mutations.
     */
    void collapseMutations() {
        synchronized (this) {
            List<TagGroupsMutation> mutations = getMutations();
            if (mutations.isEmpty()) {
                return;
            }

            mutations = TagGroupsMutation.collapseMutations(mutations);
            dataStore.put(storeKey, JsonValue.wrapOpt(mutations));
        }
    }

    /**
     * Converts the old tag group store to tag mutations.
     *
     * @param pendingAddTagsKey The old pending addPending tags key.
     * @param pendingRemoveTagsKey The old pending remove tags key.
     */
    void migrateTagGroups(@NonNull String pendingAddTagsKey, @NonNull String pendingRemoveTagsKey) {
        JsonValue pendingAddTags = dataStore.getJsonValue(pendingAddTagsKey);
        JsonValue pendingRemoveTags = dataStore.getJsonValue(pendingRemoveTagsKey);

        if (pendingAddTags.isNull() && pendingRemoveTags.isNull()) {
            return;
        }

        Map<String, Set<String>> addTags = TagUtils.convertToTagsMap(pendingAddTags);
        Map<String, Set<String>> removeTags = TagUtils.convertToTagsMap(pendingRemoveTags);

        TagGroupsMutation mutation = TagGroupsMutation.newAddRemoveMutation(addTags, removeTags);
        List<TagGroupsMutation> mutations = Collections.singletonList(mutation);
        mutations = TagGroupsMutation.collapseMutations(mutations);

        dataStore.put(storeKey, JsonValue.wrapOpt(mutations));

        dataStore.remove(pendingAddTagsKey);
        dataStore.remove(pendingRemoveTagsKey);
    }

    /**
     * Gets the tag group mutations.
     *
     * @return A list of all the tag group mutations.
     */
    @NonNull
    List<TagGroupsMutation> getMutations() {
        return TagGroupsMutation.fromJsonList(dataStore.getJsonValue(storeKey).optList());
    }

}
