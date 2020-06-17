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

class PendingAttributeMutationStore extends JsonDataStoreQueue<List<PendingAttributeMutation>> {

    /**
     * Default constructor.
     *
     * @param dataStore The preference data store.
     * @param storeKey The store key.
     */
    PendingAttributeMutationStore(PreferenceDataStore dataStore, String storeKey) {
        super(dataStore, storeKey, new Function<List<PendingAttributeMutation>, JsonSerializable>() {
            @Override
            public JsonSerializable apply(List<PendingAttributeMutation> input) {
                return JsonValue.wrapOpt(input);
            }
        }, new Function<JsonValue, List<PendingAttributeMutation>>() {
            @Override
            public List<PendingAttributeMutation> apply(JsonValue input) {
                return PendingAttributeMutation.fromJsonList(input.optList());
            }
        });
    }

    /**
     * Collapses a list of mutations down to a single collection of mutations.
     */
    void collapseAndSaveMutations() {
        apply(new Function<List<List<PendingAttributeMutation>>, List<List<PendingAttributeMutation>>>() {
            @Override
            public List<List<PendingAttributeMutation>> apply(List<List<PendingAttributeMutation>> input) {
                List<PendingAttributeMutation> combined = new ArrayList<>();
                for (List<PendingAttributeMutation> mutations : input) {
                    combined.addAll(mutations);
                }

                if (combined.isEmpty()) {
                    return Collections.emptyList();
                }

                return Collections.singletonList(PendingAttributeMutation.collapseMutations(combined));
            }
        });
    }
}
