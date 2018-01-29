/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.reactive;

import android.support.annotation.RestrictTo;

import java.util.ArrayList;
import java.util.List;

/**
 * Subscription for tracking and cancelling multiple subscriptions.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class CompoundSubscription extends Subscription {

    private List<Subscription> subscriptions;

    /**
     * Default constructor
     */
    public CompoundSubscription() {
        this.subscriptions = new ArrayList<>();
    }

    /**
     * Adds a Subscription
     * @param subscription The subscription.
     */
    public synchronized void add(Subscription subscription) {
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
     * @param subscription The subscription.
     */
    public synchronized void remove(Subscription subscription) {
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
