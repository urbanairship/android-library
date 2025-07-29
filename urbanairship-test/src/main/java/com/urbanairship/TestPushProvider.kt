/* Copyright Airship and Contributors */
package com.urbanairship

import android.content.Context
import com.urbanairship.push.PushProvider
import com.urbanairship.push.PushProvider.RegistrationException

public class TestPushProvider @JvmOverloads public constructor(
    @param:UAirship.Platform override val platform: Int = UAirship.ANDROID_PLATFORM,
    override val deliveryType: PushProvider.DeliveryType = PushProvider.DeliveryType.FCM
) : PushProvider {

    public var registrationToken: String? = null

    @Throws(RegistrationException::class)
    override fun getRegistrationToken(context: Context): String? {
        return registrationToken
    }

    override fun isAvailable(context: Context): Boolean {
        return true
    }

    override fun isSupported(context: Context): Boolean {
        return true
    }
}
