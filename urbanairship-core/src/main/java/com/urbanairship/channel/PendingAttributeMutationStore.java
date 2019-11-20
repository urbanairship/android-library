/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class PendingAttributeMutationStore {
    private final PreferenceDataStore dataStore;
    private final String storeKey;

    /**
     * Default constructor.
     *
     * @param dataStore The preference data store.
     * @param storeKey The store key.
     */
    PendingAttributeMutationStore(PreferenceDataStore dataStore, String storeKey) {
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
     * Adds new pending attribute mutations to the end of the store.
     *
     * @param pendingAttributeMutations A list of pending attribute mutations.
     */
    void add(List<PendingAttributeMutation> pendingAttributeMutations) {
        synchronized (this) {
            List<List<PendingAttributeMutation>> allMutations = getMutations();
            allMutations.add(pendingAttributeMutations);
            dataStore.put(storeKey, JsonValue.wrapOpt(allMutations));
        }
    }

    /**
     * Pops the next pending attribute mutations off the store.
     *
     * @return The next attributes mutation or {@code null} if no mutations exist.
     */
    @Nullable
    List<PendingAttributeMutation> pop() {
        synchronized (this) {
            List<List<PendingAttributeMutation>> allMutations = getMutations();

            if (peek() == null) {
                return null;
            }

            List<PendingAttributeMutation> mutations = allMutations.remove(0);

            dataStore.put(storeKey, JsonValue.wrapOpt(allMutations));
            return mutations;
        }
    }

    /**
     * Peeks the top mutation.
     *
     * @return The top attributes mutation or {@code null} if no mutations exist.
     */
    @Nullable
    List<PendingAttributeMutation> peek() {
        synchronized (this) {
            List<List<PendingAttributeMutation>> allMutations = getMutations();

            if (allMutations.isEmpty()) {
                return null;
            }

            if (allMutations.get(0).isEmpty()) {
                return null;
            }

            return allMutations.get(0);
        }
    }

    /**
     * Collapses a list of mutations down to a single collection of mutations.
     */
    void collapseAndSaveMutations() {
        synchronized (this) {
            List<List<PendingAttributeMutation>> allMutations = getMutations();

            List<PendingAttributeMutation> combined = new ArrayList<>();
            for (List<PendingAttributeMutation> mutations : allMutations) {
                combined.addAll(mutations);
            }

            List<PendingAttributeMutation> collapsedMutation = PendingAttributeMutation.collapseMutations(combined);

            allMutations.clear();
            allMutations.add(collapsedMutation);

            dataStore.put(storeKey, JsonValue.wrapOpt(allMutations));
        }
    }

    /**
     * Gets the attribute mutations.
     *
     * @return A list of all the attribute mutations.
     */
    @NonNull
    List<List<PendingAttributeMutation>> getMutations() {
        JsonList jsonList =  dataStore.getJsonValue(storeKey).optList();
        List<List<PendingAttributeMutation>> allMutations = new ArrayList<>();

        for (JsonValue value : jsonList) {
            allMutations.add(PendingAttributeMutation.fromJsonList(value.optList()));
        }
        return allMutations;
    }
}
