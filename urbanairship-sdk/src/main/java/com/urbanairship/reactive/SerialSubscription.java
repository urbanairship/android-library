/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.reactive;

/**
 * Subscription for tracking a single subscription that can be
 * set atomically. Useful for situations in which a subscription
 * must be referenced within the scope that creates it.
 */
public class SerialSubscription extends Subscription {

    private Subscription subscription;

    /**
     * Sets the subscription.
     * @param subscription
     */
    public synchronized void setSubscription(Subscription subscription) {
        if (!isCancelled()) {
            this.subscription = subscription;
        } else {
            subscription.cancel();
        }
    }

    @Override
    public synchronized void cancel() {
        Subscription currentSubscription = subscription;
        if (!isCancelled()) {
            super.cancel();
            this.subscription = null;
        }

        if (currentSubscription != null) {
            currentSubscription.cancel();
        }
    }
}
