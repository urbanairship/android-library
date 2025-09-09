package com.urbanairship

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.push.PushProvider
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class PushProvidersTest {

    @Test
    public fun testValidPushProvider() {
        verifyInvalidProvider(
            TestPushProvider(
                Airship.Platform.AMAZON, PushProvider.DeliveryType.FCM
            )
        )
        verifyInvalidProvider(
            TestPushProvider(
                Airship.Platform.AMAZON, PushProvider.DeliveryType.HMS
            )
        )
        verifyInvalidProvider(
            TestPushProvider(
                Airship.Platform.ANDROID, PushProvider.DeliveryType.ADM
            )
        )

        verifyValidProvider(
            TestPushProvider(
                Airship.Platform.AMAZON, PushProvider.DeliveryType.ADM
            )
        )
        verifyValidProvider(
            TestPushProvider(
                Airship.Platform.ANDROID, PushProvider.DeliveryType.HMS
            )
        )
        verifyValidProvider(
            TestPushProvider(
                Airship.Platform.ANDROID, PushProvider.DeliveryType.FCM
            )
        )
    }

    public companion object {

        public fun verifyValidProvider(provider: TestPushProvider) {
            val configOptions =
                AirshipConfigOptions.newBuilder().setCustomPushProvider(provider).build()

            val providers = PushProviders.load(ApplicationProvider.getApplicationContext(), configOptions)
            Assert.assertEquals(1, providers.getAvailableProviders().size.toLong())
            Assert.assertEquals(provider, providers.getAvailableProviders()[0])
        }

        public fun verifyInvalidProvider(provider: TestPushProvider) {
            val configOptions =
                AirshipConfigOptions.newBuilder().setCustomPushProvider(provider).build()

            val providers = PushProviders.load(ApplicationProvider.getApplicationContext(), configOptions)
            Assert.assertTrue(providers.getAvailableProviders().isEmpty())
        }
    }
}
