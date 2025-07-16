/* Copyright Airship and Contributors */
package com.urbanairship.base

import androidx.annotation.RestrictTo

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun interface Supplier<V> {
    public fun get(): V?
}
