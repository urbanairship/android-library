/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipExecutors
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.UAirship
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A broadcast receiver that handles notification intents.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class NotificationProxyReceiver public constructor() : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Autopilot.automaticTakeOff(context)

        if (!UAirship.isTakingOff && !UAirship.isFlying) {
            UALog.e("NotificationProxyReceiver - unable to receive intent, takeOff not called.")
            return
        }

        if (intent?.action == null) {
            return
        }

        UALog.v("Received intent: %s", intent.action)

        val pendingResult = goAsync()
        val future = NotificationIntentProcessor(context = context, intent = intent).process()

        AirshipExecutors.threadPoolExecutor().execute {
            try {
                val result = future[ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS]
                UALog.v("Finished processing notification intent with result $result.")
            } catch (e: InterruptedException) {
                UALog.e(e, "NotificationProxyReceiver - Exception when processing notification intent.")
                Thread.currentThread().interrupt()
            } catch (e: ExecutionException) {
                UALog.e(e, "NotificationProxyReceiver - Exception when processing notification intent.")
                Thread.currentThread().interrupt()
            } catch (e: TimeoutException) {
                UALog.e("NotificationProxyReceiver - Application took too long to process notification intent.")
            }
            pendingResult.finish()
        }
    }

    public companion object {
        private const val ACTION_TIMEOUT_SECONDS: Long = 9 // 9 seconds
    }
}
