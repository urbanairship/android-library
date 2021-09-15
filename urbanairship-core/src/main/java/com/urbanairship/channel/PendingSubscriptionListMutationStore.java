package com.urbanairship.channel;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.JsonDataStoreQueue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;

public class PendingSubscriptionListMutationStore extends JsonDataStoreQueue<List<SubscriptionListMutation>> {

    /**
     * Default constructor.
     *
     * @param dataStore The preference data store.
     * @param storeKey The store key.
     */
    public PendingSubscriptionListMutationStore(@NonNull PreferenceDataStore dataStore, @NonNull String storeKey) {
        super(dataStore, storeKey,
            new Function<List<SubscriptionListMutation>, JsonSerializable>() {
                @Override
                public JsonSerializable apply(List<SubscriptionListMutation> input) {
                    return JsonValue.wrapOpt(input);
                }
            },
            new Function<JsonValue, List<SubscriptionListMutation>>() {
                @Override
                public List<SubscriptionListMutation> apply(JsonValue input) {
                    return SubscriptionListMutation.fromJsonList(input.optList());
                }
            }
        );
    }

    public void collapseAndSaveMutations() {
        apply(new Function<List<List<SubscriptionListMutation>>, List<List<SubscriptionListMutation>>>() {
            @Override
            public List<List<SubscriptionListMutation>> apply(List<List<SubscriptionListMutation>> input) {
                List<SubscriptionListMutation> combined = new ArrayList<>();
                for (List<SubscriptionListMutation> mutations : input) {
                    combined.addAll(mutations);
                }

                if (combined.isEmpty()) {
                    return Collections.emptyList();
                }

                return Collections.singletonList(SubscriptionListMutation.collapseMutations(combined));
            }
        });
    }
}
