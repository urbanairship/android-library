/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.app.Activity
import androidx.core.util.Consumer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestActivityMonitor
import com.urbanairship.permission.PermissionRequestResult
import com.urbanairship.permission.PermissionStatus
import com.urbanairship.push.NotificationsPermissionDelegate.PermissionRequestDelegate
import com.urbanairship.push.notifications.NotificationChannelRegistry
import java.util.UUID
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class NotificationsPermissionDelegateTest {

    private val defaultChannelId = UUID.randomUUID().toString()
    private val dataStore = PreferenceDataStore.inMemoryStore(
        ApplicationProvider.getApplicationContext()
    )
    private val channelRegistry: NotificationChannelRegistry = mockk(relaxed = true)
    private val notificationManager: AirshipNotificationManager = mockk(relaxed = true)
    private val permissionRequestDelegate: PermissionRequestDelegate = mockk()

    private val activityMonitor = TestActivityMonitor()

    private val delegate = NotificationsPermissionDelegate(
        defaultChannelId,
        dataStore,
        notificationManager,
        channelRegistry,
        activityMonitor,
        permissionRequestDelegate
    )
    private val testCheckConsumer = TestConsumer<PermissionStatus>()
    private val testRequestConsumer = TestConsumer<PermissionRequestResult>()

    @Test
    public fun testCheckStatusUnsupportedPrompt() {
        every { notificationManager.promptSupport } returns AirshipNotificationManager.PromptSupport.NOT_SUPPORTED

        var areNotificationsEnabled = true
        every { notificationManager.areNotificationsEnabled() } answers { areNotificationsEnabled }

        delegate.checkPermissionStatus(
            ApplicationProvider.getApplicationContext(), testCheckConsumer
        )
        Assert.assertEquals(PermissionStatus.GRANTED, testCheckConsumer.result)

        areNotificationsEnabled = false
        delegate.checkPermissionStatus(
            ApplicationProvider.getApplicationContext(), testCheckConsumer
        )
        Assert.assertEquals(PermissionStatus.DENIED, testCheckConsumer.result)
    }

    @Test
    public fun testRequestUnsupportedPrompt() {
        every { notificationManager.promptSupport } returns AirshipNotificationManager.PromptSupport.NOT_SUPPORTED
        var areNotificationsEnabled = true
        every { notificationManager.areNotificationsEnabled() } answers { areNotificationsEnabled }
        delegate.requestPermission(ApplicationProvider.getApplicationContext(), testRequestConsumer)
        Assert.assertEquals(PermissionStatus.GRANTED, testRequestConsumer.result?.permissionStatus)

        areNotificationsEnabled = false
        delegate.requestPermission(ApplicationProvider.getApplicationContext(), testRequestConsumer)
        Assert.assertEquals(PermissionStatus.DENIED, testRequestConsumer.result?.permissionStatus)
        Assert.assertTrue(testRequestConsumer.result?.isSilentlyDenied == true)
    }

    @Test
    public fun testCheckCompatPrompt() {
        every { notificationManager.promptSupport } returns AirshipNotificationManager.PromptSupport.COMPAT
        var areNotificationsEnabled = true
        every { notificationManager.areNotificationsEnabled() } answers { areNotificationsEnabled }
        delegate.checkPermissionStatus(
            ApplicationProvider.getApplicationContext(), testCheckConsumer
        )
        Assert.assertEquals(PermissionStatus.GRANTED, testCheckConsumer.result)

        areNotificationsEnabled = false
        delegate.checkPermissionStatus(
            ApplicationProvider.getApplicationContext(), testCheckConsumer
        )
        Assert.assertEquals(PermissionStatus.NOT_DETERMINED, testCheckConsumer.result)
    }

    @Test
    public fun testCheckCompatPromptAfterRequest() {
        every { notificationManager.promptSupport } returns AirshipNotificationManager.PromptSupport.COMPAT
        every { notificationManager.areNotificationsEnabled() } returns false

        delegate.requestPermission(
            context = ApplicationProvider.getApplicationContext(),
            callback = { _ -> }
        )
        activityMonitor.resumeActivity(Activity())

        delegate.checkPermissionStatus(
            ApplicationProvider.getApplicationContext(), testCheckConsumer
        )
        Assert.assertEquals(PermissionStatus.DENIED, testCheckConsumer.result)
    }

    @Test
    public fun testRequestCompatPrompt() {
        every { notificationManager.promptSupport } returns AirshipNotificationManager.PromptSupport.COMPAT
        var areNotificationsEnabled = true
        every { notificationManager.areNotificationsEnabled() } answers { areNotificationsEnabled }

        delegate.requestPermission(ApplicationProvider.getApplicationContext(), testRequestConsumer)
        Assert.assertEquals(PermissionStatus.GRANTED, testRequestConsumer.result?.permissionStatus)

        areNotificationsEnabled = false
        testCheckConsumer.result = null
        delegate.requestPermission(ApplicationProvider.getApplicationContext(), testRequestConsumer)

        // Waits for activity resume to check
        Assert.assertNull(testCheckConsumer.result)
        areNotificationsEnabled = true
        activityMonitor.resumeActivity(Activity())
        Assert.assertEquals(PermissionStatus.GRANTED, testRequestConsumer.result?.permissionStatus)
        Assert.assertFalse(testRequestConsumer.result?.isSilentlyDenied == true)
    }

    @Test
    public fun testRequestCompatPromptChannelsCreated() {
        every { notificationManager.promptSupport } returns AirshipNotificationManager.PromptSupport.COMPAT
        every { notificationManager.areNotificationsEnabled() } returns false
        every { notificationManager.areChannelsCreated() } returns true

        delegate.requestPermission(ApplicationProvider.getApplicationContext(), testRequestConsumer)

        Assert.assertEquals(PermissionStatus.DENIED, testRequestConsumer.result?.permissionStatus)
        Assert.assertTrue(testRequestConsumer.result?.isSilentlyDenied == true)
    }

    @Test
    public fun testRequestCompatPromptCreateChannel() {
        every { notificationManager.promptSupport } returns AirshipNotificationManager.PromptSupport.COMPAT
        every { notificationManager.areNotificationsEnabled() } returns false
        every { notificationManager.areChannelsCreated() } returns false

        delegate.requestPermission(
            context = ApplicationProvider.getApplicationContext(),
            callback = { _ -> }
        )

        coVerify { channelRegistry.getNotificationChannel(defaultChannelId) }
    }

    @Test
    public fun testCheckSupportedPrompt() {
        every { notificationManager.promptSupport } returns AirshipNotificationManager.PromptSupport.SUPPORTED
        var areNotificationsEnabled = true
        every { notificationManager.areNotificationsEnabled() } answers { areNotificationsEnabled }

        delegate.checkPermissionStatus(
            ApplicationProvider.getApplicationContext(), testCheckConsumer
        )
        Assert.assertEquals(PermissionStatus.GRANTED, testCheckConsumer.result)

        areNotificationsEnabled = false
        delegate.checkPermissionStatus(
            ApplicationProvider.getApplicationContext(), testCheckConsumer
        )
        Assert.assertEquals(PermissionStatus.NOT_DETERMINED, testCheckConsumer.result)
    }

    @Test
    public fun testCheckSupportedPromptAfterRequest() {
        every { permissionRequestDelegate.requestPermissions(any(), "android.permission.POST_NOTIFICATIONS", any()) } answers {
            val resultConsumer: Consumer<PermissionRequestResult> = thirdArg()
            resultConsumer.accept(PermissionRequestResult.denied(true))
        }

        every { notificationManager.promptSupport } returns AirshipNotificationManager.PromptSupport.SUPPORTED
        every { notificationManager.areNotificationsEnabled() } returns false

        delegate.requestPermission(
            context = ApplicationProvider.getApplicationContext(),
            callback = { _ ->}
        )
        activityMonitor.resumeActivity(Activity())

        delegate.checkPermissionStatus(
            ApplicationProvider.getApplicationContext(), testCheckConsumer
        )
        Assert.assertEquals(PermissionStatus.DENIED, testCheckConsumer.result)
    }

    @Test
    public fun testRequestSupportedPromptGranted() {
        every { permissionRequestDelegate.requestPermissions(any(), "android.permission.POST_NOTIFICATIONS", any()) } answers {
            val resultConsumer: Consumer<PermissionRequestResult> = thirdArg()
            resultConsumer.accept(PermissionRequestResult.granted())

        }

        every { notificationManager.promptSupport } returns AirshipNotificationManager.PromptSupport.SUPPORTED
        every { notificationManager.areNotificationsEnabled() } returns false

        delegate.requestPermission(ApplicationProvider.getApplicationContext(), testRequestConsumer)
        Assert.assertEquals(PermissionStatus.GRANTED, testRequestConsumer.result?.permissionStatus)
    }

    @Test
    public fun testRequestSupportedPromptDenied() {
        every { permissionRequestDelegate.requestPermissions(any(), "android.permission.POST_NOTIFICATIONS", any()) } answers {
            val resultConsumer: Consumer<PermissionRequestResult> = thirdArg()
            resultConsumer.accept(PermissionRequestResult.denied(false))

        }

        every { notificationManager.promptSupport } returns AirshipNotificationManager.PromptSupport.SUPPORTED
        every { notificationManager.areNotificationsEnabled() } returns false

        delegate.requestPermission(ApplicationProvider.getApplicationContext(), testRequestConsumer)
        Assert.assertEquals(PermissionStatus.DENIED, testRequestConsumer.result?.permissionStatus)
        Assert.assertFalse(testRequestConsumer.result?.isSilentlyDenied == true)
    }

    @Test
    public fun testRequestSupportedPromptSilentlyDenied() {
        every { permissionRequestDelegate.requestPermissions(any(), "android.permission.POST_NOTIFICATIONS", any()) } answers {
            val resultConsumer: Consumer<PermissionRequestResult> = thirdArg()
            resultConsumer.accept(PermissionRequestResult.denied(true))
        }

        every { notificationManager.promptSupport } returns AirshipNotificationManager.PromptSupport.SUPPORTED
        every { notificationManager.areNotificationsEnabled() } returns false

        delegate.requestPermission(ApplicationProvider.getApplicationContext(), testRequestConsumer)
        Assert.assertEquals(PermissionStatus.DENIED, testRequestConsumer.result?.permissionStatus)
        Assert.assertTrue(testRequestConsumer.result?.isSilentlyDenied == true)
    }

    private class TestConsumer<T> : Consumer<T> {
        var result: T? = null
        override fun accept(value: T) {
            this.result = value
        }
    }
}
