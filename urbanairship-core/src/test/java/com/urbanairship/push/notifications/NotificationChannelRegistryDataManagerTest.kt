package com.urbanairship.push.notifications

import android.app.Notification
import android.app.NotificationManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestApplication
import org.junit.After
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationChannelRegistryDataManagerTest {

    private var dataManager = NotificationChannelRegistryDataManager(
        context = TestApplication.getApplication(),
        appKey = "appKey",
        dbName = "test")

    private var channel1 = NotificationChannelCompat(
        id = "test",
        name = "Test Channel",
        importance = NotificationManager.IMPORTANCE_HIGH)
        .also {
            it.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            it.sound = Uri.parse("cool://sound")
            it.group = "group"
            it.description = "Test Notification Channel"
        }

    private var channel2 = NotificationChannelCompat(
        id = "test2",
        name = "Test Channel 2",
        importance = NotificationManager.IMPORTANCE_LOW)
        .also {
            it.lightColor = 234
            it.enableVibration(true)
            it.enableLights(true)
            it.group = "other group"
            it.bypassDnd = true
        }

    @After
    fun teardown() {
        dataManager.deleteChannels()
    }

    @Test
    fun testCreateChannel() {
        val success = dataManager.createChannel(channel1)
        Assert.assertTrue(success)
    }

    @Test
    fun testCreateChannelUpserts() {
        // Initial create
        dataManager.createChannel(channel1)
        Assert.assertEquals(1, dataManager.channels.size.toLong())

        // Update the channel object and create it again
        val updatedChannel1 = NotificationChannelCompat.fromJson(channel1.toJsonValue())
            ?: throw IllegalStateException("Channel should not be null")

        updatedChannel1.description = "The same, but different..."
        dataManager.createChannel(updatedChannel1)

        val channels = dataManager.channels
        // Verify that we didn't create a duplicate
        Assert.assertEquals(1, channels.size.toLong())
        // Verify that the existing channel was updated with the new description
        Assert.assertEquals(setOf(updatedChannel1), channels)
    }

    @Test
    fun testGetChannel() {
        dataManager.createChannel(channel1)
        Assert.assertEquals(channel1, dataManager.getChannel(channel1.id))
    }

    @Test
    fun testGetChannels() {
        dataManager.createChannel(channel1)
        dataManager.createChannel(channel2)

        val channels = setOf(channel1, channel2)

        val otherChannels = dataManager.channels
        Assert.assertEquals(channels, otherChannels)
    }

    @Test
    fun testDeleteChannel() {
        dataManager.createChannel(channel1)
        Assert.assertTrue(dataManager.deleteChannel(channel1.id))
    }
}
