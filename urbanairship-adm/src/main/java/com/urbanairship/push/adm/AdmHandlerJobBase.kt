/* Copyright Airship and Contributors */
package com.urbanairship.push.adm

import android.content.Context
import android.content.Intent
import androidx.core.os.bundleOf
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.push.PushMessage
import com.urbanairship.push.PushProviderBridge
import com.amazon.device.messaging.ADMMessageHandlerJobBase

public class AdmHandlerJobBase public constructor() : ADMMessageHandlerJobBase() {

    override fun onMessage(context: Context, intent: Intent) {
        Autopilot.automaticTakeOff(context)
        val message = PushMessage(intent.extras ?: bundleOf())

        PushProviderBridge
            .processPush(AdmPushProvider::class.java, message)
            .executeSync(context)
    }

    override fun onRegistrationError(context: Context, errorId: String) {
        UALog.e("An error occurred during ADM Registration : $errorId")
    }

    override fun onRegistered(context: Context, newRegistrationId: String) {
        PushProviderBridge.requestRegistrationUpdate(
            context = context,
            pushProviderClass = AdmPushProvider::class.java,
            newToken = newRegistrationId
        )
    }

    override fun onUnregistered(context: Context, newRegistrationId: String) {
        PushProviderBridge.requestRegistrationUpdate(
            context = context,
            pushProviderClass = AdmPushProvider::class.java,
            newToken = newRegistrationId
        )
    }
}
