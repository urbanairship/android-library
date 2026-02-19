/* Copyright Airship and Contributors */
package com.urbanairship.push.fcm

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.Airship.airshipConfigOptions
import com.urbanairship.AirshipVersionInfo
import com.urbanairship.BuildConfig
import com.urbanairship.Platform
import com.urbanairship.UALog
import com.urbanairship.google.PlayServicesUtils
import com.urbanairship.push.PushProvider
import com.urbanairship.push.PushProvider.PushProviderUnavailableException
import com.urbanairship.push.PushProvider.RegistrationException
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

/**
 * FCM push provider.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class FcmPushProvider public constructor() : PushProvider, AirshipVersionInfo {

    override val platform: Platform = Platform.ANDROID
    override val deliveryType: PushProvider.DeliveryType = PushProvider.DeliveryType.FCM
    override val airshipVersion: String = BuildConfig.AIRSHIP_VERSION
    override val packageVersion: String = BuildConfig.SDK_VERSION

    @Throws(RegistrationException::class)
    override fun getRegistrationToken(context: Context): String? {
        val firebaseMessaging = try {
            getFirebaseMessaging()
        } catch (e: Exception) {
            throw PushProviderUnavailableException(
                "Firebase messaging unavailable: " + e.message, e
            )
        }

        try {
            return Tasks.await(firebaseMessaging.getToken())
        } catch (e: Exception) {
            throw RegistrationException("FCM error " + e.message, true, e)
        }
    }

    override fun isAvailable(context: Context): Boolean {
        try {
            val playServicesStatus = PlayServicesUtils.isGooglePlayServicesAvailable(context)
            if (ConnectionResult.SUCCESS != playServicesStatus) {
                UALog.i("Google Play services is currently unavailable.")
                return false
            }
        } catch (e: Exception) {
            UALog.e(e, "Unable to register with FCM.")
            return false
        }
        return true
    }

    override fun isSupported(context: Context): Boolean {
        return try {
            PlayServicesUtils.isGooglePlayStoreAvailable(context)
        } catch (e: Exception) {
            UALog.e(e, "Unable to check Google Play Store availability for FCM.")
            false
        }
    }

    override fun toString(): String {
        return "FCM Push Provider $airshipVersion"
    }

    public companion object {

        @Throws(IllegalStateException::class)
        private fun getFirebaseMessaging(): FirebaseMessaging {
            val configOptions = airshipConfigOptions
            if (configOptions.fcmFirebaseAppName.isNullOrEmpty()) {
                // This will throw an IllegalStateException if firebase is not configured
                return FirebaseMessaging.getInstance()
            } else {
                // This will throw an IllegalStateException if the app name is not registered
                val app = FirebaseApp.getInstance(configOptions.fcmFirebaseAppName!!)
                return app.get(FirebaseMessaging::class.java)
            }
        }
    }
}
