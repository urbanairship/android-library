/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.reactive;

import android.support.annotation.RestrictTo;

/**
 * Concrete convenience implementation of Observer.
 * @param <T> The type of the value under observation.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Subscriber<T> implements Observer<T> {

    @Override
    public void onNext(T value) {
    }

    @Override
    public void onCompleted() {
    }

    @Override
    public void onError(Exception e) {
    }
}
