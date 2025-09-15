/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.bundleOf
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestActivityMonitor
import com.urbanairship.TestApplication
import com.urbanairship.TestPushProvider
import com.urbanairship.analytics.Analytics
import com.urbanairship.job.JobDispatcher
import com.urbanairship.push.notifications.NotificationArguments
import com.urbanairship.push.notifications.NotificationArguments.Companion.newBuilder
import com.urbanairship.push.notifications.NotificationChannelCompat
import com.urbanairship.push.notifications.NotificationChannelRegistry
import com.urbanairship.push.notifications.NotificationProvider
import com.urbanairship.push.notifications.NotificationResult
import com.urbanairship.push.notifications.NotificationResult.Companion.cancel
import com.urbanairship.push.notifications.NotificationResult.Companion.notification
import com.urbanairship.push.notifications.NotificationResult.Companion.retry
import com.urbanairship.util.PendingIntentCompat
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
public class IncomingPushRunnableTest {

    private var activityMonitor = TestActivityMonitor()
    private var pushBundle = bundleOf(
        PushMessage.EXTRA_ALERT to "Test Push Alert!",
        PushMessage.EXTRA_PUSH_ID to "testPushID",
        PushMessage.EXTRA_SEND_ID to "testSendID",
        PushMessage.EXTRA_NOTIFICATION_TAG to "testNotificationTag"
    )
    private var message = PushMessage(pushBundle)
    private var accengageMessage = PushMessage(
        pushBundle = bundleOf(
            "a4scontent" to "neat",
            "a4sid" to 77
        )
    )

    private var testPushProvider = TestPushProvider()
    private var mockChannelRegistry: NotificationChannelRegistry = mockk(relaxed = true)
    private var notificationProvider = TestNotificationProvider()

    private var pushManager: PushManager = mockk(relaxed = true) {
        every { pushProvider } returns testPushProvider
        every { isPushAvailable } returns true
        every { notificationChannelRegistry } returns mockChannelRegistry
        every { notificationProvider } answers { this@IncomingPushRunnableTest.notificationProvider }
    }

    private var notificationManager: NotificationManagerCompat = mockk(relaxed = true)
    private var analytics: Analytics = mockk()

    private var accengageNotificationProvider: TestNotificationProvider? = null

    private var jobDispatcher: JobDispatcher = mockk(relaxed = true)
    private lateinit var pushRunnable: IncomingPushRunnable
    private lateinit var displayRunnable: IncomingPushRunnable


    @Before
    public fun setup() {
        TestApplication.getApplication().setPushManager(pushManager)
        TestApplication.getApplication().setAnalytics(analytics)

        pushRunnable = IncomingPushRunnable.Builder(TestApplication.getApplication())
            .setProviderClass(testPushProvider.javaClass.toString())
            .setMessage(PushMessage(pushBundle))
            .setNotificationManager(notificationManager)
            .setLongRunning(true)
            .setJobDispatcher(jobDispatcher)
            .setActivityMonitor(activityMonitor)
            .build()

        displayRunnable = IncomingPushRunnable.Builder(TestApplication.getApplication())
            .setProviderClass(testPushProvider.javaClass.toString())
            .setMessage(PushMessage(pushBundle))
            .setNotificationManager(notificationManager)
            .setLongRunning(true)
            .setJobDispatcher(jobDispatcher)
            .setProcessed(true)
            .setActivityMonitor(activityMonitor)
            .build()
    }

    /**
     * Test displaying a notification from a push message.
     */
    @Test
    public fun testDisplayNotification() {
        every { pushManager.isPushEnabled } returns true
        every { pushManager.isOptIn } returns true
        every { pushManager.isUniqueCanonicalId("testPushID") } returns true

        val notification = createNotification()
        notificationProvider.notification = notification
        notificationProvider.tag = "testNotificationTag"

        pushRunnable.run()

        verify { notificationManager.notify("testNotificationTag", TEST_NOTIFICATION_ID, notification) }

        val shadowPendingIntent = Shadows.shadowOf(notificationProvider.notification?.contentIntent)
        Assert.assertTrue(
            "The pending intent is an activity intent.", shadowPendingIntent.isActivity
        )

        val intent = shadowPendingIntent.savedIntent
        Assert.assertEquals(
            "The intent action should match.",
            intent.action,
            PushManager.ACTION_NOTIFICATION_RESPONSE
        )
        Assert.assertEquals(
            "The push message bundles should match.",
            pushBundle,
            intent.extras?.getBundle(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE)
        )
        Assert.assertEquals("One category should exist.", 1, intent.categories.size)

        verify { pushManager.onPushReceived(message, true) }
        verify { pushManager.onNotificationPosted(message, TEST_NOTIFICATION_ID, "testNotificationTag") }
    }

