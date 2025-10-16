/* Copyright Airship and Contributors */

package com.urbanairship.remotedata

import android.content.Context
import com.urbanairship.PushProviders
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.Airship
import com.urbanairship.Platform
import com.urbanairship.http.RequestException
import com.urbanairship.push.PushProvider
import io.mockk.every
import io.mockk.mockk
import java.util.Locale
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
public class RemoteDataUrlFactoryTest {

    private val runtimeConfig = TestAirshipRuntimeConfig()
    private val availableProviders: MutableList<PushProvider> = mutableListOf()

    private val pushProviders: PushProviders = mockk {
        every { getAvailableProviders() } returns availableProviders
    }

    private val factory: RemoteDataUrlFactory = RemoteDataUrlFactory(
        runtimeConfig,
        { pushProviders }
    )

    /**
     * Test the SDK version is sent as a query parameter.
     */
    @Test
    @Throws(RequestException::class)
    public fun testSdkVersion() {
        val uri = factory.createAppUrl(Locale("en"), 555)!!
        Assert.assertEquals(uri.getQueryParameter("sdk_version"), Airship.version)
    }

    /**
     * Test the push providers are sent as a query parameter.
     */
    @Test
    @Throws(RequestException::class)
    public fun testPushProviders() {
        availableProviders.add(TestPushProvider(PushProvider.DeliveryType.FCM))
        availableProviders.add(TestPushProvider(PushProvider.DeliveryType.FCM))
        availableProviders.add(TestPushProvider(PushProvider.DeliveryType.ADM))
        val uri = factory.createAppUrl(Locale("en"), 555)!!
        Assert.assertEquals(uri.getQueryParameter("push_providers"), "fcm,adm")
    }

    /**
     * Test the push providers is not added if the available providers is empty.
     */
    @Test
    @Throws(RequestException::class)
    public fun testEmptyPushProviders() {
        val uri = factory.createAppUrl(Locale("en"), 555)!!
        Assert.assertNull(uri.getQueryParameter("push_providers"))
    }

    /**
     * Test the manufacturer is included if on the "should include" list.
     */
    @Test
    @Throws(RequestException::class)
    public fun testManufacturer() {
        ShadowBuild.setManufacturer("huawei")
        val uri = factory.createAppUrl(Locale("en"), 555)!!
        Assert.assertEquals(uri.getQueryParameter("manufacturer"), "huawei")
    }

    /**
     * Test the manufacturer is not included if not on the "should include" list.
     */
    @Test
    @Throws(RequestException::class)
    public fun testManufacturerNotIncluded() {
        ShadowBuild.setManufacturer("google")
        val uri = factory.createAppUrl(Locale("en"), 555)!!
        Assert.assertNull(uri.getQueryParameter("manufacturer"))
    }

    /**
     * Test locale info is sent as query parameters.
     */
    @Test
    @Throws(RequestException::class)
    public fun testLocale() {
        val locale = Locale("en", "US")
        val uri = factory.createAppUrl(locale, 555)!!
        Assert.assertEquals(uri.getQueryParameter("language"), "en")
        Assert.assertEquals(uri.getQueryParameter("country"), "US")
    }

    /**
     * Test country is not sent as a query parameter if it's not defined.
     */
    @Test
    @Throws(RequestException::class)
    public fun testLocaleMissingCountry() {
        val locale = Locale("de")
        val uri = factory.createAppUrl(locale, 555)!!
        Assert.assertEquals(uri.getQueryParameter("language"), "de")
        Assert.assertNull(uri.getQueryParameter("country"))
    }

    /**
     * Test language is not sent as a query parameter if it's not defined.
     */
    @Test
    @Throws(RequestException::class)
    public fun testLocaleMissingLanguage() {
        val locale = Locale("", "US")
        val uri = factory.createAppUrl(locale, 555)!!
        Assert.assertEquals(uri.getQueryParameter("country"), "US")
    }

    @Test
    public fun testAppUrl() {
        val uri = factory.createAppUrl(Locale.CANADA_FRENCH, 555)!!
        val sdkVersion = Airship.version

        Assert.assertEquals(
            "https://remote-data.urbanairship.com/api/remote-data/app/appKey/android?sdk_version=$sdkVersion&random_value=555&language=fr&country=CA",
            uri.toString()
        )
    }

    @Test
    public fun testContactUrl() {
        val uri = factory.createContactUrl("some-contact-id", Locale.CANADA_FRENCH, 555)!!

        val sdkVersion = Airship.version
        Assert.assertEquals(
            "https://remote-data.urbanairship.com/api/remote-data-contact/android/some-contact-id?sdk_version=$sdkVersion&random_value=555&language=fr&country=CA",
            uri.toString()
        )
    }

    private class TestPushProvider(override val deliveryType: PushProvider.DeliveryType) : PushProvider {

        override val platform: Platform
            get() = throw RuntimeException("Not implemented")


        override fun getRegistrationToken(context: Context): String? {
            throw RuntimeException("Not implemented")
        }

        override fun isAvailable(context: Context): Boolean {
            return true
        }

        override fun isSupported(context: Context): Boolean {
            return true
        }
    }
}
