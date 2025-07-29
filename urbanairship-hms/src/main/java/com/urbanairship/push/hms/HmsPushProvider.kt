/* Copyright Airship and Contributors */
package com.urbanairship.push.hms

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipVersionInfo
import com.urbanairship.BuildConfig
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.push.PushProvider
import com.urbanairship.push.PushProvider.RegistrationException
import com.urbanairship.util.UAStringUtil
import com.huawei.agconnect.config.AGConnectServicesConfig
import com.huawei.hms.aaid.HmsInstanceId
import com.huawei.hms.api.HuaweiApiAvailability

/**
 * HMS push provider.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class HmsPushProvider public constructor() : PushProvider, AirshipVersionInfo {

    override val platform: Int = UAirship.ANDROID_PLATFORM
    override val deliveryType: PushProvider.DeliveryType = PushProvider.DeliveryType.HMS
    override val airshipVersion: String = BuildConfig.AIRSHIP_VERSION
    override val packageVersion: String = BuildConfig.SDK_VERSION

    @Throws(RegistrationException::class)
    override fun getRegistrationToken(context: Context): String? {
        val token: String?

        try {
            val appId = AGConnectServicesConfig.fromContext(context).getString(APP_ID_KEY)
                ?: return null

            token = HmsInstanceId.getInstance(context).getToken(appId, HCM_SCOPE)
        } catch (e: Exception) {
            throw RegistrationException("HMS error " + e.message, true, e)
        }

        if (token.isNullOrEmpty()) {
            return HmsTokenCache.shared()[context]
                ?: throw RegistrationException("Empty HMS registration token", true)
        } else {
            HmsTokenCache.shared()[context] = token
            return token
        }
    }

    override fun isAvailable(context: Context): Boolean {
        try {
            val appId = AGConnectServicesConfig.fromContext(context).getString(APP_ID_KEY)
            return !appId.isNullOrEmpty()
        } catch (e: Exception) {
            UALog.e(e, "HmsPushProvider - HMS availability check failed.")
            return false
        }
    }

    override fun isSupported(context: Context): Boolean {
        try {
            return HuaweiApiAvailability.getInstance().isHuaweiMobileNoticeAvailable(context) == 0
        } catch (e: Exception) {
            UALog.e(e, "HmsPushProvider - HMS is supported check failed.")
            return false
        }
    }

    override fun toString(): String {
        return "HMS Push Provider $airshipVersion"
    }

    private companion object {
        private const val HCM_SCOPE = "HCM"
        private const val APP_ID_KEY = "client/app_id"
    }
}
