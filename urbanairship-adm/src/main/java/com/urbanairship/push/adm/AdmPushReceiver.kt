/* Copyright Airship and Contributors */
package com.urbanairship.push.adm

import com.urbanairship.UALog
import com.amazon.device.messaging.ADMMessageReceiver

/**
 * AdmPushReceiver listens for incoming ADM registration responses and messages.
 * @hide
 */
internal class AdmPushReceiver() : ADMMessageReceiver(AdmHandlerBase::class.java) {

    init {
        //Check if the latest ADM version is available on the device
        try {
            Class.forName("com.amazon.device.messaging.ADMMessageHandlerJobBase")
            registerJobServiceClass(AdmHandlerJobBase::class.java, 3004)
        } catch (e: ClassNotFoundException) {
            UALog.w("Using legacy ADM class : " + e.message)
        }
    }
}
