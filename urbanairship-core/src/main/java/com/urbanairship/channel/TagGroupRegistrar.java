package com.urbanairship.channel;

import com.urbanairship.Logger;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.util.UAStringUtil;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class TagGroupRegistrar {

    private final List<TagGroupListener> tagGroupListeners = new CopyOnWriteArrayList<>();
    private final Object idLock = new Object();
    private final TagGroupApiClient apiClient;
    private final PendingTagGroupMutationStore pendingTagGroupMutationStore;

    private String identifier;

    TagGroupRegistrar(TagGroupApiClient apiClient, PendingTagGroupMutationStore pendingTagGroupMutationStore) {
        this.apiClient = apiClient;
        this.pendingTagGroupMutationStore = pendingTagGroupMutationStore;
        this.pendingTagGroupMutationStore.collapseAndSaveMutations();
    }

    void addPendingMutations(@NonNull List<TagGroupsMutation> mutations) {
        pendingTagGroupMutationStore.addAll(mutations);
    }

    void setId(String identifier, boolean clearPendingOnIdChange) {
        synchronized (idLock) {
            if (clearPendingOnIdChange && !UAStringUtil.equals(this.identifier, identifier)) {
                pendingTagGroupMutationStore.removeAll();
            }
            this.identifier = identifier;
        }
    }

    boolean uploadPendingMutations() {
        while (true) {
            TagGroupsMutation mutation;
            String identifier;
            synchronized (idLock) {
                pendingTagGroupMutationStore.collapseAndSaveMutations();
                mutation = pendingTagGroupMutationStore.peek();
                identifier = this.identifier;
            }

            if (UAStringUtil.isEmpty(identifier) || mutation == null) {
                return true;
            }

            Response<Void> response;
            try {
                response = apiClient.updateTags(identifier, mutation);
            } catch (RequestException e) {
                Logger.debug(e, "Failed to update tag groups");
                return false;
            }

            Logger.debug("Updated tag group response: %s", response);
            if (response.isServerError() || response.isTooManyRequestsError()) {
                return false;
            }

            if (response.isClientError()) {
                Logger.error("Dropping tag group update %s due to error: %s message: %s", mutation, response.getStatus(), response.getResponseBody());
            } else {
                for (TagGroupListener listener : tagGroupListeners) {
                    listener.onTagGroupsMutationUploaded(Collections.singletonList(mutation));
                }
            }

            synchronized (idLock) {
                if (mutation.equals(pendingTagGroupMutationStore.peek()) && identifier.equals(this.identifier)) {
                    pendingTagGroupMutationStore.pop();
                }
            }
        }
    }

    void clearPendingMutations() {
        pendingTagGroupMutationStore.removeAll();
    }

    List<TagGroupsMutation> getPendingMutations() {
        return pendingTagGroupMutationStore.getList();
    }

    void addTagGroupListener(@NonNull TagGroupListener listener) {
        this.tagGroupListeners.add(listener);
    }
}
