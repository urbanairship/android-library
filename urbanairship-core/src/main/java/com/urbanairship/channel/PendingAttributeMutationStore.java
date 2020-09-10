/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.JsonDataStoreQueue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.arch.core.util.Function;

class PendingAttributeMutationStore extends JsonDataStoreQueue<List<AttributeMutation>> {

    /**
     * Default constructor.
     *
     * @param dataStore The preference data store.
     * @param storeKey The store key.
     */
    PendingAttributeMutationStore(PreferenceDataStore dataStore, String storeKey) {
        super(dataStore, storeKey, new Function<List<AttributeMutation>, JsonSerializable>() {
            @Override
            public JsonSerializable apply(List<AttributeMutation> input) {
                return JsonValue.wrapOpt(input);
            }
        }, new Function<JsonValue, List<AttributeMutation>>() {
            @Override
            public List<AttributeMutation> apply(JsonValue input) {
                return AttributeMutation.fromJsonList(input.optList());
            }
        });
    }

    /**
     * Collapses a list of mutations down to a single collection of mutations.
     */
    void collapseAndSaveMutations() {
        apply(new Function<List<List<AttributeMutation>>, List<List<AttributeMutation>>>() {
            @Override
            public List<List<AttributeMutation>> apply(List<List<AttributeMutation>> input) {
                List<AttributeMutation> combined = new ArrayList<>();
                for (List<AttributeMutation> mutations : input) {
                    combined.addAll(mutations);
                }

                if (combined.isEmpty()) {
                    return Collections.emptyList();
                }

                return Collections.singletonList(AttributeMutation.collapseMutations(combined));
            }
        });
    }
}
