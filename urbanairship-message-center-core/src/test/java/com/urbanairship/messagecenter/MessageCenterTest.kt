/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.UAirship
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.messagecenter.core.Inbox
import com.urbanairship.messagecenter.core.MessageCenter
import com.urbanairship.messagecenter.core.MessageCenter.OnShowMessageCenterListener
import com.urbanairship.mockk.clearInvocations
import com.urbanairship.push.PushListener
import com.urbanairship.push.PushManager
import com.urbanairship.push.PushMessage
import com.urbanairship.remoteconfig.RemoteAirshipConfig
import com.urbanairship.remoteconfig.RemoteConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowApplication

/** Tests for [MessageCenter] */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class MessageCenterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val testDispatcher = StandardTestDispatcher()
    private val unconfinedTestDispatcher = UnconfinedTestDispatcher()

    private val dataStore = PreferenceDataStore.inMemoryStore(context)
    private val shadowApplication: ShadowApplication = Shadows.shadowOf(context as Application?)
    private val privacyManager = mockk<PrivacyManager>(relaxUnitFun = true) {
       every { isEnabled(PrivacyManager.Feature.MESSAGE_CENTER) } returns true
    }
    private val pushManager = mockk<PushManager>(relaxUnitFun = true) {}
    private val inbox = mockk<Inbox>(relaxUnitFun = true) {
        coEvery { fetchMessages() } returns true
    }
    private val onShowMessageCenterListener = mockk<OnShowMessageCenterListener> {}
    private val config = TestAirshipRuntimeConfig()

    private val messageCenter: MessageCenter = MessageCenter(
        context = context,
        dataStore = dataStore,
        config = config,
        privacyManager = privacyManager,
        inbox = inbox,
        pushManager = pushManager,
        dispatcher = unconfinedTestDispatcher
    )

    private lateinit var pushListener: PushListener
    private lateinit var privacyManagerListener: PrivacyManager.Listener

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
        messageCenter.initialize()

        val pushListenerSlot = slot<PushListener>()
        verify { pushManager.addInternalPushListener(capture(pushListenerSlot)) }
        pushListener = pushListenerSlot.captured

        val privacyListenerSlot = slot<PrivacyManager.Listener>()
        verify { privacyManager.addListener(capture(privacyListenerSlot)) }
        privacyManagerListener = privacyListenerSlot.captured

        val messageCenterActivityClass = "com.urbanairship.messagecenter.ui.MessageCenterActivity"
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testShowMessageCenterListener() {
        val listener = mockk<OnShowMessageCenterListener> {
            every { onShowMessageCenter(null) } returns true
        }

        messageCenter.setOnShowMessageCenterListener(listener)
        messageCenter.showMessageCenter()

        verify { listener.onShowMessageCenter(null) }
        assertNull(shadowApplication.nextStartedActivity)
    }

    @Test
    public fun testShowMessageListener() {
        val listener = mockk<OnShowMessageCenterListener> {
            every { onShowMessageCenter("id") } returns true
        }

        messageCenter.setOnShowMessageCenterListener(listener)
        messageCenter.showMessageCenter("id")

        verify { listener.onShowMessageCenter("id") }
        assertNull(shadowApplication.nextStartedActivity)
    }

    @Test
    public fun testParseMessageId() {
        val intent = Intent(MessageCenter.VIEW_MESSAGE_INTENT_ACTION, Uri.parse("message:cool"))
        assertEquals("cool", MessageCenter.parseMessageId(intent))

        intent.setAction(MessageCenter.VIEW_MESSAGE_CENTER_INTENT_ACTION)
        assertEquals("cool", MessageCenter.parseMessageId(intent))

        intent.setAction("SOME OTHER ACTION")
        assertNull(MessageCenter.parseMessageId(intent))

        intent.setAction(MessageCenter.VIEW_MESSAGE_CENTER_INTENT_ACTION)
            .setData(Uri.parse("WHAT"))
        assertNull(MessageCenter.parseMessageId(intent))
    }

    @Test
    public fun testPushListener(): TestResult = runTest {
        val message = PushMessage(mapOf(PushMessage.EXTRA_RICH_PUSH_ID to "messageID"))
        coEvery { inbox.getMessage("messageID") } returns null

        pushListener.onPushReceived(message, true)
        advanceUntilIdle()

        coVerify { inbox.fetchMessages() }
    }

    @Test
    public fun testUrlConfigUpdateCallback() {
        val remoteConfig = RemoteConfig(
            RemoteAirshipConfig(
                "https://remote-data",
                "https://device",
                "https://wallet",
                "https://analytics",
                "https://metered-usage"
            )
        )
        config.updateRemoteConfig(remoteConfig)

        verify { inbox.dispatchUpdateUserJob(true) }
    }

    @Test
    public fun testPrivacyManagerListenerUpdatesEnabledState() {
        // Clear setup invocations
        clearInvocations(pushManager, inbox)

        every { privacyManager.isEnabled(PrivacyManager.Feature.MESSAGE_CENTER) } returns false

        privacyManagerListener.onEnabledFeaturesChanged()

        verify(exactly = 1) {
            inbox.setEnabled(false)
            inbox.updateEnabledState()
        }
    }

    @Test
    public fun testUpdateEnabledStateWhenEnabled() {
        // Clear setup invocations
        clearInvocations(pushManager, inbox)

        every { privacyManager.isEnabled(PrivacyManager.Feature.MESSAGE_CENTER) } returns true

        messageCenter.updateInboxEnabledState()

        verify {
            inbox.setEnabled(true)
            inbox.updateEnabledState()
        }

        verify(exactly = 0) { pushManager.addInternalPushListener(any()) }
    }

    @Test
    public fun testUpdateEnabledStateWhenDisabled() {
        // Clear setup invocations
        clearInvocations(pushManager, inbox)

        every { privacyManager.isEnabled(PrivacyManager.Feature.MESSAGE_CENTER) } returns false

        messageCenter.updateInboxEnabledState()

        verify {
            inbox.setEnabled(false)
            inbox.updateEnabledState()
            // Verify that MessageCenter was torn down
            inbox.tearDown()
            pushManager.removePushListener(any())
        }
    }

    @Test
    public fun testShowMessageCenterWhenDisabled() {
        every { privacyManager.isEnabled(PrivacyManager.Feature.MESSAGE_CENTER) } returns false

        messageCenter.showMessageCenter()

        assertNull(shadowApplication.nextStartedActivity)
    }

    @Test
    public fun testPerformJobWhenEnabled() {
        val (airship, jobInfo) = mockk<UAirship>() to mockk<JobInfo>()
        every { inbox.onPerformJob(airship, jobInfo) } returns JobResult.SUCCESS
        every { privacyManager.isEnabled(PrivacyManager.Feature.MESSAGE_CENTER) } returns true

        val result = messageCenter.onPerformJob(airship, jobInfo)

        assertEquals(JobResult.SUCCESS, result)
        verify { inbox.onPerformJob(airship, jobInfo) }
    }

    @Test
    public fun testPerformJobWhenDisabled() {
        every { privacyManager.isEnabled(PrivacyManager.Feature.MESSAGE_CENTER) } returns false

        val result = messageCenter.onPerformJob(mockk<UAirship>(), mockk<JobInfo>())

        assertEquals(JobResult.SUCCESS, result)
        verify(exactly = 0) { inbox.onPerformJob(any(), any()) }
    }

    @Test
    public fun testDeepLinkMessageCenter() {
        every { onShowMessageCenterListener.onShowMessageCenter(null) } returns false

        messageCenter.setOnShowMessageCenterListener(onShowMessageCenterListener)

        val deepLink = Uri.parse("uairship://message_center")
        assertTrue(messageCenter.onAirshipDeepLink(deepLink))
        verify { onShowMessageCenterListener.onShowMessageCenter(null) }
    }

    @Test
    public fun testDeepLinkMessageCenterTrailingSlash() {
        every { onShowMessageCenterListener.onShowMessageCenter(null) } returns false

        messageCenter.setOnShowMessageCenterListener(onShowMessageCenterListener)

        val deepLink = Uri.parse("uairship://message_center/")
        assertTrue(messageCenter.onAirshipDeepLink(deepLink))
        verify { onShowMessageCenterListener.onShowMessageCenter(null) }
    }

    @Test
    public fun testDeepLinkMessage() {
        every { onShowMessageCenterListener.onShowMessageCenter("cool-message") } returns false

        messageCenter.setOnShowMessageCenterListener(onShowMessageCenterListener)

        val deepLink = Uri.parse("uairship://message_center/cool-message")
        assertTrue(messageCenter.onAirshipDeepLink(deepLink))
        verify { onShowMessageCenterListener.onShowMessageCenter("cool-message") }
    }

    @Test
    public fun testDeepLinkMessageTrailingSlash() {
        every { onShowMessageCenterListener.onShowMessageCenter("cool-message") } returns false

        messageCenter.setOnShowMessageCenterListener(onShowMessageCenterListener)

        val deepLink = Uri.parse("uairship://message_center/cool-message/")
        assertTrue(messageCenter.onAirshipDeepLink(deepLink))
        verify { onShowMessageCenterListener.onShowMessageCenter("cool-message") }
    }

    @Test
    public fun testInvalidDeepLinks() {
        messageCenter.setOnShowMessageCenterListener(onShowMessageCenterListener)

        val wrongHost = Uri.parse("uairship://what/cool-message/")
        assertFalse(messageCenter.onAirshipDeepLink(wrongHost))

        val wrongArgs = Uri.parse("uairship://message_center/cool-message/what")
        assertFalse(messageCenter.onAirshipDeepLink(wrongArgs))

        verify(exactly = 0) { onShowMessageCenterListener.onShowMessageCenter(any()) }
    }
}
