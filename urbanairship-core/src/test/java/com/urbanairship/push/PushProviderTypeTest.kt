package com.urbanairship.push

import com.urbanairship.TestPushProvider
import com.urbanairship.Airship
import com.urbanairship.Platform
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

    private companion object {
        private val PROVIDER_ADM = TestPushProvider(Platform.AMAZON, PushProvider.DeliveryType.ADM)
        private val PROVIDER_FCM = TestPushProvider(Platform.ANDROID, PushProvider.DeliveryType.FCM)
        private val PROVIDER_HMS = TestPushProvider(Platform.ANDROID, PushProvider.DeliveryType.HMS)
    }
}
