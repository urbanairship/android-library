/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.os.bundleOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestApplication
import com.urbanairship.Airship
import com.urbanairship.analytics.Analytics
import com.urbanairship.analytics.InteractiveNotificationEvent
import java.util.concurrent.Executor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [NotificationIntentProcessor] tests.
 */
@RunWith(AndroidJUnit4::class)
class NotificationIntentProcessorTest {

    private var context: Context = mockk(relaxed = true)
    private var notificationManager: NotificationManager = mockk(relaxed = true)
    private var analytics: Analytics = mockk(relaxed = true)
    private var notificationListener: NotificationListener = mockk(relaxed = true)
    private val executor: Executor = Executor { command -> command.run() }

    private lateinit var responseIntent: Intent
    private lateinit var dismissIntent: Intent
    private lateinit var message: PushMessage
    private lateinit var launchIntent: Intent

    @Before
    fun before() {
        val pushManager: PushManager = mockk(relaxed = true) {
            every { notificationListener } returns this@NotificationIntentProcessorTest.notificationListener
        }

        TestApplication.getApplication().setAnalytics(analytics)
        TestApplication.getApplication().setPushManager(pushManager)

        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager

        val pushBundle = bundleOf(
            PushMessage.EXTRA_SEND_ID to "sendId",
            PushMessage.EXTRA_METADATA to "metadata"
        )
        message = PushMessage(pushBundle)

        responseIntent = Intent()
            .setAction(PushManager.ACTION_NOTIFICATION_RESPONSE)
            .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, pushBundle)
            .putExtra(PushManager.EXTRA_NOTIFICATION_ID, 150)
            .putExtra(PushManager.EXTRA_NOTIFICATION_TAG, "TAG")

        dismissIntent = Intent()
            .setAction(PushManager.ACTION_NOTIFICATION_DISMISSED)
            .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, pushBundle)
            .putExtra(PushManager.EXTRA_NOTIFICATION_ID, 150)
            .putExtra(PushManager.EXTRA_NOTIFICATION_TAG, "TAG")

        launchIntent = Intent().setAction("LAUNCH")

        every { context.packageManager } returns mockk {
            every { getLaunchIntentForPackage(any()) } returns launchIntent
        }
    }

    /**
     * Test notification response.
     */
    @Test
    fun testNotificationResponse() {
        every { notificationListener.onNotificationOpened(any()) } returns false

        val result = processIntent(responseIntent)
        Assert.assertTrue(result == true)

        // Verify the conversion id and metadata was set
        verify { analytics.conversionSendId = message.sendId }
        verify { analytics.conversionMetadata = message.metadata }

        // Verify the application was launched
        verifyApplicationLaunched()

        // Verify the listener was called
        verify { notificationListener.onNotificationOpened(any()) }
    }

    /**
     * Test notification response when the listener returns true.
     */
    @Test
    fun testNotificationResponseListenerStartsApp() {
        every { notificationListener.onNotificationOpened(any()) } returns true

        val result = processIntent(responseIntent)
        Assert.assertTrue(result == true)

        // Verify the application was not auto launched
        verify(exactly = 0) { context.startActivity(any()) }
    }

    /**
     * Test foreground action response.
     */
    @Test
    fun testForegroundActionResponse() {
        every { notificationListener.onNotificationForegroundAction(any(), any()) } returns false

        // Update the response intent to contain action info
        responseIntent
            .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID, "buttonId")
            .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, true)

        val result = processIntent(responseIntent)
        Assert.assertTrue(result == true)

        // Verify the conversion id and metadata was set
        verify { analytics.conversionSendId = message.sendId }
        verify { analytics.conversionMetadata = message.metadata }

        // Verify the notification was dismissed
        verify { notificationManager.cancel("TAG", 150) }

        // Verify the application was launched
        verifyApplicationLaunched()

        // Verify we added an interactive notification event
        verify { analytics.addEvent(any<InteractiveNotificationEvent>()) }

        // Verify the listener was notified
        verify { notificationListener.onNotificationForegroundAction(any(), any()) }
    }

    /**
     * Test foreground action response when the listener returns true.
     */
    @Test
    fun testForegroundActionResponseListenerStartsApp() {
        every { notificationListener.onNotificationForegroundAction(any(), any()) } returns true

        // Update the response intent to contain action info
        responseIntent
            .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID, "buttonId")
            .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, true)

        val result = processIntent(responseIntent)
        Assert.assertTrue(result == true)

        // Verify the application was not auto launched
        verify(exactly = 0) { context.startActivity(any()) }
    }

    /**
     * Test background action response.
     */
    @Test
    fun testBackgroundActionResponse() {
        // Update the response intent to contain action info
        responseIntent
            .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID, "buttonId")
            .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, false)

        val result = processIntent(responseIntent)
        Assert.assertTrue(result == true)

        // Verify the notification was dismissed
        verify { notificationManager.cancel("TAG", 150) }

        // Verify the application was not auto launched
        verify(exactly = 0) { context.startActivity(any()) }

        // Verify we added an interactive notification event
        verify { analytics.addEvent(any<InteractiveNotificationEvent>()) }

        // Verify the listener was notified
        verify { notificationListener.onNotificationBackgroundAction(any(), any()) }
    }

    /**
     * Test notification dismissed.
     */
    @Test
    fun testNotificationDismissed() {
        val result = processIntent(dismissIntent)
        Assert.assertTrue(result == true)

        // Verify the application was not auto launched
        verify(exactly = 0) { context.startActivity(any()) }

        // Verify the listener was notified
        verify { notificationListener.onNotificationDismissed(any()) }
    }

    /**
     * Test invalid intents.
     */
    @Test
    fun testInvalidIntents() {
        // Missing action
        Assert.assertFalse(processIntent(Intent()) == true)

        // Missing push data
        Assert.assertFalse(processIntent(Intent().setAction(PushManager.ACTION_NOTIFICATION_RESPONSE))!!)
        Assert.assertFalse(processIntent(Intent().setAction(PushManager.ACTION_NOTIFICATION_DISMISSED))!!)
    }

    private fun verifyApplicationLaunched() {
        verify { context.startActivity(launchIntent) }

        Assert.assertEquals(
            (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).toLong(),
            launchIntent.flags.toLong()
        )
        Assert.assertEquals(message.getPushBundle(), launchIntent.getBundleExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE))
    }

    private fun processIntent(intent: Intent): Boolean? {
        return NotificationIntentProcessor(Airship.shared(), context, intent, executor)
            .process()
            .get()
    }
}