    /**
     * Test ignoring push from other vendors.
     */
    @Test
    public fun test() {
        every { pushManager.isPushEnabled } returns true
        every { pushManager.isOptIn } returns true
        every { pushManager.isUniqueCanonicalId("testPushID") } returns true

        notificationProvider.notification = createNotification()
        notificationProvider.tag = "testNotificationTag"

        pushRunnable = IncomingPushRunnable.Builder(TestApplication.getApplication())
            .setProviderClass("wrong  class")
            .setMessage(PushMessage(pushBundle))
            .setLongRunning(true)
            .setNotificationManager(notificationManager)
            .build()

        pushRunnable.run()

        verify { notificationManager wasNot Called }
    }

    /**
     * Test receiving a push from an invalid provider.
     */
    @Test
    public fun testInvalidPushProvider() {
        every { pushManager.isPushEnabled } returns true
        every { pushManager.isOptIn } returns true
        every { pushManager.isUniqueCanonicalId("testPushID") } returns true

        pushRunnable = IncomingPushRunnable.Builder(TestApplication.getApplication())
            .setProviderClass("wrong  class")
            .setMessage(PushMessage(pushBundle))
            .setLongRunning(true)
            .setNotificationManager(notificationManager)
            .build()

        pushRunnable.run()

        verify { notificationManager wasNot Called }
    }

    /**
     * Test user notifications disabled still notifies user of a background notification.
     */
    @Test
    public fun testUserNotificationsDisabled() {
        every { pushManager.isPushEnabled } returns true
        every { pushManager.isOptIn } returns false
        every { pushManager.isUniqueCanonicalId("testPushID") } returns true

        pushRunnable.run()

        verify { pushManager.onPushReceived(message, false) }
    }

    /**
     * Test suppress message in foreground when isForegroundDisplayable is false.
     */
    @Test
    public fun testNotForegroundDisplayable() {
        every { pushManager.isPushEnabled } returns true
        every { pushManager.isOptIn } returns true
        every { pushManager.isUniqueCanonicalId("testPushID") } returns true

        activityMonitor.foreground()

        notificationProvider.notification = createNotification()
        notificationProvider.tag = "testNotificationTag"

        pushBundle.putString("com.urbanairship.foreground_display", "false")
        message = PushMessage(pushBundle)

        pushRunnable = IncomingPushRunnable.Builder(TestApplication.getApplication())
            .setProviderClass(testPushProvider.javaClass.toString())
            .setMessage(message)
            .setNotificationManager(notificationManager)
            .setLongRunning(true)
            .setJobDispatcher(jobDispatcher)
            .setActivityMonitor(activityMonitor)
            .build()

        pushRunnable.run()

        verify { pushManager.onPushReceived(message, false) }
    }

    /**
     * Test message in foreground when isForegroundDisplayable is true.
     */
    @Test
    public fun testForegroundDisplayable() {
        every { pushManager.isPushEnabled } returns true
        every { pushManager.isOptIn } returns true
        every { pushManager.isUniqueCanonicalId("testPushID") } returns true
        every { pushManager.foregroundNotificationDisplayPredicate } returns null

        activityMonitor.foreground()

        notificationProvider.notification = createNotification()
        notificationProvider.tag = "testNotificationTag"

        pushBundle.putString("com.urbanairship.foreground_display", "true")
        message = PushMessage(pushBundle)

        pushRunnable = IncomingPushRunnable.Builder(TestApplication.getApplication())
            .setProviderClass(testPushProvider.javaClass.toString())
            .setMessage(message)
            .setNotificationManager(notificationManager)
            .setLongRunning(true)
            .setJobDispatcher(jobDispatcher)
            .setActivityMonitor(activityMonitor)
            .build()

        pushRunnable.run()

        verify { pushManager.onPushReceived(message, true) }
    }

