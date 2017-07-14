/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;


import com.urbanairship.PreferenceDataStore;
import com.urbanairship.json.JsonValue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper class to manage pending tag group mutations.
 */
class TagGroupMutationStore {

    private final PreferenceDataStore dataStore;
    private final String storeKey;

    /**
     * Default constructor.
     *
     * @param dataStore The preference data store.
     * @param storeKey The store key.
     */
    TagGroupMutationStore(PreferenceDataStore dataStore, String storeKey) {
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
     * Adds new tag group mutations to the end of the store. Mutations will be collapsed automatically.
     *
     * @param tagGroupsMutations A list of tag group mutations.
     */
    void add(List<TagGroupsMutation> tagGroupsMutations) {
        synchronized (this) {
            List<TagGroupsMutation> mutations = getMutations();
            mutations.addAll(tagGroupsMutations);
            mutations = TagGroupsMutation.collapseMutations(mutations);
            dataStore.put(storeKey, JsonValue.wrapOpt(mutations));
        }
    }

    /**
     * Pops the next tag group mutation off the store.
     *
     * @return The next tag group mutation or {@code null} if no mutations exist.
     */
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
     * Pushes a tag group mutation on top of the store. Mutations will be collapsed automatically.
     *
     * @param mutation Tag group mutation.
     */
    void push(TagGroupsMutation mutation) {
        synchronized (this) {
            List<TagGroupsMutation> mutations = getMutations();
            mutations.add(0, mutation);
            mutations = TagGroupsMutation.collapseMutations(mutations);
            dataStore.put(storeKey, JsonValue.wrapOpt(mutations));
        }
    }

    /**
     * Converts the old tag group store to tag mutations.
     *
     * @param pendingAddTagsKey The old pending add tags key.
     * @param pendingRemoveTagsKey The old pending remove tags key.
     */
    void migrateTagGroups(String pendingAddTagsKey, String pendingRemoveTagsKey) {
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
    List<TagGroupsMutation> getMutations() {
        return TagGroupsMutation.fromJsonList(dataStore.getJsonValue(storeKey).optList());
    }
}
