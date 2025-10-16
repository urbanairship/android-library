/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.Airship

/**
 * Tracks Google Play Store install referrals. The receiver needs to be added
 * to the manifest before the install referrals will be tracked:
 * ```
 * `<receiver android:name="com.urbanairship.analytics.InstallReceiver" exported="true">
 * <intent-filter>
 * <action android:name="com.android.vending.INSTALL_REFERRER" />
 * </intent-filter>
 * </receiver>
 * ```
 *
 *
 * Only a single receiver is able to handle the `"com.android.vending.INSTALL_REFERRER"` action.
 * To handle multiple receivers, instead of registering the [InstallReceiver], register a custom
 * receiver that notifies multiple receivers:
 * ```
 * `// Notify the Airship InstallReceiver
 * new InstallReceiver().onReceive(context, intent);
 *
 * // Notify other receivers
 * new OtherReceiver().onReceive(context, intent);
 * ```
 */
public class InstallReceiver internal constructor(
    private val eventRecorder: (InstallAttributionEvent) -> Unit =  { event ->
        Airship.onReady {
            analytics.addEvent(event)
        }
    }
) : BroadcastReceiver() {

    public constructor(): this(eventRecorder = { event ->
        Airship.onReady {
            analytics.addEvent(event)
        }
    })

    override fun onReceive(context: Context, intent: Intent?) {
        Autopilot.automaticTakeOff(context)
        if (intent == null) {
            return
        }

        val referrer = intent.getStringExtra(EXTRA_INSTALL_REFERRER)
        if (referrer?.isEmpty() != false || ACTION_INSTALL_REFERRER != intent.action) {
            UALog.d("missing referrer or invalid action.")
            return
        }

        eventRecorder(InstallAttributionEvent(referrer))
    }

    private companion object {
        private const val EXTRA_INSTALL_REFERRER = "referrer"
        private const val ACTION_INSTALL_REFERRER = "com.android.vending.INSTALL_REFERRER"
    }
}
