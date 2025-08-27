/* Copyright Airship and Contributors */
package com.urbanairship

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.Airship.OnReadyCallback
import com.urbanairship.actions.DeepLinkListener
import kotlin.concurrent.Volatile
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.update
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowApplication

@RunWith(AndroidJUnit4::class)
class AirshipTest {

    private var configOptions = AirshipConfigOptions.Builder()
        .setProductionAppKey("0000000000000000000000")
        .setProductionAppSecret("0000000000000000000000")
        .setInProduction(true)
        .build()

    private val looper = Looper.myLooper()
    private var application = TestApplication.getApplication()

    @Before
    fun setup() {
        // TestApplication automatically sets up airship for other tests, clean it up with land.
        Airship.land()
    }

    @After
    fun cleanup() {
        Airship.land()
    }

    /**
     * Test takeOff with valid application and config options calls the correct callbacks.
     */
    @Test
    fun testAsyncTakeOff() {
        val testCallback = TestCallback()
        Airship.shared(testCallback)

        val cancelCallback = TestCallback()
        val cancelable = Airship.shared(cancelCallback)
        cancelable.cancel()

        val takeOffCallback: TestCallback = object : TestCallback() {
            override fun onAirshipReady(airship: Airship) {
                super.onAirshipReady(airship)
                Assert.assertFalse(
                    "Take off callback should be called first", testCallback.onReadyCalled
                )
            }
        }

        Airship.takeOff(application, configOptions, takeOffCallback)

        // Block until its ready
        Airship.shared()
        Shadows.shadowOf(looper).runToEndOfTasks()

        Assert.assertTrue(testCallback.onReadyCalled)
        Assert.assertTrue(takeOffCallback.onReadyCalled)
        Assert.assertFalse(cancelCallback.onReadyCalled)

        // Verify the airship ready intent was fired
        val intents = getApplicationShadow().broadcastIntents
        Assert.assertEquals(intents.size, 1)
        Assert.assertEquals(intents[0].action, Airship.ACTION_AIRSHIP_READY)
        Assert.assertNull(intents[0].extras)
    }

    /**
     * Test takeOff with valid application and config options calls the correct callbacks.
     * Also tests the AIRSHIP_READY broadcast is extended.
     */
    @Test
    fun testAsyncTakeOffWithExtendedBroadcasts() {
        val testCallback = TestCallback()
        Airship.shared(testCallback)

        val cancelCallback = TestCallback()
        val cancelable = Airship.shared(cancelCallback)
        cancelable.cancel()

        val takeOffCallback: TestCallback = object : TestCallback() {
            override fun onAirshipReady(airship: Airship) {
                super.onAirshipReady(airship)
                Assert.assertFalse(
                    "Take off callback should be called first", testCallback.onReadyCalled
                )
            }
        }

        configOptions = AirshipConfigOptions.Builder()
            .setProductionAppKey("0000000000000000000000")
            .setProductionAppSecret("0000000000000000000000")
            .setInProduction(true)
            .setExtendedBroadcastsEnabled(true)
            .build()

        Airship.takeOff(application, configOptions, takeOffCallback)

        // Block until its ready
        Airship.shared()
        Shadows.shadowOf(looper).runToEndOfTasks()

        Assert.assertTrue(testCallback.onReadyCalled)
        Assert.assertTrue(takeOffCallback.onReadyCalled)
        Assert.assertFalse(cancelCallback.onReadyCalled)

        // Verify the airship ready intent was fired
        val intents = getApplicationShadow().broadcastIntents
        Assert.assertEquals(intents.size, 1)
        Assert.assertEquals(intents[0].action, Airship.ACTION_AIRSHIP_READY)
        val extras = intents[0].extras
        Assert.assertNotNull(extras)
        Assert.assertEquals(extras?.getInt("payload_version"), 1)
        Assert.assertEquals(extras?.getString("app_key"), "0000000000000000000000")
        Assert.assertTrue(extras?.containsKey("channel_id") == true)
        Assert.assertEquals(extras?.getString("channel_id"), null)
    }

    /**
     * Test that we throw an illegal state exception when shared() is called before
     * takeOff
     */
    @Test(expected = IllegalStateException::class)
    fun testSharedBeforeTakeOff() {
        Airship.shared()
    }

    @Test
    fun testDeepLinkListener() {
        Airship.takeOff(application, configOptions)

        val airship = Airship.shared()

        val mockListener: DeepLinkListener = mockk()
        airship.deepLinkListener = mockListener

        val goodDeepLink = "some deep link"
        val badDeepLink = "some other deep link"

        every { mockListener.onDeepLink(any()) } answers {
            val deepLink = firstArg<String>()
            when(deepLink) {
                goodDeepLink -> true
                else -> false
            }
        }

        Assert.assertTrue(airship.deepLink(goodDeepLink))
        Assert.assertFalse(airship.deepLink(badDeepLink))

        verify { mockListener.onDeepLink(goodDeepLink) }
        verify { mockListener.onDeepLink(badDeepLink) }
    }

