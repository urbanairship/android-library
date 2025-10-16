/* Copyright Airship and Contributors */
package com.urbanairship

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.actions.DeepLinkListener
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowApplication

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class AirshipTest {

    @get:Rule
    public val mainDispatcherRule = MainDispatcherRule()

    private var configOptions = AirshipConfigOptions.Builder()
        .setProductionAppKey("0000000000000000000000")
        .setProductionAppSecret("0000000000000000000000")
        .setInProduction(true)
        .build()

    // Assuming TestApplication provides a suitable Application instance via ApplicationProvider
    private val application = ApplicationProvider.getApplicationContext<TestApplication>()

    @Before
    public fun setup() {
        // Land Airship to ensure a clean state before each test
        Airship.land()
    }

    @After
    public fun cleanup() {
        Airship.land()
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Test takeOff with valid application and config options calls the correct callbacks.
     */
    @Test
    public fun testAsyncTakeOff() = runTest {
        val testCallback = TestCallback()
        Airship.onReady(testCallback)

        val takeOffCallback: TestCallback = object : TestCallback() {
            override fun onAirshipReady(airship: Airship) {
                super.onAirshipReady(airship)
                Assert.assertFalse(
                    "Take off callback should be called first", testCallback.onReadyCalled.value
                )
            }
        }

        Airship.takeOff(application, configOptions, takeOffCallback)

        // Assert readiness using the suspending method
        Assert.assertTrue(takeOffCallback.checkForReady())
        Assert.assertTrue(testCallback.checkForReady())

        // Verify the airship ready intent was fired
        val intents = getApplicationShadow().broadcastIntents
        Assert.assertEquals(1, intents.size)
        Assert.assertEquals(Airship.ACTION_AIRSHIP_READY, intents[0].action)
        Assert.assertNull(intents[0].extras)
    }

    /**
     * Test takeOff with valid application and config options calls the correct callbacks.
     * Also tests the AIRSHIP_READY broadcast is extended.
     */
    @Test
    public fun testAsyncTakeOffWithExtendedBroadcasts() = runTest {
        val testCallback = TestCallback()
        Airship.onReady(testCallback)

        val takeOffCallback: TestCallback = object : TestCallback() {
            override fun onAirshipReady(airship: Airship) {
                super.onAirshipReady(airship)
                Assert.assertFalse(
                    "Take off callback should be called first", testCallback.onReadyCalled.value
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

        // Assert readiness using the suspending method
        Assert.assertTrue(takeOffCallback.checkForReady())
        Assert.assertTrue(testCallback.checkForReady())

        val intents = getApplicationShadow().broadcastIntents
        Assert.assertEquals(1, intents.size)
        Assert.assertEquals(Airship.ACTION_AIRSHIP_READY, intents[0].action)
        val extras = intents[0].extras
        Assert.assertNotNull(extras)
        Assert.assertEquals(1, extras?.getInt("payload_version"))
        Assert.assertEquals("0000000000000000000000", extras?.getString("app_key"))
        Assert.assertTrue(extras?.containsKey("channel_id") == true)
        Assert.assertEquals(null, extras?.getString("channel_id"))
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Test that we throw an illegal state exception when a component is accessed before
     * takeOff.
     */
    @Test(expected = IllegalStateException::class)
    public fun testSharedBeforeTakeOff() {
        // Accessing any component (like Airship.analytics) will trigger requireReadyInstance
        // which throws the IllegalStateException if takeOff wasn't called.
        Airship.analytics
    }

    @Test
    public fun testDeepLinkListener() = runTest {
        Airship.takeOff(application, configOptions)

        val mockListener: DeepLinkListener = mockk()
        Airship.deepLinkListener = mockListener

        val goodDeepLink = "some deep link"
        val badDeepLink = "some other deep link"

        every { mockListener.onDeepLink(any()) } answers {
            val deepLink = firstArg<String>()
            when(deepLink) {
                goodDeepLink -> true
                else -> false
            }
        }

        Assert.assertTrue(Airship.deepLink(goodDeepLink))
        Assert.assertFalse(Airship.deepLink(badDeepLink))

        verify { mockListener.onDeepLink(goodDeepLink) }
        verify { mockListener.onDeepLink(badDeepLink) }
    }

    @Test
    public fun testDeepLinkNotHandledByListener() = runTest {
        Airship.takeOff(application, configOptions)

        val mockListener: DeepLinkListener = mockk()
        Airship.deepLinkListener = mockListener

        every { mockListener.onDeepLink("some deep link") } returns true
        Assert.assertTrue(Airship.deepLink("some deep link"))
        verify { mockListener.onDeepLink("some deep link") }
    }

    @Test
    public fun testAirshipDeepLinks() = runTest {
        // App Settings deeplink
        Airship.takeOff(application, configOptions)

        var deepLink = "uairship://app_settings"
        var uri = Uri.parse(deepLink)

        val mockComponent: AirshipComponent = mockk(relaxed = true) {
            every { onAirshipDeepLink(uri) } answers { true }
        }

        Airship.requireReadyInstance().components.update { it + mockComponent }

        var mockListener: DeepLinkListener = mockk()
        Airship.deepLinkListener = mockListener

        Assert.assertTrue(Airship.deepLink(deepLink))

        // These links are handled internally before hitting components/listener
        verify { mockListener wasNot Called }
        verify(exactly = 0) { mockComponent.onAirshipDeepLink(uri) }

        // App Store deeplink
        deepLink = "uairship://app_store"
        uri = Uri.parse(deepLink)

        Assert.assertTrue(Airship.deepLink(deepLink))

        verify { mockListener wasNot Called }
        verify(exactly = 0) { mockComponent.onAirshipDeepLink(uri) }
    }

    @Test
    public fun testAirshipComponentsDeepLinks() = runTest {
        Airship.takeOff(application, configOptions)

        val airship = Airship

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

        airship.requireReadyInstance().components.update {
            it + listOf(mockComponent1, mockComponent2, mockComponent3)
        }

        val mockListener: DeepLinkListener = mockk()
        airship.deepLinkListener = mockListener

        Assert.assertTrue(airship.deepLink(deepLink))

        verify { mockListener wasNot Called }
        verify { mockComponent1.onAirshipDeepLink(uri) }
        // Execution should stop after the first component handles it (mockComponent2)
        verify { mockComponent2.onAirshipDeepLink(uri) }
        verify(exactly = 0) { mockComponent3.onAirshipDeepLink(uri) }
    }

    @Test
    public fun testAirshipComponentsDeepLinksFallbackDeepLinkListener() = runTest {
        Airship.takeOff(application, configOptions)

        val airship = Airship

        val deepLink = "uairship://neat"
        val uri = Uri.parse(deepLink)

        val mockComponent1: AirshipComponent = mockk(relaxed = true) {
            every { onAirshipDeepLink(uri) } returns false
        }
        airship.requireReadyInstance().components.update { it + mockComponent1 }

        val mockListener: DeepLinkListener = mockk(relaxed = true)
        airship.deepLinkListener = mockListener

        Assert.assertTrue(airship.deepLink(deepLink))

        // Component didn't handle it, so it falls back to the listener
        verify { mockListener.onDeepLink(deepLink) }
        verify { mockComponent1.onAirshipDeepLink(uri) }
    }

    @Test
    public fun testAirshipComponentsDeepLinksNotHandled() = runTest {
        Airship.takeOff(application, configOptions)

        val airship = Airship

        val deepLink = "uairship://neat"
        val uri = Uri.parse(deepLink)

        val mockComponent1: AirshipComponent = mockk(relaxed = true) {
            every { onAirshipDeepLink(uri) } returns false
        }
        airship.requireReadyInstance().components.update { it + mockComponent1 }

        val mockListener: DeepLinkListener = mockk(relaxed = true) {
            every { onDeepLink(deepLink) } returns false // Listener returns false
        }
        airship.deepLinkListener = mockListener

        // The method should still return true because the deep link process was handled
        // (i.e., components/listener were called).
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
    internal open class TestCallback : Airship.OnReadyCallback {

        @Volatile
        var onReadyCalled: MutableStateFlow<Boolean> = MutableStateFlow(false)

        suspend fun checkForReady(duration: Duration = 10.seconds): Boolean {
           return withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(duration) {
                    onReadyCalled.first { it }
                }
            }
        }

        override fun onAirshipReady(airship: Airship) {
            onReadyCalled.update { true }
        }
    }
}
