/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.app.Activity
import android.os.Bundle
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.Airship
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * An activity that handles notification intents.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class NotificationProxyActivity public constructor() : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Autopilot.automaticTakeOff(this)

        if (!Airship.isTakingOff && !Airship.isFlying) {
            UALog.e("NotificationProxyActivity - unable to receive intent, takeOff not called.")
            finish()
            return
        }

        if (intent?.action == null) {
            finish()
            return
        }

        UALog.v("Received intent: %s", intent.action)

        // Processing outlives this activity, which must finish before onCreate returns.
        NotificationIntentProcessor(applicationContext, intent).process(readyTimeout = TAKE_OFF_TIMEOUT) { result ->
            result.fold(onSuccess = {
                UALog.v { "Finished processing notification intent with result $it." }
            }, onFailure = {
                UALog.e(it) {
                    "NotificationProxyActivity - Exception when processing notification intent."
                }
            })
        }

        // This needs to be called before onCreate finishes or Android will crash
        finish()
    }

    internal companion object {
        /**
         * How long to wait for takeOff before giving up on a notification intent.
         *
         * Bounded by Android's background activity start grace period rather than by an ANR
         * budget: processing resumes after this activity has finished, and once that period
         * lapses the application launch is dropped and the notification tap opens nothing.
         * Only the wait is bounded, so notification actions still run to completion.
         */
        @VisibleForTesting
        internal val TAKE_OFF_TIMEOUT: Duration = 8.seconds
    }
}
