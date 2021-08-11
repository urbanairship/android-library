/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.Logger;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class AttributeRegistrar {

    private final Object idLock = new Object();
    private final AttributeApiClient apiClient;
    private final PendingAttributeMutationStore mutationStore;
    private final List<AttributeListener> attributeListeners = new CopyOnWriteArrayList<>();

    private String identifier;

    AttributeRegistrar(AttributeApiClient apiClient, PendingAttributeMutationStore mutationStore) {
        this.apiClient = apiClient;
        this.mutationStore = mutationStore;
    }

    void addPendingMutations(@NonNull List<AttributeMutation> mutations) {
        mutationStore.add(mutations);
    }

    void setId(String identifier, boolean clearPendingOnIdChange) {
        synchronized (idLock) {
            if (clearPendingOnIdChange && !UAStringUtil.equals(this.identifier, identifier)) {
                mutationStore.removeAll();
            }
            this.identifier = identifier;
        }
    }

    boolean uploadPendingMutations() {
        List<AttributeMutation> mutations;
        String identifier;
        synchronized (idLock) {
            mutationStore.collapseAndSaveMutations();
            mutations = mutationStore.peek();
            identifier = this.identifier;
        }

        if (identifier == null || mutations == null || mutations.isEmpty()) {
            return true;
        }

        Response<Void> response;
        try {
            response = apiClient.updateAttributes(identifier, mutations);
        } catch (RequestException e) {
            Logger.debug(e, "Failed to update attributes");
            return false;
        }

        Logger.debug("Updated attributes response: %s", response);
        if (response.isServerError() || response.isTooManyRequestsError()) {
            return false;
        }

        if (response.isClientError()) {
            Logger.error("Dropping attributes %s due to error: %s message: %s", mutations, response.getStatus(), response.getResponseBody());
        } else {
            for (AttributeListener listener : attributeListeners) {
                listener.onAttributeMutationsUploaded(mutations);
            }
        }

        synchronized (idLock) {
            if (mutations.equals(mutationStore.peek()) && identifier.equals(this.identifier)) {
                mutationStore.pop();
            }
        }

        return true;
    }

    void clearPendingMutations() {
        mutationStore.removeAll();
    }

    void addAttributeListener(@NonNull AttributeListener listener) {
        attributeListeners.add(listener);
    }

    List<AttributeMutation> getPendingMutations() {
        List<AttributeMutation> combined = new ArrayList<>();
        for (List<AttributeMutation> mutations : mutationStore.getList()) {
            combined.addAll(mutations);
        }
        return combined;
    }

}
