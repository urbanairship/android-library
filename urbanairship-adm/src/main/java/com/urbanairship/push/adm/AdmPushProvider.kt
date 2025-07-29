/* Copyright Airship and Contributors */
package com.urbanairship.push.adm

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import com.urbanairship.AirshipVersionInfo
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.push.PushProvider
import com.urbanairship.push.PushProvider.RegistrationException
import kotlin.time.Duration.Companion.seconds
import com.amazon.device.messaging.ADMConstants

/**
 * Adm push provider.
 *
 * @hide
 */
public class AdmPushProvider public constructor() : PushProvider, AirshipVersionInfo {

    override val platform: Int = UAirship.AMAZON_PLATFORM
    override val deliveryType: PushProvider.DeliveryType = PushProvider.DeliveryType.ADM
    override fun isAvailable(context: Context): Boolean = true
    override val airshipVersion: String = BuildConfig.AIRSHIP_VERSION
    override val packageVersion: String = BuildConfig.SDK_VERSION

    @Throws(RegistrationException::class)
    override fun getRegistrationToken(context: Context): String? {
        AdmWrapper.getRegistrationId(context)?.let { return it }

        val registerReceiver = RegistrationReceiver()

        val intentFilter = IntentFilter()
        intentFilter.addAction(ADMConstants.LowLevel.ACTION_APP_REGISTRATION_EVENT)
        intentFilter.addCategory(context.packageName)

        context.registerReceiver(
            registerReceiver, intentFilter, AMAZON_SEND_PERMISSION, Handler(Looper.getMainLooper())
        )
        AdmWrapper.startRegistration(context)

        try {
            registerReceiver.awaitRegistration()
        } catch (e: InterruptedException) {
            throw RegistrationException("ADM registration interrupted", true, e)
        }

        context.unregisterReceiver(registerReceiver)

        registerReceiver.error?.let {
            throw RegistrationException(it, false)
        }

        return registerReceiver.registrationToken
    }

    override fun isSupported(context: Context): Boolean {
        val isAdmAvailable = isAdmDependencyAvailable ?: run {
            var result: Boolean
            try {
                Class.forName("com.amazon.device.messaging.ADM")
                result = true
            } catch (e: ClassNotFoundException) {
                result = false
            }
            isAdmDependencyAvailable = result
            result
        }

        return isAdmAvailable && AdmWrapper.isSupported()
    }

    override fun toString(): String {
        return "ADM Push Provider $airshipVersion"
    }

    private class RegistrationReceiver : BroadcastReceiver() {

        var registrationToken: String? = null
        var error: String? = null

        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.extras != null && ADMConstants.LowLevel.ACTION_APP_REGISTRATION_EVENT == intent.action) {
                val error = intent.extras?.getString(ADMConstants.LowLevel.EXTRA_ERROR)
                if (error == null) {
                    this.registrationToken = intent.getStringExtra(ADMConstants.LowLevel.EXTRA_REGISTRATION_ID)
                    return
                }

                UALog.e("ADM error occurred: $error")
                this.error = error
            }

            if (this.isOrderedBroadcast) {
                resultCode = Activity.RESULT_OK
            }

            synchronized(this) {
                (this as Object).notifyAll()
            }
        }

        @Throws(InterruptedException::class)
        fun awaitRegistration() {
            synchronized(this) {
                (this as Object).wait(REGISTRATION_TIMEOUT.inWholeMilliseconds)
            }
        }

        private companion object {
            private val REGISTRATION_TIMEOUT = 10.seconds
        }
    }

    private companion object {
        private const val AMAZON_SEND_PERMISSION = "com.amazon.device.messaging.permission.SEND"
        private var isAdmDependencyAvailable: Boolean? = null
    }
}