    @Test
    public fun testForegroundDisplayPredicate() {
        every { pushManager.isPushEnabled } returns true
        every { pushManager.isOptIn } returns true
        every { pushManager.isUniqueCanonicalId("testPushID") } returns true
        every { pushManager.foregroundNotificationDisplayPredicate } returns { false }

        activityMonitor.foreground()

        notificationProvider.notification = createNotification()
        notificationProvider.tag = "testNotificationTag"
        message = PushMessage(pushBundle)

        pushRunnable = IncomingPushRunnable.Builder(TestApplication.getApplication())
            .setProviderClass(testPushProvider.javaClass.toString())
            .setMessage(message)
            .setNotificationManager(notificationManager)
            .setLongRunning(true)
            .setJobDispatcher(jobDispatcher)
            .setActivityMonitor(activityMonitor)
            .build()

        pushRunnable.run()

        verify { pushManager.onPushReceived(message, false) }
    }

    /**
     * Test handling a background push.
     */
    @Test
    public fun testBackgroundPush() {
        every { pushManager.isPushEnabled } returns true
        every { pushManager.isOptIn } returns true
        every { pushManager.isUniqueCanonicalId("testPushID") } returns true

        pushRunnable.run()

        verify { pushManager.onPushReceived(message, false) }
    }

    /**
     * Test handling an exceptions from the notification provider.
     */
    @Test
    public fun testNotificationProviderException() {
        every { pushManager.isPushEnabled } returns true
        every { pushManager.isOptIn } returns true
        every { pushManager.isUniqueCanonicalId("testPushID") } returns true

        // Set a notification factory that throws an exception
        notificationProvider = object : TestNotificationProvider() {
            override fun onCreateNotification(
                context: Context, arguments: NotificationArguments
            ): NotificationResult {
                throw RuntimeException("Unable to create and display notification.")
            }
        }

        pushRunnable.run()

        verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
        verify(exactly = 0) { jobDispatcher.dispatch(any()) }
    }

    @Test
    public fun testNotificationProviderSuccess() {
        val notification = createNotification()
        notificationProvider.notification = notification

        every { pushManager.isPushEnabled } returns true
        every { pushManager.isOptIn } returns true
        every { pushManager.isUniqueCanonicalId("testPushID") } returns true

        pushRunnable.run()

        verify { notificationManager.notify(null, TEST_NOTIFICATION_ID, notification)}
        verify(exactly = 0) { jobDispatcher.dispatch(any()) }
    }

    /**
     * Test that when the provider returns a cancel status, no notification is posted and no jobs are scheduled
     */
    @Test
    public fun testNotificationProviderResultCancel() {
        every { pushManager.isPushEnabled } returns true
        every { pushManager.isOptIn } returns true
        every { pushManager.isUniqueCanonicalId("testPushID") } returns true

        notificationProvider = object : TestNotificationProvider() {
            override fun onCreateNotification(
                context: Context, arguments: NotificationArguments
            ): NotificationResult {
                return cancel()
            }
        }

        displayRunnable.run()

        verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
        verify(exactly = 0) { jobDispatcher.dispatch(any()) }
    }

    /**
     * Test that when the factory returns a retry status, no notification is posted and a retry job is scheduled
     */
    @Test
    public fun testNotificationFactoryResultRetry() {
        every { pushManager.isPushEnabled } returns true
        every { pushManager.isOptIn } returns true
        every { pushManager.isUniqueCanonicalId("testPushID") } returns true

        notificationProvider = object : TestNotificationProvider() {
            override fun onCreateNotification(
                context: Context, arguments: NotificationArguments
            ): NotificationResult {
                return retry()
            }
        }

        displayRunnable.run()

        verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
        verify { jobDispatcher.dispatch(any()) }
    }

