/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.RestrictTo
import com.urbanairship.Airship
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A broadcast receiver that handles notification intents.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class NotificationProxyReceiver public constructor() : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Autopilot.automaticTakeOff(context)

        if (!Airship.isTakingOff && !Airship.isFlying) {
            UALog.e("NotificationProxyReceiver - unable to receive intent, takeOff not called.")
            return
        }

        if (intent?.action == null) {
            return
        }

        UALog.v("Received intent: %s", intent.action)

        /**
         * We wait for the callback to finish before finishing the broadcast. We have 10 seconds total
         * to process the intent before its considered a background ANR, so we are giving ourselves 9 seconds
         * before finishing early (1 second for takeOff).
         */
        val pendingResult = goAsync()
        NotificationIntentProcessor(context, intent).process(ACTION_TIMEOUT) { result ->
            result.fold(onSuccess = {
                UALog.v { "Finished processing notification intent with result $it." }
            }, onFailure = {
                UALog.e(it) {
                    "NotificationProxyReceiver - Exception when processing notification intent."
                }
            })

            // Finish the broadcast
            pendingResult.finish()
        }
    }

    public companion object {
        private val ACTION_TIMEOUT: Duration = 9.seconds
    }
}
