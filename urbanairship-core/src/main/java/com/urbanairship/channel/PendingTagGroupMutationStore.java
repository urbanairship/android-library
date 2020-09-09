/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.JsonDataStoreQueue;

import java.util.List;

import androidx.arch.core.util.Function;

/**
 * Helper class to manage pending tag group mutations.
 */
class PendingTagGroupMutationStore extends JsonDataStoreQueue<TagGroupsMutation> {

    /**
     * Default constructor.
     *
     * @param dataStore The preference data store.
     * @param storeKey The store key.
     */
    PendingTagGroupMutationStore(PreferenceDataStore dataStore, String storeKey) {
        super(dataStore, storeKey, new Function<TagGroupsMutation, JsonSerializable>() {
                    @Override
                    public JsonSerializable apply(TagGroupsMutation input) {
                        return input;
                    }
                },
                new Function<JsonValue, TagGroupsMutation>() {
                    @Override
                    public TagGroupsMutation apply(JsonValue input) {
                        return TagGroupsMutation.fromJsonValue(input);
                    }
                });
    }

    /**
     * Collapses mutations down to a minimum set of mutations.
     */
    void collapseAndSaveMutations() {
        apply(new Function<List<TagGroupsMutation>, List<TagGroupsMutation>>() {
            @Override
            public List<TagGroupsMutation> apply(List<TagGroupsMutation> input) {
                return TagGroupsMutation.collapseMutations(input);
            }
        });
    }

}
