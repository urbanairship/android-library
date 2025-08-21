/* Copyright Airship and Contributors */
package com.urbanairship

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.channel.AirshipChannel
import java.util.Calendar
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SuppressLint("NewApi")
@RunWith(AndroidJUnit4::class)
class ChannelCaptureTest {

    private val mockChannel: AirshipChannel = mockk()
    private var configOptions = AirshipConfigOptions.Builder()
        .setDevelopmentAppKey("appKey")
        .setDevelopmentAppSecret("appSecret")
        .build()
    private val clipboardManager = ApplicationProvider
        .getApplicationContext<Context>()
        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val dataStore = PreferenceDataStore.inMemoryStore(ApplicationProvider.getApplicationContext())
    private val activityMonitor = TestActivityMonitor()
    private val dispatcher = StandardTestDispatcher()

    private var capture = makeChannelCapture()

    private fun makeChannelCapture(): ChannelCapture = ChannelCapture(
        context = ApplicationProvider.getApplicationContext(),
        configOptions = configOptions,
        airshipChannel = mockChannel,
        preferenceDataStore = dataStore,
        activityMonitor = activityMonitor,
        dispatcher = dispatcher
    )

    @Before
    fun setup() {
        capture.init()
        clearClipboard()
    }

    @After
    fun takeDown() {
        capture.isEnabled = false
    }

    /**
     * Test disabling the channel capture through AirshipConfigOptions.
     */
    @Test
    fun testChannelCaptureDisabled() {
        // Disable the channel capture
        configOptions = AirshipConfigOptions.Builder()
            .setDevelopmentAppKey("appKey")
            .setDevelopmentAppSecret("appSecret")
            .setChannelCaptureEnabled(false)
            .build()

        // Reinitialize it
        capture.tearDown()
        capture = makeChannelCapture()

        capture.init()

        every { mockChannel.id } returns "channel ID"
        knockAndRunLooperTasks(6)

        val clipData = clipboardManager.primaryClip
        Assert.assertEquals(
            "",
            clipData?.getItemAt(0)?.coerceToText(ApplicationProvider.getApplicationContext())
        )
    }

    /**
     * Test enabling the channel capture through AirshipConfigOptions.
     */
    @Test
    fun testChannelCaptureEnabled() {
        // Enable the channel capture
        configOptions = AirshipConfigOptions.Builder()
            .setDevelopmentAppKey("appKey")
            .setDevelopmentAppSecret("appSecret")
            .setChannelCaptureEnabled(true)
            .build()

        // Reinitialize it
        capture.tearDown()
        capture = makeChannelCapture()

        capture.init()

        every { mockChannel.id } returns "channel ID"

        knockAndRunLooperTasks(6)

        val clipData = clipboardManager.primaryClip
        Assert.assertEquals(
            "ua:" + mockChannel.id,
            clipData?.getItemAt(0)?.coerceToText(ApplicationProvider.getApplicationContext())
        )
    }

    /**
     * Test the channel capture with Channel ID null.
     */
    @Test
    fun testChannelCaptureEnabledChannelNull() {
        // Enable the channel capture
        configOptions = AirshipConfigOptions.Builder()
            .setDevelopmentAppKey("appKey")
            .setDevelopmentAppSecret("appSecret")
            .setChannelCaptureEnabled(true)
            .build()

        // Reinitialize it
        capture.tearDown()
        capture = makeChannelCapture()

        capture.init()

        every { mockChannel.id } returns null

        knockAndRunLooperTasks(6)

        val clipData = clipboardManager.primaryClip
        Assert.assertEquals(
            "ua:",
            clipData?.getItemAt(0)?.coerceToText(ApplicationProvider.getApplicationContext())
        )
    }

    /**
     * Test the channel capture on a single knock.
     */
    @Test
    fun testChannelCaptureSingleForeground() {
        // Enable the channel capture
        configOptions = AirshipConfigOptions.Builder()
            .setDevelopmentAppKey("appKey")
            .setDevelopmentAppSecret("appSecret")
            .setChannelCaptureEnabled(true)
            .build()

        // Reinitialize it
        capture.tearDown()
        capture = makeChannelCapture()

        capture.init()

        every { mockChannel.id } returns "Channel ID"

        knockAndRunLooperTasks(1)

        val clipData = clipboardManager.primaryClip
        Assert.assertEquals(
            "",
            clipData?.getItemAt(0)?.coerceToText(ApplicationProvider.getApplicationContext())
        )
    }

    /**
     * Test channel capture requires 6 knocks each time.
     */
    @Test
    fun testChannelCaptureRequires6Knocks() {
        // Enable the channel capture
        configOptions = AirshipConfigOptions.Builder()
            .setDevelopmentAppKey("appKey")
            .setDevelopmentAppSecret("appSecret")
            .setChannelCaptureEnabled(true)
            .build()

        // Reinitialize it
        capture.tearDown()
        capture = makeChannelCapture()

        capture.init()

        every { mockChannel.id } returns "channel ID"

        knockAndRunLooperTasks(6)

        var clipData = clipboardManager.primaryClip
        Assert.assertEquals(
            "ua:" + mockChannel.id,
            clipData?.getItemAt(0)?.coerceToText(ApplicationProvider.getApplicationContext())
        )

        clearClipboard()
        clipData = clipboardManager.primaryClip

        knockAndRunLooperTasks(1)

        clipData = clipboardManager.primaryClip
        Assert.assertEquals(
            "",
            clipData?.getItemAt(0)?.coerceToText(ApplicationProvider.getApplicationContext())
        )

        knockAndRunLooperTasks(5)

        clipData = clipboardManager.primaryClip
        Assert.assertEquals(
            "ua:" + mockChannel.id,
            clipData?.getItemAt(0)?.coerceToText(ApplicationProvider.getApplicationContext())
        )
    }

    /**
     * Test enabling the channel capture at runtime
     */
    @Test
    fun testEnable() {
        capture.isEnabled = true
        Assert.assertTrue(capture.isEnabled)
    }

    /**
     * Test disabling the channel capture at runtime
     */
    @Test
    fun testDisable() {
        capture.isEnabled = true
        capture.isEnabled = false
        Assert.assertFalse(capture.isEnabled)
    }

    /**
     * Send one or more knocks, followed by `runToEndOfTasks` on the background looper.
     */
    private fun knockAndRunLooperTasks(repeat: Int) {
        for (i in 0..<repeat) {
            activityMonitor.foreground(Calendar.getInstance().timeInMillis)
        }

        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun clearClipboard() {
        val clipData = ClipData.newPlainText("", "")
        clipboardManager.setPrimaryClip(clipData)
    }
}
