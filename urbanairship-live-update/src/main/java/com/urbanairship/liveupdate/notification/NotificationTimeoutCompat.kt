/* Copyright Airship and Contributors */

package com.urbanairship.liveupdate.notification

import androidx.core.app.NotificationCompat
import com.urbanairship.util.Clock
import com.urbanairship.util.minus
import java.time.Instant

/** Sets the timeout for a notification. */
internal class NotificationTimeoutCompat(
    private val clock: Clock = Clock.DEFAULT_CLOCK,
) {
    internal fun setTimeoutAt(
        builder: NotificationCompat.Builder,
        timeoutAt: Instant
    ): NotificationCompat.Builder =
        builder.setTimeoutAfter((timeoutAt - clock.now()).inWholeMilliseconds)
}
