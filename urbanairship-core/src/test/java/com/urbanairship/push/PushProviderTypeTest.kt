package com.urbanairship.push

import com.urbanairship.TestPushProvider
import com.urbanairship.UAirship
import org.junit.Assert.assertEquals
import org.junit.Test

public class PushProviderTypeTest {

    @Test
    public fun testFromFcm() {
        assertEquals(PushProviderType.FCM, PushProviderType.from(PROVIDER_FCM))
    }

    @Test
    public fun testFromAdm() {
        assertEquals(PushProviderType.ADM, PushProviderType.from(PROVIDER_ADM))
    }

    @Test
    public fun testFromHms() {
        assertEquals(PushProviderType.HMS, PushProviderType.from(PROVIDER_HMS))
    }

    @Test
    public fun testFromNull() {
        assertEquals(PushProviderType.NONE, PushProviderType.from(null))
    }

    @Test
    public fun testFromInvalid() {
        val invalidProvider = TestPushProvider(UAirship.ANDROID_PLATFORM, "snail mail")
        assertEquals(PushProviderType.NONE, PushProviderType.from(invalidProvider))
    }

    private companion object {
        private val PROVIDER_ADM =
            TestPushProvider(UAirship.AMAZON_PLATFORM, PushProvider.ADM_DELIVERY_TYPE)
        private val PROVIDER_FCM =
            TestPushProvider(UAirship.ANDROID_PLATFORM, PushProvider.FCM_DELIVERY_TYPE)
        private val PROVIDER_HMS =
            TestPushProvider(UAirship.ANDROID_PLATFORM, PushProvider.HMS_DELIVERY_TYPE)
    }
}
