package com.urbanairship.channel;

import com.urbanairship.Logger;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SubscriptionListRegistrar {

    private final List<SubscriptionListListener> listeners = new CopyOnWriteArrayList<>();
    private final Object idLock = new Object();
    private final SubscriptionListApiClient apiClient;
    private final PendingSubscriptionListMutationStore mutationStore;

    private String identifier;

    SubscriptionListRegistrar(SubscriptionListApiClient apiClient, PendingSubscriptionListMutationStore mutationStore) {
        this.apiClient = apiClient;
        this.mutationStore = mutationStore;
        this.mutationStore.collapseAndSaveMutations();
    }

    void addPendingMutations(@NonNull List<SubscriptionListMutation> mutations) {
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
        while (true) {
            List<SubscriptionListMutation> mutations;
            String uploadIdentifier;
            synchronized (idLock) {
                mutationStore.collapseAndSaveMutations();
                mutations = mutationStore.peek();
                uploadIdentifier = identifier;
            }

            if (UAStringUtil.isEmpty(uploadIdentifier) || mutations == null || mutations.isEmpty()) {
                return true;
            }

            Response<Void> response;
            try {
                response = apiClient.updateSubscriptionLists(uploadIdentifier, mutations);
            } catch (RequestException e) {
                Logger.error(e, "Failed to update subscription lists!");
                return false;
            }

            Logger.debug("Subscription lists update response: %s", response);
            if (response.isServerError() || response.isTooManyRequestsError()) {
                return false;
            }

            if (response.isClientError()) {
                Logger.error("Dropping subscription list update %s due to error: %d message: %s",
                        mutations, response.getStatus(), response.getResponseBody());
            } else {
                for (SubscriptionListListener listener : listeners) {
                    listener.onSubscriptionListMutationUploaded(uploadIdentifier, mutations);
                }
            }

            synchronized (idLock) {
                if (mutations.equals(mutationStore.peek()) && uploadIdentifier.equals(identifier)) {
                    mutationStore.pop();
                }
            }
        }
    }

    Set<String> fetchChannelSubscriptionLists() {
        String fetchIdentifier;
        synchronized (idLock) {
            fetchIdentifier = identifier;
        }

        Response<Set<String>> response;
        try {
            response = apiClient.getSubscriptionLists(fetchIdentifier);
        } catch (RequestException e) {
            Logger.error("Failed to fetch channel subscription lists!");
            return Collections.emptySet();
        }

        Logger.verbose("Subscription list fetched: %s", response);

        if (response.isSuccessful()) {
            return response.getResult();
        } else {
            Logger.error("Failed to fetch channel subscription lists! error: %d message: %s",
                    response.getStatus(), response.getResponseBody());
            return Collections.emptySet();
        }
    }

    void clearPendingMutations() {
        mutationStore.removeAll();
    }

    List<SubscriptionListMutation> getPendingMutations() {
        List<SubscriptionListMutation> combined = new ArrayList<>();
        for (List<SubscriptionListMutation> mutations : mutationStore.getList()) {
            combined.addAll(mutations);
        }
        return combined;
    }

    void addSubscriptionListListener(@NonNull SubscriptionListListener listener) {
        listeners.add(listener);
    }
}
