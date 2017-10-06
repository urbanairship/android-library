/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.reactive;

import android.support.annotation.RestrictTo;

import com.urbanairship.Cancelable;

import java.util.ArrayList;
import java.util.List;

/**
 * Subscription for tracking and cancelling multiple cancelables.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class CompoundSubscription extends Subscription {

    private List<Cancelable> cancelables;

    /**
     * Default constructor
     */
    CompoundSubscription() {
        this.cancelables = new ArrayList<>();
    }

    /**
     * Adds a Cancelable
     * @param cancelable
     */
    public void add(Cancelable cancelable) {
        cancelables.add(cancelable);
    }

    @Override
    public synchronized boolean cancel() {
        for (Cancelable cancelable : cancelables) {
            cancelable.cancel();
        }
        return super.cancel();
    }
}
