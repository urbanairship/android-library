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
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config

public class NotificationChannelRegistryTest : BaseTestCase() {

    private val notificationManager: NotificationManager = mockk(relaxed = true)
    private val dataManager: NotificationChannelRegistryDataManager = mockk(relaxed = true)
    private val context: Context = mockk {
        every { getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
    }

    public val testDispatcher: TestDispatcher = StandardTestDispatcher()
    private val channelRegistry: NotificationChannelRegistry = NotificationChannelRegistry(
        context = context,
        dataManager = dataManager,
        dispatcher = testDispatcher
    )

    public val channel: NotificationChannel = NotificationChannel("test", "Test Channel", NotificationManagerCompat.IMPORTANCE_HIGH)
    public val otherChannel: NotificationChannel = NotificationChannel("test2", "Test Channel 2", NotificationManagerCompat.IMPORTANCE_LOW)
    public val channelCompat: NotificationChannelCompat = NotificationChannelCompat("test", "Test Channel", NotificationManagerCompat.IMPORTANCE_HIGH)
    public val otherChannelCompat: NotificationChannelCompat = NotificationChannelCompat("test2", "Test Channel 2", NotificationManagerCompat.IMPORTANCE_LOW)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @Test
    @Config(sdk = [25])
    public fun testGetNotificationChannelAsyncPreOreo(): TestResult = runTest {
        every { dataManager.getChannel("test") } returns channelCompat
        val result = channelRegistry.getNotificationChannel("test")
        testDispatcher.scheduler.advanceUntilIdle()

        verify { dataManager.getChannel(any()) }
        Assert.assertEquals(channelCompat, result)
    }

    @Test
    public fun testGetNotificationChannelAsync(): TestResult = runTest {
        every { notificationManager.getNotificationChannel("test") } returns channel
        val result = channelRegistry.getNotificationChannel("test")
        testDispatcher.scheduler.advanceUntilIdle()

        verify { notificationManager.getNotificationChannel(any()) }
        Assert.assertEquals(channelCompat, result)
    }

    @Test
    @Config(sdk = [25])
    public fun testCreateNotificationChannelPreOreo() {
        channelRegistry.createNotificationChannel(channelCompat)
        testDispatcher.scheduler.advanceUntilIdle()
        verify { dataManager.createChannel(channelCompat) }
    }

    @Test
    public fun testCreateNotificationChannel() {
        channelRegistry.createNotificationChannel(channelCompat)
        testDispatcher.scheduler.advanceUntilIdle()
        verify { notificationManager.createNotificationChannel(channel) }
    }

    @Test
    public fun testCreateDeferredChannel() {
        channelRegistry.createDeferredNotificationChannel(channelCompat)
        testDispatcher.scheduler.advanceUntilIdle()
        verify { notificationManager wasNot Called }
    }

    @Test
    public fun testGetNotificationChannelCreatesRealChannel(): TestResult = runTest {
        every { notificationManager.getNotificationChannel(channelCompat.id) } returns null
        every { dataManager.getChannel(channelCompat.id) } answers { channelCompat }
        channelRegistry.getNotificationChannel(channelCompat.id)
        testDispatcher.scheduler.advanceUntilIdle()
        verify { notificationManager.createNotificationChannel(channelCompat.toNotificationChannel()) }
    }

    @Test
    @Config(sdk = [25])
    public fun testDeleteNotificationChannelPreOreo() {
        channelRegistry.deleteNotificationChannel("test")
        testDispatcher.scheduler.advanceUntilIdle()
        verify { dataManager.deleteChannel("test") }
    }

    @Test
    public fun testDeleteNotificationChannel() {
        channelRegistry.deleteNotificationChannel("test")
        testDispatcher.scheduler.advanceUntilIdle()
        verify { notificationManager.deleteNotificationChannel("test") }
    }
}
