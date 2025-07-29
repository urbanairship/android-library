/* Copyright Airship and Contributors */
package com.urbanairship.push.adm

import android.content.Intent
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.push.PushMessage
import com.urbanairship.push.PushProviderBridge
import com.amazon.device.messaging.ADMMessageHandlerBase

public class AdmHandlerBase : ADMMessageHandlerBase { //Class that is used for legacy ADM devices
    public constructor(className: String?) : super(className)
    public constructor() : super("AdmHandlerBase")

    override fun onMessage(intent: Intent) {
        Autopilot.automaticTakeOff(applicationContext)
        val message = PushMessage(intent.extras!!)
        PushProviderBridge
            .processPush(AdmPushProvider::class.java, message)
            .executeSync(applicationContext)
    }

    override fun onRegistrationError(errorId: String) {
        UALog.e("An error occurred during ADM Registration : $errorId")
    }

    override fun onRegistered(newRegistrationId: String) {
        PushProviderBridge.requestRegistrationUpdate(
            context = applicationContext,
            pushProviderClass = AdmPushProvider::class.java,
            newToken = newRegistrationId
        )
    }

    override fun onUnregistered(newRegistrationId: String) {
        PushProviderBridge.requestRegistrationUpdate(
            context = applicationContext,
            pushProviderClass = AdmPushProvider::class.java,
            newToken = newRegistrationId
        )
    }
}
