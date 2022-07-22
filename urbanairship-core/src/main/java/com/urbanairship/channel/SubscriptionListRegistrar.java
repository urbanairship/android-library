package com.urbanairship.channel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.Logger;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.util.CachedValue;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SubscriptionListRegistrar {

    /**
     * Max age for the channel subscription listing cache.
     */
    private static final long LOCAL_HISTORY_CACHE_LIFETIME_MS = 10 * 60 * 1000; // 10M

    private final List<SubscriptionListListener> listeners = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    private final SubscriptionListApiClient apiClient;
    private final PendingSubscriptionListMutationStore mutationStore;

    @VisibleForTesting
    @NonNull
    final List<CachedValue<SubscriptionListMutation>> localHistory = new CopyOnWriteArrayList<>();

    private String identifier;

    SubscriptionListRegistrar(SubscriptionListApiClient apiClient,
                              PendingSubscriptionListMutationStore mutationStore) {
        this.apiClient = apiClient;
        this.mutationStore = mutationStore;
        this.mutationStore.collapseAndSaveMutations();
    }

    void addPendingMutations(@NonNull List<SubscriptionListMutation> mutations) {
        mutationStore.add(mutations);
    }

    void setId(String identifier, boolean clearPendingOnIdChange) {
        synchronized (lock) {
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
            synchronized (lock) {
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

            synchronized (lock) {
                if (mutations.equals(mutationStore.peek()) && uploadIdentifier.equals(identifier)) {
                    mutationStore.pop();
                    if (response.isSuccessful()) {
                        cacheInLocalHistory(mutations);
                    }
                }
            }
        }
    }

    @Nullable
    Set<String> fetchChannelSubscriptionLists() {
        String fetchIdentifier;
        synchronized (lock) {
            fetchIdentifier = identifier;
        }

        Response<Set<String>> response;
        try {
            response = apiClient.getSubscriptionLists(fetchIdentifier);
        } catch (RequestException e) {
            Logger.error(e, "Failed to fetch channel subscription lists!");
            return null;
        }

        Logger.verbose("Channel Subscription list fetched: %s", response);

        if (response.isSuccessful()) {
            return response.getResult();
        } else {
            Logger.error("Failed to fetch channel subscription lists! error: %d message: %s",
                    response.getStatus(), response.getResponseBody());
            return null;
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

    void clearLocalHistory() {
        localHistory.clear();
    }

    void cacheInLocalHistory(@NonNull List<SubscriptionListMutation> mutations) {
        synchronized (lock) {
            for (SubscriptionListMutation mutation : mutations) {
                CachedValue<SubscriptionListMutation> cache = new CachedValue<>();
                cache.set(mutation, LOCAL_HISTORY_CACHE_LIFETIME_MS);
                localHistory.add(cache);
            }
        }
    }

    void applyLocalChanges(@NonNull Set<String> subscriptions) {
        for (CachedValue<SubscriptionListMutation> localHistoryCachedMutation : localHistory) {
            SubscriptionListMutation mutation = localHistoryCachedMutation.get();
            if (mutation != null) {
                mutation.apply(subscriptions);
            } else {
                // Remove from local history cache when it expired
                localHistory.remove(localHistoryCachedMutation);
            }
        }
    }

    void addSubscriptionListListener(@NonNull SubscriptionListListener listener) {
        listeners.add(listener);
    }

    void removeSubscriptionListListener(@NonNull SubscriptionListListener listener) {
        listeners.remove(listener);
    }
}
