package com.urbanairship.channel;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Subscription list update upload listener.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface SubscriptionListListener {

    /**
     * Called when a subscription list update is uploaded.
     *
     * @param identifier The identifier (a Channel ID).
     * @param subscriptionListMutation The subscription list mutation.
     */
    void onSubscriptionListMutationUploaded(@NonNull String identifier, @NonNull List<SubscriptionListMutation> mutations);
}
