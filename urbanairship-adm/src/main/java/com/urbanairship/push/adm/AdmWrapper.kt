/* Copyright Airship and Contributors */
package com.urbanairship.push.adm

import android.content.Context
import com.urbanairship.UALog
import com.amazon.device.messaging.ADM

/**
 * Wrapper around ADM methods.
 */
internal object AdmWrapper {

    /**
     * Wraps [ADM.isSupported].
     *
     * @return The value returned by [ADM.isSupported].
     */
    fun isSupported(context: Context?): Boolean {
        try {
            return ADM(context).isSupported
        } catch (ex: RuntimeException) {
            UALog.w("Failed to call ADM. Make sure ADM jar is not bundled with the APK.")
            return false
        }
    }

    /**
     * Wraps [ADM.startRegister].
     */
    fun startRegistration(context: Context) {
        ADM(context).startRegister()
    }

    /**
     * Wraps [ADM.getRegistrationId].
     *
     * @param context The application context.
     * @return The registration ID or null if ADM has not registered yet.
     */
    fun getRegistrationId(context: Context?): String? {
        return ADM(context).registrationId
    }
}
