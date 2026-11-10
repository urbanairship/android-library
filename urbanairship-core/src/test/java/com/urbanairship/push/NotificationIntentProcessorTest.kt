/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.os.bundleOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.Airship
import com.urbanairship.MainDispatcherRule
import com.urbanairship.analytics.Analytics
import com.urbanairship.analytics.InteractiveNotificationEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [NotificationIntentProcessor] tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class NotificationIntentProcessorTest {

    @get:Rule
    public val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    private var context: Context = mockk(relaxed = true)
    private var notificationManager: NotificationManager = mockk(relaxed = true)
    private var analytics: Analytics = mockk(relaxed = true)

    private var notificationListener: NotificationListener = mockk(relaxed = true)

    private val pushManager: PushManager = mockk(relaxed = true) {
        every { this@mockk.notificationListener } returns this@NotificationIntentProcessorTest.notificationListener
    }

    private lateinit var responseIntent: Intent
    private lateinit var dismissIntent: Intent
    private lateinit var message: PushMessage
    private lateinit var launchIntent: Intent

    @Before
    public fun before() {

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
        every { context.packageName } returns "com.urbanairship.test"
    }

    /**
     * Test notification response.
     */
    @Test
    public fun testNotificationResponse(): TestResult = runTest {
        every { notificationListener.onNotificationOpened(any()) } returns false

        val result = processIntent(responseIntent)
        Assert.assertTrue(result.isSuccess)

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
    public fun testNotificationResponseListenerStartsApp(): TestResult = runTest {
        every { notificationListener.onNotificationOpened(any()) } returns true

        val result = processIntent(responseIntent)
        Assert.assertTrue(result.isSuccess)

        // Verify the application was not auto launched
        verify(exactly = 0) { context.startActivity(any()) }
    }

    /**
     * Test foreground action response.
     */
    @Test
    public fun testForegroundActionResponse(): TestResult = runTest {
        every { notificationListener.onNotificationForegroundAction(any(), any()) } returns false

        // Update the response intent to contain action info
        responseIntent
            .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID, "buttonId")
            .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, true)

        val result = processIntent(responseIntent)
        Assert.assertTrue(result.isSuccess)

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
    public fun testForegroundActionResponseListenerStartsApp(): TestResult = runTest {
        every { notificationListener.onNotificationForegroundAction(any(), any()) } returns true

        // Update the response intent to contain action info
        responseIntent
            .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID, "buttonId")
            .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, true)

        val result = processIntent(responseIntent)
        Assert.assertTrue(result.isSuccess)

        // Verify the application was not auto launched
        verify(exactly = 0) { context.startActivity(any()) }
    }

    /**
     * Test background action response.
     */
    @Test
    public fun testBackgroundActionResponse(): TestResult = runTest {
        // Update the response intent to contain action info
        responseIntent
            .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID, "buttonId")
            .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, false)

        val result = processIntent(responseIntent)
        Assert.assertTrue(result.isSuccess)

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
    public fun testNotificationDismissed(): TestResult = runTest {
        val result = processIntent(dismissIntent)
        Assert.assertTrue(result.isSuccess)

        // Verify the application was not auto launched
        verify(exactly = 0) { context.startActivity(any()) }

        // Verify the listener was notified
        verify { notificationListener.onNotificationDismissed(any()) }
    }

    /**
     * Test invalid intents.
     */
    @Test
    public fun testInvalidIntents(): TestResult = runTest {
        // Missing action
        Assert.assertTrue(processIntent(Intent()).isFailure)

        // Missing push data
        Assert.assertTrue(processIntent(Intent().setAction(PushManager.ACTION_NOTIFICATION_RESPONSE)).isFailure)
        Assert.assertTrue(processIntent(Intent().setAction(PushManager.ACTION_NOTIFICATION_DISMISSED)).isFailure)
    }

    /**
     * Test that the SDK is not reached while the processor is being constructed.
     */
    @Test
    public fun testConstructionDoesNotResolveAirship() {
        mockkObject(Airship)
        try {
            val resolvedDuringConstruction = AssertionError("Resolved the SDK during construction")
            every { Airship.analytics } throws resolvedDuringConstruction
            every { Airship.push } throws resolvedDuringConstruction
            every { Airship.airshipConfigOptions } throws resolvedDuringConstruction

            NotificationIntentProcessor(context = context, intent = responseIntent)
        } finally {
            unmockkObject(Airship)
        }
    }

    /**
     * Test that processing fails when the SDK never becomes ready.
     */
    @Test
    public fun testNotReadyFails(): TestResult = runTest {
        val result = processIntent(responseIntent, waitForReady = { false })

        Assert.assertTrue(result.isFailure)
        verify(exactly = 0) { context.startActivity(any()) }
        verify(exactly = 0) { notificationListener.onNotificationOpened(any()) }
    }

    /**
     * Test that the timeout covers the wait for the SDK to become ready.
     */
    @Test
    public fun testTimeoutCoversWaitForReady(): TestResult = runTest {
        val result = processIntent(
            intent = responseIntent,
            timeout = 9.seconds,
            waitForReady = { awaitCancellation() }
        )

        Assert.assertTrue(result.exceptionOrNull() is TimeoutCancellationException)
        verify(exactly = 0) { context.startActivity(any()) }
    }

    private fun verifyApplicationLaunched() {
        verify { context.startActivity(launchIntent) }

        Assert.assertEquals(
            (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).toLong(),
            launchIntent.flags.toLong()
        )
        Assert.assertEquals(message.getPushBundle(), launchIntent.getBundleExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE))
    }

    private suspend fun processIntent(
        intent: Intent,
        timeout: Duration? = null,
        waitForReady: suspend () -> Boolean = { true }
    ): Result<Unit> {
        val deferred = CompletableDeferred<Result<Unit>>()
        NotificationIntentProcessor(
            context = context,
            intent = intent,
            waitForReady = waitForReady,
            analyticsProvider = { analytics },
            pushProvider = { pushManager },
            autoLaunchApplicationProvider = { true }
        ).process(timeout) { result ->
            deferred.complete(result)
        }
        return deferred.await()
    }
}