    /**
     * Test that when the factory returns a successful result, a notification is posted and no jobs are scheduled.
     */
    @Test
    public fun testNotificationFactoryResultOK() {
        every { pushManager.isPushEnabled } returns true
        every { pushManager.isOptIn } returns true
        every { pushManager.isUniqueCanonicalId("testPushID") } returns true

        val notification = createNotification()
        notificationProvider.notification = notification

        displayRunnable.run()

        verify { notificationManager.notify(null, TEST_NOTIFICATION_ID, notification) }
        verify(exactly = 0) { jobDispatcher.dispatch(any()) }
    }

    /**
     * Test notification content intent
     */
    @Test
    public fun testNotificationContentIntent() {
        every { pushManager.isPushEnabled } returns true
        every { pushManager.isOptIn } returns true
        every { pushManager.isUniqueCanonicalId("testPushID") } returns true

        val pendingIntent = PendingIntentCompat
            .getBroadcast(ApplicationProvider.getApplicationContext(), 1, Intent(), 0)
        notificationProvider.notification = createNotification()
        notificationProvider.tag = "cool-tag"
        notificationProvider.notification!!.contentIntent = pendingIntent

        pushRunnable.run()

        val shadowPendingIntent =
            Shadows.shadowOf(notificationProvider.notification?.contentIntent)
        Assert.assertTrue(
            "The pending intent is an activity intent.", shadowPendingIntent.isActivityIntent
        )

        val intent = shadowPendingIntent.savedIntent
        Assert.assertEquals(
            "The intent action should match.",
            intent.action,
            PushManager.ACTION_NOTIFICATION_RESPONSE
        )
        Assert.assertEquals("One category should exist.", 1, intent.categories.size)
        Assert.assertNotNull("The notification content intent is not null.", pendingIntent)
        Assert.assertSame(
            "The notification content intent matches.",
            pendingIntent,
            intent.extras?.get(PushManager.EXTRA_NOTIFICATION_CONTENT_INTENT)
        )
        Assert.assertSame(
            "cool-tag", intent.extras?.getString(PushManager.EXTRA_NOTIFICATION_TAG)
        )
        Assert.assertSame(
            TEST_NOTIFICATION_ID, intent.extras?.getInt(PushManager.EXTRA_NOTIFICATION_ID)
        )
    }

    /**
     * Test notification delete intent
     */
    @Test
    public fun testNotificationDeleteIntent() {
        every { pushManager.isPushEnabled } returns true
        every { pushManager.isOptIn } returns true
        every { pushManager.isUniqueCanonicalId("testPushID") } returns true

        val pendingIntent = PendingIntentCompat
            .getBroadcast(RuntimeEnvironment.application, 1, Intent(), 0)
        notificationProvider.notification = createNotification()
        notificationProvider.notification?.deleteIntent = pendingIntent

        pushRunnable.run()

        val shadowPendingIntent =
            Shadows.shadowOf(notificationProvider.notification?.deleteIntent)
        Assert.assertTrue(
            "The pending intent is broadcast intent.", shadowPendingIntent.isBroadcast
        )

        val intent = shadowPendingIntent.savedIntent
        Assert.assertEquals(
            "The intent action should match.",
            intent.action,
            PushManager.ACTION_NOTIFICATION_DISMISSED
        )
        Assert.assertEquals("One category should exist.", 1, intent.categories.size)
        Assert.assertNotNull("The notification delete intent is not null.", pendingIntent)
        Assert.assertSame(
            "The notification delete intent matches.",
            pendingIntent,
            intent.extras?.get(PushManager.EXTRA_NOTIFICATION_DELETE_INTENT)
        )
    }

