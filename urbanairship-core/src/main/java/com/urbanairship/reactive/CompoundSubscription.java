/* Copyright Airship and Contributors */

package com.urbanairship.reactive;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import java.util.ArrayList;
import java.util.List;

/**
 * Subscription for tracking and cancelling multiple subscriptions.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class CompoundSubscription extends Subscription {

    private final List<Subscription> subscriptions;

    /**
     * Default constructor
     */
    public CompoundSubscription() {
        this.subscriptions = new ArrayList<>();
    }

    /**
     * Adds a Subscription
     *
     * @param subscription The subscription.
     */
    public synchronized void add(@NonNull Subscription subscription) {
        if (subscription.isCancelled()) {
            return;
        }
        if (isCancelled()) {
            subscription.cancel();
        } else {
            subscriptions.add(subscription);
        }
    }

    /**
     * Removes a Subscription
     *
     * @param subscription The subscription.
     */
    public synchronized void remove(@NonNull Subscription subscription) {
        if (!isCancelled()) {
            subscriptions.remove(subscription);
        }
    }

    @Override
    public synchronized void cancel() {
        for (Subscription subscription : new ArrayList<>(subscriptions)) {
            subscription.cancel();
            subscriptions.remove(subscription);
        }

        super.cancel();
    }

}
