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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config

class NotificationChannelRegistryTest : BaseTestCase() {

    private val notificationManager: NotificationManager = mockk(relaxed = true)
    private val dataManager: NotificationChannelRegistryDataManager = mockk(relaxed = true)
    private val context: Context = mockk {
        every { getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
    }

    val testDispatcher = StandardTestDispatcher()
    private val channelRegistry: NotificationChannelRegistry = NotificationChannelRegistry(
        context = context,
        dataManager = dataManager,
        dispatcher = testDispatcher
    )

    val channel: NotificationChannel = NotificationChannel("test", "Test Channel", NotificationManagerCompat.IMPORTANCE_HIGH)
    val otherChannel: NotificationChannel = NotificationChannel("test2", "Test Channel 2", NotificationManagerCompat.IMPORTANCE_LOW)
    val channelCompat: NotificationChannelCompat = NotificationChannelCompat("test", "Test Channel", NotificationManagerCompat.IMPORTANCE_HIGH)
    val otherChannelCompat: NotificationChannelCompat = NotificationChannelCompat("test2", "Test Channel 2", NotificationManagerCompat.IMPORTANCE_LOW)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @Test
    @Config(sdk = [25])
    fun testGetNotificationChannelAsyncPreOreo(): TestResult = runTest {
        every { dataManager.getChannel("test") } returns channelCompat
        val result = channelRegistry.getNotificationChannel("test")
        testDispatcher.scheduler.advanceUntilIdle()

        verify { dataManager.getChannel(any()) }
        Assert.assertEquals(channelCompat, result)
    }

    @Test
    fun testGetNotificationChannelAsync(): TestResult = runTest {
        every { notificationManager.getNotificationChannel("test") } returns channel
        val result = channelRegistry.getNotificationChannel("test")
        testDispatcher.scheduler.advanceUntilIdle()

        verify { notificationManager.getNotificationChannel(any()) }
        Assert.assertEquals(channelCompat, result)
    }

    @Test
    @Config(sdk = [25])
    fun testCreateNotificationChannelPreOreo() {
        channelRegistry.createNotificationChannel(channelCompat)
        testDispatcher.scheduler.advanceUntilIdle()
        verify { dataManager.createChannel(channelCompat) }
    }

    @Test
    fun testCreateNotificationChannel() {
        channelRegistry.createNotificationChannel(channelCompat)
        testDispatcher.scheduler.advanceUntilIdle()
        verify { notificationManager.createNotificationChannel(channel) }
    }

    @Test
    fun testCreateDeferredChannel() {
        channelRegistry.createDeferredNotificationChannel(channelCompat)
        testDispatcher.scheduler.advanceUntilIdle()
        verify { notificationManager wasNot Called }
    }

    @Test
    fun testGetNotificationChannelCreatesRealChannel(): TestResult = runTest {
        every { notificationManager.getNotificationChannel(channelCompat.id) } returns null
        every { dataManager.getChannel(channelCompat.id) } answers { channelCompat }
        channelRegistry.getNotificationChannel(channelCompat.id)
        testDispatcher.scheduler.advanceUntilIdle()
        verify { notificationManager.createNotificationChannel(channelCompat.toNotificationChannel()) }
    }

    @Test
    @Config(sdk = [25])
    fun testDeleteNotificationChannelPreOreo() {
        channelRegistry.deleteNotificationChannel("test")
        testDispatcher.scheduler.advanceUntilIdle()
        verify { dataManager.deleteChannel("test") }
    }

    @Test
    fun testDeleteNotificationChannel() {
        channelRegistry.deleteNotificationChannel("test")
        testDispatcher.scheduler.advanceUntilIdle()
        verify { notificationManager.deleteNotificationChannel("test") }
    }
}