    /**
     * Test that when a push is delivered pre-Oreo the notification settings are drawn
     * from our notification channel compat layer.
     */
    @Test
    @Config(sdk = [25])
    public fun testDeliverPushPreOreo() {
        every { pushManager.isPushEnabled } returns true
        every { pushManager.isOptIn } returns true
        every { pushManager.isUniqueCanonicalId("testPushID") } returns true

        // Create a channel and set some non-default values
        val channelCompat = NotificationChannelCompat(
            TEST_NOTIFICATION_CHANNEL_ID,
            "Test Notification Channel",
            NotificationManager.IMPORTANCE_HIGH
        )
        channelCompat.sound = Uri.parse("cool://sound")
        channelCompat.enableVibration(true)
        channelCompat.enableLights(true)
        channelCompat.lightColor = 123

        every { mockChannelRegistry.getNotificationChannelSync(TEST_NOTIFICATION_CHANNEL_ID) } returns channelCompat
        notificationProvider.notification = createNotification()

        pushRunnable.run()

        val notification = notificationProvider.notification

        Assert.assertEquals(notification!!.sound, channelCompat.sound)
        Assert.assertEquals(NotificationManager.IMPORTANCE_HIGH, channelCompat.importance)
        Assert.assertEquals(
            notification.defaults and Notification.DEFAULT_VIBRATE, Notification.DEFAULT_VIBRATE
        )
        Assert.assertEquals(notification.ledARGB, channelCompat.lightColor)
    }

    /**
     * Test remote data notifications
     */
    @Test
    public fun testRemoteDataMessage() {
        every { pushManager.isPushEnabled } returns true
        every { pushManager.isOptIn } returns true
        every { pushManager.isUniqueCanonicalId("testPushID") } returns true

        // Set a notification factory that throws an exception
        notificationProvider = mockk()

        val pushBundle = bundleOf(
            PushMessage.EXTRA_PUSH_ID to "testPushID",
            PushMessage.EXTRA_SEND_ID to "testSendID",
            PushMessage.REMOTE_DATA_UPDATE_KEY to "true"
        )

        val message = PushMessage(pushBundle)

        IncomingPushRunnable.Builder(TestApplication.getApplication())
            .setProviderClass(testPushProvider.javaClass.toString())
            .setMessage(message)
            .setNotificationManager(notificationManager)
            .setLongRunning(true)
            .setJobDispatcher(jobDispatcher)
            .build()
            .run()

        verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
        verify(exactly = 0) { jobDispatcher.dispatch(any()) }
        verify { notificationProvider wasNot Called }
        verify { pushManager.onPushReceived(message, false) }
    }

    @Test
    public fun testNullNotificationChannel() {
        every { pushManager.isPushEnabled } returns true
        every { pushManager.isOptIn } returns true
        every { pushManager.isUniqueCanonicalId("testPushID") } returns true

        notificationProvider.notification = NotificationCompat.Builder(TestApplication.getApplication())
                .setContentTitle("Test NotificationBuilder Title")
                .setContentText("Test NotificationBuilder Text")
                .setAutoCancel(true)
                .build()

        notificationProvider.tag = "testNotificationTag"

        pushRunnable.run()

        verify { mockChannelRegistry wasNot Called }
        verify { notificationManager.notify("testNotificationTag", TEST_NOTIFICATION_ID, notificationProvider.notification!!) }
        verify { pushManager.onPushReceived(message, true) }
        verify { pushManager.onNotificationPosted(message, TEST_NOTIFICATION_ID, "testNotificationTag") }
    }

    private fun createNotification(): Notification = NotificationCompat
        .Builder(TestApplication.getApplication(), "some-channel")
        .setContentTitle("Test NotificationBuilder Title")
        .setContentText("Test NotificationBuilder Text")
        .setAutoCancel(true)
        .build()

    public open class TestNotificationProvider : NotificationProvider {

        public var notification: Notification? = null
        public var tag: String? = null

        override fun onCreateNotificationArguments(
            context: Context, message: PushMessage
        ): NotificationArguments {
            return newBuilder(message).setNotificationChannelId(TEST_NOTIFICATION_CHANNEL_ID)
                .setNotificationId(tag, TEST_NOTIFICATION_ID).build()
        }

        override fun onCreateNotification(
            context: Context, arguments: NotificationArguments
        ): NotificationResult {
            return if (notification != null) {
                notification(
                    notification!!
                )
            } else {
                cancel()
            }
        }

        override fun onNotificationCreated(
            context: Context, notification: Notification, arguments: NotificationArguments
        ) {
        }
    }

    public companion object {

        private const val TEST_NOTIFICATION_ID = 123
        private const val TEST_NOTIFICATION_CHANNEL_ID = "Test notification channel"
    }
}
