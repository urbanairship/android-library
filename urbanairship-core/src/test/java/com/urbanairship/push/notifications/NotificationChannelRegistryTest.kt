/* Copyright Airship and Contributors */
package com.urbanairship.push.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.urbanairship.BaseTestCase
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert
import org.junit.Test
import org.robolectric.annotation.Config

class NotificationChannelRegistryTest : BaseTestCase() {

    private val notificationManager: NotificationManager = mockk(relaxed = true)
    private val dataManager: NotificationChannelRegistryDataManager = mockk(relaxed = true)
    private val context: Context = mockk {
        every { getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
    }
    private val channelRegistry: NotificationChannelRegistry = NotificationChannelRegistry(
        context = context,
        dataManager = dataManager,
        executor = { command -> command.run() })

    val channel: NotificationChannel = NotificationChannel("test", "Test Channel", NotificationManagerCompat.IMPORTANCE_HIGH)
    val otherChannel: NotificationChannel = NotificationChannel("test2", "Test Channel 2", NotificationManagerCompat.IMPORTANCE_LOW)
    val channelCompat: NotificationChannelCompat = NotificationChannelCompat("test", "Test Channel", NotificationManagerCompat.IMPORTANCE_HIGH)
    val otherChannelCompat: NotificationChannelCompat = NotificationChannelCompat("test2", "Test Channel 2", NotificationManagerCompat.IMPORTANCE_LOW)

    @Test
    @Config(sdk = [25])
    fun testGetNotificationChannelAsyncPreOreo() {
        every { dataManager.getChannel("test") } returns channelCompat
        val result = channelRegistry.getNotificationChannel("test")

        verify { dataManager.getChannel(any()) }
        Assert.assertEquals(channelCompat, result.result)
    }

    @Test
    fun testGetNotificationChannelAsync() {
        every { notificationManager.getNotificationChannel("test") } returns channel
        val result = channelRegistry.getNotificationChannel("test")

        verify { notificationManager.getNotificationChannel(any()) }
        Assert.assertEquals(channelCompat, result.result)
    }

    @Test
    @Config(sdk = [25])
    fun testCreateNotificationChannelPreOreo() {
        channelRegistry.createNotificationChannel(channelCompat)
        verify { dataManager.createChannel(channelCompat) }
    }

    @Test
    fun testCreateNotificationChannel() {
        channelRegistry.createNotificationChannel(channelCompat)
        verify { notificationManager.createNotificationChannel(channel) }
    }

    @Test
    fun testCreateDeferredChannel() {
        channelRegistry.createDeferredNotificationChannel(channelCompat)
        verify { notificationManager wasNot Called }
    }

    @Test
    fun testGetNotificationChannelCreatesRealChannel() {
        every { notificationManager.getNotificationChannel(channelCompat.id) } returns null
        every { dataManager.getChannel(channelCompat.id) } answers { channelCompat }
        channelRegistry.getNotificationChannel(channelCompat.id)
        verify { notificationManager.createNotificationChannel(channelCompat.toNotificationChannel()) }
    }

    @Test
    @Config(sdk = [25])
    fun testDeleteNotificationChannelPreOreo() {
        channelRegistry.deleteNotificationChannel("test")
        verify { dataManager.deleteChannel("test") }
    }

    @Test
    fun testDeleteNotificationChannel() {
        channelRegistry.deleteNotificationChannel("test")
        verify { notificationManager.deleteNotificationChannel("test") }
    }
}
