/* Copyright Airship and Contributors */

package com.urbanairship.base;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@FunctionalInterface
public interface Extender<T> {
    @NonNull T extend(@NonNull T value);
}
