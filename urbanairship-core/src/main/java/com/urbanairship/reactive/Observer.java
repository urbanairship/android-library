/* Copyright Airship and Contributors */

package com.urbanairship.reactive;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Interface for reactive observers. Intended for
 * Use with {@link Observable}.
 *
 * @param <T> The type of the value under observation.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface Observer<T> {

    /**
     * Notifies the observer that a new value is available
     *
     * @param value The value.
     */
    void onNext(@NonNull T value);

    /**
     * Notifies the observer that the observable has finished providing value updates.
     * No further values should be provided.
     */
    void onCompleted();

    /**
     * Notifies the observer that the observable has encountered an error.
     * No further values should be provided.
     *
     * @param e The error as an exception.
     */
    void onError(@NonNull Exception e);

}
