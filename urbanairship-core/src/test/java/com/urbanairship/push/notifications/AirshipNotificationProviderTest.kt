/* Copyright Airship and Contributors */
package com.urbanairship.push.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.os.bundleOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.TestApplication
import com.urbanairship.Airship
import com.urbanairship.push.PushMessage
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AirshipNotificationProviderTest {

    private var configOptions = AirshipConfigOptions.Builder()
        .setDevelopmentAppKey("appKey")
        .setDevelopmentAppSecret("appSecret")
        .setProductionAppSecret("appSecret")
        .setProductionAppKey("appKey")
        .setNotificationIcon(10)
        .setNotificationAccentColor(20)
        .setNotificationChannel("test_channel")
        .build()

    private val context = Airship.applicationContext
    private var provider = AirshipNotificationProvider(context, configOptions)

    private var defaultPushMessage = PushMessage(
        bundleOf(
            PushMessage.EXTRA_ALERT to "Test Push Alert!",
            PushMessage.EXTRA_PUSH_ID to "0a2027a0-1766-11e4-9db0-90e2ba287ae5",
            PushMessage.EXTRA_NOTIFICATION_TAG to "some-tag"
        )
    )

    @Before
    fun setup() {
        createChannel("test_channel")
    }

    /**
     * Test empty alert should result in a CANCEL status.
     */
    @Test
    fun testBuildNotificationNull() {
        val emptyPushMessage = PushMessage(bundleOf())

        val arguments = provider.onCreateNotificationArguments(context, emptyPushMessage)
        val result = provider.onCreateNotification(context, arguments)
        Assert.assertEquals(NotificationResult.Status.CANCEL, result.status)
    }

    /**
     * Test creating a notification.
     */
    @Test
    fun testBuildNotification() {
        val arguments = provider.onCreateNotificationArguments(context, defaultPushMessage)
        val result = provider.onCreateNotification(context, arguments)

        Assert.assertEquals(NotificationResult.Status.OK, result.status)
        Assert.assertNotNull(result.notification)
    }

    /**
     * Test the defaults.
     */
    @Test
    fun testDefaults() {
        Assert.assertEquals(10, provider.smallIcon)
        Assert.assertEquals(20, provider.defaultAccentColor)
        Assert.assertEquals("test_channel", provider.defaultNotificationChannelId)
    }

    /**
     * Test arguments when the channel is empty.
     */
    @Test
    fun testArgumentsDefaultChannel() {
        provider.defaultNotificationChannelId = "does not exist"
        val arguments = provider.onCreateNotificationArguments(context, defaultPushMessage)

        Assert.assertEquals(
            NotificationProvider.DEFAULT_NOTIFICATION_CHANNEL, arguments.notificationChannelId
        )
    }

    /**
     * Test the channel will fallback to the SDK default channel if the specified channel does not exist.
     */
    @Test
    fun testArgumentsFallbackChannel() {
        val arguments = provider.onCreateNotificationArguments(context, defaultPushMessage)

        Assert.assertEquals("test_channel", arguments.notificationChannelId)
        Assert.assertEquals(defaultPushMessage, arguments.message)
        Assert.assertEquals(
            AirshipNotificationProvider.TAG_NOTIFICATION_ID, arguments.notificationId
        )
        Assert.assertEquals("some-tag", arguments.notificationTag)
        Assert.assertFalse(arguments.requiresLongRunningTask)
    }

    /**
     * Test arguments when the channel is defined on the push message.
     */
    @Test
    fun testArgumentsWithChannel() {
        createChannel("cool-channel")

        val pushMessage = PushMessage(
            bundleOf(
                PushMessage.EXTRA_ALERT to "Test Push Alert!",
                PushMessage.EXTRA_PUSH_ID to "0a2027a0-1766-11e4-9db0-90e2ba287ae5",
                PushMessage.EXTRA_NOTIFICATION_CHANNEL to "cool-channel",
                PushMessage.EXTRA_NOTIFICATION_TAG to "some-tag"
            )
        )

        val arguments = provider.onCreateNotificationArguments(context, pushMessage)
        Assert.assertEquals("cool-channel", arguments.notificationChannelId)
        Assert.assertEquals(pushMessage, arguments.message)
        Assert.assertEquals(
            AirshipNotificationProvider.TAG_NOTIFICATION_ID, arguments.notificationId
        )
        Assert.assertEquals("some-tag", arguments.notificationTag)
        Assert.assertFalse(arguments.requiresLongRunningTask)
    }

    /**
     * Test overriding the small icon from a push.
     */
    @Test
    fun testOverrideSmallIcon() {
        val mockPush: PushMessage = mockk(relaxed = true) {
            every { alert } returns "alert"
            every { getIcon(context, any()) } returns 100
        }

        val arguments = provider.onCreateNotificationArguments(context, mockPush)
        val result = provider.onCreateNotification(context, arguments)

        Assert.assertEquals(NotificationResult.Status.OK, result.status)
        Assert.assertEquals(100, result.notification?.smallIcon?.resId)
    }

    private fun createChannel(channelId: String) {
        val manager = TestApplication.getApplication()
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(channelId, "test", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}
