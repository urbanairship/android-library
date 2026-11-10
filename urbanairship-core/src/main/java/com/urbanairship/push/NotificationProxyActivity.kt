/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.app.Activity
import android.os.Bundle
import androidx.annotation.RestrictTo
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
        NotificationIntentProcessor(applicationContext, intent).process(ACTION_TIMEOUT) { result ->
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

    private companion object {
        /**
         * How long processing may run after this activity has finished.
         *
         * Bounded by Android's background activity start grace period rather than by an ANR
         * budget: once that period lapses, the application launch this processing performs is
         * dropped and the notification tap opens nothing.
         */
        private val ACTION_TIMEOUT: Duration = 8.seconds
    }
}
