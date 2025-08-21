/* Copyright Airship and Contributors */
package com.urbanairship.util

import androidx.annotation.RestrictTo
import kotlin.math.max

/**
 * A class containing basic math operations.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object UAMathUtil {

    /**
     * Constrains an int to a min and max.
     *
     * @param value Value to clamp.
     * @param min The floor of the value.
     * @param max The ceiling of the value.
     * @return The value constrained between the min and max.
     */
    public fun constrain(value: Int, min: Int, max: Int): Int {
        return value.coerceIn(min, max)
    }
}
