/* Copyright Airship and Contributors */
package com.urbanairship.push.adm

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.annotation.Keep
import com.urbanairship.AirshipVersionInfo
import com.urbanairship.Platform
import com.urbanairship.UALog
import com.urbanairship.push.PushProvider
import com.urbanairship.push.PushProvider.RegistrationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds
import com.amazon.device.messaging.ADMConstants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/**
 * Adm push provider.
 *
 * @hide
 */
@Keep
public class AdmPushProvider public constructor() : PushProvider, AirshipVersionInfo {

    override val platform: Platform = Platform.AMAZON
    override val deliveryType: PushProvider.DeliveryType = PushProvider.DeliveryType.ADM
    override fun isAvailable(context: Context): Boolean = true
    override val airshipVersion: String = BuildConfig.AIRSHIP_VERSION
    override val packageVersion: String = BuildConfig.SDK_VERSION

    @OptIn(ExperimentalCoroutinesApi::class)
    @Throws(RegistrationException::class)
    override suspend fun getRegistrationToken(context: Context): String? {
        AdmWrapper.getRegistrationId(context)?.let { return it }

        return try {
            withTimeout(REGISTRATION_TIMEOUT) {
                suspendCancellableCoroutine { continuation ->
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context, intent: Intent?) {
                            if (intent?.extras == null ||
                                ADMConstants.LowLevel.ACTION_APP_REGISTRATION_EVENT != intent.action) return
                            if (isOrderedBroadcast) resultCode = Activity.RESULT_OK
                            try { context.unregisterReceiver(this) } catch (_: Exception) {}
                            val error = intent.extras?.getString(ADMConstants.LowLevel.EXTRA_ERROR)
                            if (error == null) {
                                continuation.resume(intent.getStringExtra(ADMConstants.LowLevel.EXTRA_REGISTRATION_ID))
                            } else {
                                UALog.e("ADM error occurred: $error")
                                continuation.resumeWithException(RegistrationException(error, false))
                            }
                        }
                    }
                    val intentFilter = IntentFilter().apply {
                        addAction(ADMConstants.LowLevel.ACTION_APP_REGISTRATION_EVENT)
                        addCategory(context.packageName)
                    }
                    context.registerReceiver(
                        receiver, intentFilter, AMAZON_SEND_PERMISSION, Handler(Looper.getMainLooper())
                    )
                    continuation.invokeOnCancellation {
                        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
                    }
                    AdmWrapper.startRegistration(context)
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw RegistrationException("ADM registration timed out", true, e)
        }
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

        return isAdmAvailable && AdmWrapper.isSupported(context)
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
    }

    private companion object {
        private const val AMAZON_SEND_PERMISSION = "com.amazon.device.messaging.permission.SEND"
        private var isAdmDependencyAvailable: Boolean? = null
        private val REGISTRATION_TIMEOUT = 10.seconds

    }
}
