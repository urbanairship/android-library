/* Copyright Airship and Contributors */

package com.urbanairship.contacts;

import com.urbanairship.Logger;
import com.urbanairship.util.Clock;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Subscription list editor. See {@link Contact#editSubscriptionLists()}.
 */
public abstract class ScopedSubscriptionListEditor {
    private final List<ScopedSubscriptionListMutation> mutations = new ArrayList<>();
    private final Clock clock;

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected ScopedSubscriptionListEditor(Clock clock) {
        this.clock = clock;
    }

    /**
     * Subscribe to a list.
     *
     * @param subscriptionListId The subscription list ID.
     * @param scope Defines the channel types that the change applies to.
     * @return The {@code SubscriptionListEditor} instance.
     */
    @NonNull
    public ScopedSubscriptionListEditor subscribe(@NonNull String subscriptionListId, @NonNull Scope scope) {
        subscriptionListId = subscriptionListId.trim();
        if (UAStringUtil.isEmpty(subscriptionListId)) {
            Logger.error("The subscription list ID must not be null or empty." );
            return this;
        }

        mutations.add(ScopedSubscriptionListMutation.newSubscribeMutation(subscriptionListId, scope, clock.currentTimeMillis()));
        return this;
    }

    /**
     * Subscribes from a set of lists.
     *
     * @param subscriptionListIds A {@code Set} of list IDs.
     * @param scope Defines the channel types that the change applies to.
     * @return The {@code SubscriptionListEditor} instance.
     */
    @NonNull
    public ScopedSubscriptionListEditor subscribe(Set<String> subscriptionListIds, @NonNull Scope scope) {
        for (String id : subscriptionListIds) {
            subscribe(id, scope);
        }

        return this;
    }


    /**
     * Unsubscribe from a list.
     *
     * @param subscriptionListId The subscription list ID.
     * @param scope Defines the channel types that the change applies to.
     * @return The {@code SubscriptionListEditor} instance.
     */
    @NonNull
    public ScopedSubscriptionListEditor unsubscribe(String subscriptionListId, @NonNull Scope scope) {
        subscriptionListId = subscriptionListId.trim();
        if (UAStringUtil.isEmpty(subscriptionListId)) {
            Logger.error("The subscription list ID must not be null or empty." );
            return this;
        }

        mutations.add(ScopedSubscriptionListMutation.newUnsubscribeMutation(subscriptionListId, scope, clock.currentTimeMillis()));
        return this;
    }

    /**
     * Unsubscribes from a set of lists.
     *
     * @param subscriptionListIds A {@code Set} of list IDs.
     * @param scope Defines the channel types that the change applies to.
     * @return The {@code SubscriptionListEditor} instance.
     */
    @NonNull
    public ScopedSubscriptionListEditor unsubscribe(Set<String> subscriptionListIds, @NonNull Scope scope) {
        for (String id : subscriptionListIds) {
            unsubscribe(id, scope);
        }

        return this;
    }

    /**
     * Internal helper that uses a boolean flag to indicate whether to subscribe or unsubscribe.
     *
     * @param subscriptionListId The ID of the list to subscribe to or unsubscribe from.
     * @param scopes Defines the set of channel types that the change applies to.
     * @param isSubscribe {@code true} to subscribe or {@code false} to unsubscribe.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public ScopedSubscriptionListEditor mutate(@NonNull String subscriptionListId, @NonNull Set<Scope> scopes, boolean isSubscribe) {
        for (Scope scope : scopes) {
            if (isSubscribe) {
                subscribe(subscriptionListId, scope);
            } else {
                unsubscribe(subscriptionListId, scope);
            }
        }
        return this;
    }

    /**
     * Apply the subscription list changes.
     */
    public void apply() {
        onApply(ScopedSubscriptionListMutation.collapseMutations(mutations));
    }

    /**
     * Called when apply is called.
     *
     * @param mutations The list of mutations to be applied.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected abstract void onApply(@NonNull List<ScopedSubscriptionListMutation> mutations);
}
