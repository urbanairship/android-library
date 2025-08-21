/* Copyright Airship and Contributors */
package com.urbanairship

import android.content.Context
import com.urbanairship.push.PushProvider
import com.urbanairship.push.PushProvider.RegistrationException

public class TestPushProvider public constructor(
    override val platform: UAirship.Platform = UAirship.Platform.ANDROID,
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
