/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.Logger;
import com.urbanairship.util.Clock;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Subscription list editor. See {@link AirshipChannel#editSubscriptionLists()}.
 */
public abstract class SubscriptionListEditor {

    private final List<SubscriptionListMutation> mutations = new ArrayList<>();
    private final Clock clock;

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected SubscriptionListEditor(Clock clock) {
        this.clock = clock;
    }

    /**
     * Subscribe to a list.
     *
     * @param subscriptionListId The ID of the list to subscribe to.
     * @return The {@code SubscriptionListEditor} instance.
     */
    @NonNull
    public SubscriptionListEditor subscribe(@NonNull String subscriptionListId) {
        subscriptionListId = subscriptionListId.trim();
        if (UAStringUtil.isEmpty(subscriptionListId)) {
            Logger.error("The subscription list ID must not be null or empty." );
            return this;
        }

        mutations.add(SubscriptionListMutation.newSubscribeMutation(subscriptionListId, clock.currentTimeMillis()));

        return this;
    }

    /**
     * Subscribe to a set of lists.
     *
     * @param subscriptionListIds A {@code Set} of list IDs to subscribe to.
     * @return The {@code SubscriptionListEditor} instance.
     */
    @NonNull
    public SubscriptionListEditor subscribe(Set<String> subscriptionListIds) {
        for (String id : subscriptionListIds) {
            subscribe(id);
        }

        return this;
    }

    /**
     * Unsubscribe from a list.
     *
     * @param subscriptionListId The ID of the list to unsubscribe from.
     * @return The {@code SubscriptionListEditor} instance.
     */
    @NonNull
    public SubscriptionListEditor unsubscribe(String subscriptionListId) {
        subscriptionListId = subscriptionListId.trim();
        if (UAStringUtil.isEmpty(subscriptionListId)) {
            Logger.error("The subscription list ID must not be null or empty." );
            return this;
        }

        mutations.add(SubscriptionListMutation.newUnsubscribeMutation(subscriptionListId, clock.currentTimeMillis()));

        return this;
    }

    /**
     * Unsubscribe from a set of lists.
     *
     * @param subscriptionListIds A {@code Set} of list IDs to unsubscribe from.
     * @return The {@code SubscriptionListEditor} instance.
     */
    @NonNull
    public SubscriptionListEditor unsubscribe(Set<String> subscriptionListIds) {
        for (String id : subscriptionListIds) {
            unsubscribe(id);
        }

        return this;
    }

    /**
     * Internal helper that uses a boolean flag to indicate whether to subscribe or unsubscribe.
     *
     * @param subscriptionListId The ID of the list to subscribe to or unsubscribe from.
     * @param isSubscribe {@code true} to subscribe or {@code false} to unsubscribe.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public SubscriptionListEditor mutate(String subscriptionListId, boolean isSubscribe) {
        return isSubscribe ? subscribe(subscriptionListId) : unsubscribe(subscriptionListId);
    }

    /**
     * Apply the subscription list changes.
     */
    public void apply() {
        onApply(SubscriptionListMutation.collapseMutations(mutations));
    }

    /**
     * Called when apply is called.
     *
     * @param collapsedMutations The collapsed list of mutations to be applied.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected abstract void onApply(@NonNull List<SubscriptionListMutation> collapsedMutations);
}
