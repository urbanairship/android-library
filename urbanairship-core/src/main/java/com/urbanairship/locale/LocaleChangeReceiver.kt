package com.urbanairship.locale

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.RestrictTo
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.UAirship

/**
 * Broadcast receiver that listens for [Intent.ACTION_LOCALE_CHANGED].
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class LocaleChangeReceiver public constructor() : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (Intent.ACTION_LOCALE_CHANGED != intent?.action) {
            return
        }

        if (!UAirship.isTakingOff && !UAirship.isFlying) {
            UALog.e("LocaleChangedReceiver - unable to receive intent, takeOff not called.")
            return
        }

        Autopilot.automaticTakeOff(context)
        UAirship.shared().localeManager.onDeviceLocaleChanged()
    }
}
