/* Copyright Airship and Contributors */

package com.urbanairship.util.timer

import androidx.annotation.RestrictTo
import kotlin.time.Duration

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface Timer {
    public val time: Duration
    public fun start()
    public fun stop()
}
