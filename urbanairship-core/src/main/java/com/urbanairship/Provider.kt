/* Copyright Airship and Contributors */

package com.urbanairship

import androidx.annotation.RestrictTo

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun interface Provider<T> {
    public fun get(): T
}