    @Test
    fun testDeepLinkNotHandledByListener() {
        Airship.takeOff(application, configOptions)

        val airship = Airship.shared()

        val mockListener: DeepLinkListener = mockk()
        airship.deepLinkListener = mockListener

        every { mockListener.onDeepLink("some deep link") } returns true
        Assert.assertTrue(airship.deepLink("some deep link"))
        verify { mockListener.onDeepLink("some deep link") }
    }

    @Test
    fun testAirshipDeepLinks() {
        // App Settings deeplink
        Airship.takeOff(application, configOptions)

        val airship = Airship.shared()

        var deepLink = "uairship://app_settings"
        var uri = Uri.parse(deepLink)

        val mockComponent: AirshipComponent = mockk(relaxed = true) {
            every { onAirshipDeepLink(uri) } answers { true }
        }

        airship.components.update { it + mockComponent }

        var mockListener: DeepLinkListener = mockk()
        airship.deepLinkListener = mockListener

        Assert.assertTrue(airship.deepLink(deepLink))

        verify { mockListener wasNot Called }
        verify(exactly = 0) { mockComponent.onAirshipDeepLink(uri) }

        // App Store deeplink
        deepLink = "uairship://app_store"
        uri = Uri.parse(deepLink)

        Assert.assertTrue(airship.deepLink(deepLink))

        verify { mockListener wasNot Called }
        verify(exactly = 0) { mockComponent.onAirshipDeepLink(uri) }
    }

    @Test
    fun testAirshipComponentsDeepLinks() {
        Airship.takeOff(application, configOptions)

        val airship = Airship.shared()

        val deepLink = "uairship://neat"
        val uri = Uri.parse(deepLink)

        val mockComponent1: AirshipComponent = mockk(relaxed = true) {
            every { onAirshipDeepLink(uri) } returns false
        }

        val mockComponent2: AirshipComponent = mockk(relaxed = true) {
            every { onAirshipDeepLink(uri) } returns true
        }

        val mockComponent3: AirshipComponent = mockk(relaxed = true) {
            every { onAirshipDeepLink(uri) } returns true
        }
        airship.components.update { it + listOf(mockComponent1, mockComponent2, mockComponent3) }

        val mockListener: DeepLinkListener = mockk()
        airship.deepLinkListener = mockListener

        Assert.assertTrue(airship.deepLink(deepLink))

        verify { mockListener wasNot Called }
        verify { mockComponent1.onAirshipDeepLink(uri) }
        verify { mockComponent2.onAirshipDeepLink(uri) }
        verify(exactly = 0) { mockComponent3.onAirshipDeepLink(uri) }
    }

    @Test
    fun testAirshipComponentsDeepLinksFallbackDeepLinkListener() {
        Airship.takeOff(application, configOptions)

        val airship = Airship.shared()

        val deepLink = "uairship://neat"
        val uri = Uri.parse(deepLink)

        val mockComponent1: AirshipComponent = mockk(relaxed = true) {
            every { onAirshipDeepLink(uri) } returns false
        }
        airship.components.update { it + mockComponent1 }

        val mockListener: DeepLinkListener = mockk(relaxed = true)
        airship.deepLinkListener = mockListener

        Assert.assertTrue(airship.deepLink(deepLink))

        verify { mockListener.onDeepLink(deepLink) }
        verify { mockComponent1.onAirshipDeepLink(uri) }
    }

    @Test
    fun testAirshipComponentsDeepLinksNotHandled() {
        Airship.takeOff(application, configOptions)

        val airship = Airship.shared()

        val deepLink = "uairship://neat"
        val uri = Uri.parse(deepLink)

        val mockComponent1: AirshipComponent = mockk(relaxed = true) {
            every { onAirshipDeepLink(uri) } returns false
        }
        airship.components.update { it + mockComponent1 }

        val mockListener: DeepLinkListener = mockk(relaxed = true)
        airship.deepLinkListener = mockListener

        Assert.assertTrue(airship.deepLink(deepLink))

        verify { mockListener.onDeepLink(deepLink) }
        verify { mockComponent1.onAirshipDeepLink(uri) }
    }

    private fun getApplicationShadow(): ShadowApplication {
        return Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
    }

    /**
     * Helper callback for testing.
     */
    internal open class TestCallback : OnReadyCallback {

        @Volatile
        var onReadyCalled: Boolean = false

        override fun onAirshipReady(airship: Airship) {
            onReadyCalled = true
        }
    }
}
