/* Copyright Airship and Contributors */
package com.urbanairship.util

import android.os.SystemClock
import androidx.annotation.RestrictTo

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public open class Clock public constructor() {

    public open fun currentTimeMillis(): Long {
        return System.currentTimeMillis()
    }

    public open fun elapsedRealtime(): Long {
        return SystemClock.elapsedRealtime()
    }

    public companion object {
        public val DEFAULT_CLOCK: Clock = Clock()
    }
}
