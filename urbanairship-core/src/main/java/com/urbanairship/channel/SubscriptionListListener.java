package com.urbanairship.channel;

import java.util.List;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Subscription list update upload listener.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class SubscriptionListListener {

    /**
     * Called when a subscription list update is uploaded.
     *
     * @param identifier The identifier (a Channel ID).
     * @param mutations The subscription list mutations that were uploaded.
     */
    public abstract void onSubscriptionListMutationUploaded(@Nullable String identifier, @Nullable List<SubscriptionListMutation> mutations);
}
