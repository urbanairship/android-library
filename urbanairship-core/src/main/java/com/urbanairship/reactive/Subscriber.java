/* Copyright Airship and Contributors */

package com.urbanairship.reactive;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Concrete convenience implementation of Observer.
 *
 * @param <T> The type of the value under observation.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Subscriber<T> implements Observer<T> {

    @Override
    public void onNext(@NonNull T value) {
    }

    @Override
    public void onCompleted() {
    }

    @Override
    public void onError(@NonNull Exception e) {
    }

}
