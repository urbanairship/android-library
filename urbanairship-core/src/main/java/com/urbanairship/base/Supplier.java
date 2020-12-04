/* Copyright Airship and Contributors */

package com.urbanairship.base;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public interface Supplier<V> {
    @Nullable
    V get();
}
