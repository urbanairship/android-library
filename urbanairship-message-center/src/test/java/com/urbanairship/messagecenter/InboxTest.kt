/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.Predicate
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.UAirship
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.channel.AirshipChannelListener
import com.urbanairship.channel.ChannelRegistrationPayload
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.messagecenter.Inbox.FetchMessagesCallback
import com.urbanairship.mockk.clearInvocations
import com.urbanairship.remoteconfig.RemoteAirshipConfig
import com.urbanairship.remoteconfig.RemoteConfig
import java.util.concurrent.Executor
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class InboxTest {

    private val testDispatcher = StandardTestDispatcher()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val mainLooper: ShadowLooper = shadowOf(Looper.getMainLooper())

    private val mockUser = mockk<User>(relaxUnitFun = true) {}
    private val mockDispatcher = mockk<JobDispatcher>(relaxUnitFun = true) {}
    private val mockChannel = mockk<AirshipChannel>(relaxUnitFun = true) {}
    private val mockMessageDao = mockk<MessageDao>(relaxUnitFun = true) {}
    private val spyActivityMonitor = spyk(GlobalActivityMonitor.shared(context))

    private val dataStore = PreferenceDataStore.inMemoryStore(context)
    private val privacyManager = PrivacyManager(
        dataStore = dataStore,
        defaultEnabledFeatures = PrivacyManager.Feature.ALL
    )
    private val runtimeConfig = TestAirshipRuntimeConfig(
        RemoteConfig(
            RemoteAirshipConfig(
                "https://remote-data",
                "https://example.com",
                "https://wallet",
                "https://analytics",
                "https://metered-usage"
            )
        )
    )

    private val inbox = Inbox(
        dataStore = dataStore,
        jobDispatcher = mockDispatcher,
        user = mockUser,
        messageDao = mockMessageDao,
        activityMonitor = spyActivityMonitor,
        airshipChannel = mockChannel,
        privacyManager = privacyManager,
        config = runtimeConfig,
        dispatcher = testDispatcher
    )

    private var testPredicate: Predicate<Message> = Predicate<Message> { message ->
        val substring = message.messageId.replace("_message_id", "")
        val index = substring.toInt()
        // Only the "even" messages
        index % 2 == 0
    }

    private val messageEntities = mutableListOf<MessageEntity>()

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)

        MessageCenterTestUtils.setup()

        inbox.setEnabled(true)

        // Populate the MCRAP database with 10 messages
        for (i in 0..9) {
            val message = MessageCenterTestUtils.createMessage("${i + 1}_message_id", null, false)
            val entity = requireNotNull(
                MessageEntity.createMessageFromPayload(message.messageId, message.rawMessageJson)
            )
            messageEntities.add(entity)
        }

        // Put some expired messages in there (these should not show up after refresh)
        for (i in 10..14) {
            val message = MessageCenterTestUtils.createMessage("${i + 1}_message_id", null, true)
            val entity = requireNotNull(
                MessageEntity.createMessageFromPayload(message.messageId, message.rawMessageJson)
            )
            messageEntities.add(entity)
        }

        coEvery { mockMessageDao.getMessages() } returns messageEntities

        runBlocking {
            inbox.refresh(false)
        }

        clearInvocations(mockMessageDao)
    }

    @After
    public fun teardown() {
        Dispatchers.resetMain()
    }

    /** Test init dispatches the user update job if necessary. */
    @Test
    public fun testInitUserShouldUpdate() {
        every { mockUser.shouldUpdate() } returns true

        inbox.init()

        verify {
            mockDispatcher.dispatch(withArg { jobInfo ->
                assertEquals(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE, jobInfo.action)
            })
        }
    }

    /** Test channel registration extender adds the user id. */
    @Test
    public fun testChannelRegistrationDisabledTokenRegistration() {
        every { mockUser.shouldUpdate() } returns false

        val argument = slot<AirshipChannel.Extender.Blocking>()

        inbox.init()

        verify { mockChannel.addChannelRegistrationPayloadExtender(capture(argument)) }

        val extender = argument.captured
        assertNotNull(extender)

        every { mockUser.id } returns "cool"

        val builder = ChannelRegistrationPayload.Builder()
        val payload = extender.extend(builder).build()
        val expected = ChannelRegistrationPayload.Builder().setUserId("cool").build()

        assertEquals(expected, payload)
    }

    /** Test channel creation updates the user. */
    @Test
    public fun testChannelCreateUpdatesUser() {
        every { mockUser.shouldUpdate() } returns false

        val argument = slot<AirshipChannelListener>()

        inbox.init()

        verify { mockChannel.addChannelListener(capture(argument)) }

        val listener = argument.captured
        assertNotNull(listener)

        clearInvocations(mockDispatcher)
        listener.onChannelCreated("some-channel")

        verify {
            mockDispatcher.dispatch(withArg { jobInfo ->
                assertEquals(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE, jobInfo.action)
            })
        }
    }

    /** Test user updates refresh the inbox. */
    @Test
    public fun testUserUpdateRefreshesInbox() {
        every { mockUser.shouldUpdate() } returns false

        val argument = slot<User.Listener>()

        inbox.init()

        verify { mockUser.addListener(capture(argument)) }

        val listener = argument.captured
        assertNotNull(listener)

        clearInvocations(mockDispatcher)

        listener.onUserUpdated(true)

        verify {
            mockDispatcher.dispatch(withArg { jobInfo ->
                assertEquals(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE, jobInfo.action)
            })
        }
    }

    /** Tests the inbox reports the correct number of messages. */
    @Test
    public fun testNewRichPushInbox() {
        assertEquals(10, inbox.count)
        assertEquals(10, inbox.unreadCount)
        assertEquals(0, inbox.readCount)
    }

    /** Test mark messages are marked deleted in the database and the inbox. */
    @Test
    public fun testMarkMessagesDeleted() {
        assertEquals(10, inbox.count)

        val deletedIds = setOf("1_message_id", "3_message_id", "6_message_id")

        inbox.deleteMessages(deletedIds)

        // Should have 3 less messages
        assertEquals(7, inbox.count.toLong())
        assertEquals(7, inbox.unreadCount.toLong())
        assertEquals(0, inbox.readCount.toLong())
        for (deletedId: String? in deletedIds) {
            assertFalse(inbox.messageIds.contains(deletedId))
        }
    }

    /** Test mark messages are marked read in the database and the inbox. */
    @Test
    public fun testMarkMessagesRead() {
        val markedReadIds = setOf("1_message_id", "3_message_id", "6_message_id")

        inbox.markMessagesRead(markedReadIds)
        assertEquals(3, inbox.readCount.toLong())

        // Should have 3 read messages
        assertEquals(10, inbox.count.toLong())
        assertEquals(7, inbox.unreadCount.toLong())
        assertEquals(3, inbox.readCount.toLong())

        val readMessages = createIdToMessageMap(inbox.getReadMessages())
        val unreadMessages = createIdToMessageMap(inbox.getUnreadMessages())

        // Verify the read message are in the right lists
        for (readId: String in markedReadIds) {
            assertTrue(readMessages.containsKey(readId))
            assertFalse(unreadMessages.containsKey(readId))
        }
    }

    /** Test mark messages are marked unread in the database and the inbox. */
    @Test
    public fun testMarkMessagesUnread() {
        val messageIds = setOf("1_message_id", "3_message_id", "6_message_id")

        // Mark messages read
        inbox.markMessagesRead(messageIds)
        assertEquals(3, inbox.readCount.toLong())
        assertEquals(7, inbox.unreadCount.toLong())

        // Mark messages as unread
        inbox.markMessagesUnread(messageIds)
        assertEquals(10, inbox.count.toLong())
        assertEquals(10, inbox.unreadCount.toLong())
        assertEquals(0, inbox.readCount.toLong())
    }

    /** Test fetch messages starts the AirshipService. */
    @Test
    public fun testFetchMessages() {
        inbox.fetchMessages()

        verify {
            mockDispatcher.dispatch(withArg { jobInfo ->
                assertEquals(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE, jobInfo.action)
            })
        }
    }

    /** Test fetching messages skips triggering the rich push service if already refreshing. */
    @Test
    public fun testRefreshMessagesAlreadyRefreshing() {
        // Start refreshing messages
        inbox.fetchMessages()

        // Try to refresh again
        inbox.fetchMessages()

        verify(exactly = 1) {
            mockDispatcher.dispatch(withArg { jobInfo ->
                assertEquals(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE, jobInfo.action)
            })
        }
    }

    /** Test multiple fetch requests only performs a single request if the first one has yet to finish. */
    @Test
    public fun testRefreshMessageResponse() {
        val callback = mockk<FetchMessagesCallback>(relaxUnitFun = true)

        // Start refreshing messages
        inbox.fetchMessages()

        // Force another update
        inbox.fetchMessages(callback)

        // Verify we dispatched only 1 job
        verify(exactly = 1) {
            mockDispatcher.dispatch(withArg { jobInfo ->
                assertEquals(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE, jobInfo.action)
                assertEquals(JobInfo.REPLACE, jobInfo.conflictStrategy)
            })
        }
    }

    /** Test fetch message request with a callback */
    @Test
    public fun testRefreshMessagesWithCallback() {
        val callback = mockk<FetchMessagesCallback>(relaxUnitFun = true)

        inbox.fetchMessages(callback)

        verify {
            mockDispatcher.dispatch(withArg { jobInfo ->
                assertEquals(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE, jobInfo.action)
            })
        }

        inbox.onUpdateMessagesFinished(true)
        mainLooper.runToEndOfTasks()

        verify { callback.onFinished(true) }
    }

    /** Test failed fetch message request with a callback */
    @Test
    public fun testFetchMessagesFailWithCallback() {
        val callback = mockk<FetchMessagesCallback>(relaxUnitFun = true)

        inbox.fetchMessages(callback)

        verify {
            mockDispatcher.dispatch(withArg { jobInfo ->
                assertEquals(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE, jobInfo.action)
            })
        }

        inbox.onUpdateMessagesFinished(false)
        mainLooper.runToEndOfTasks()

        verify { callback.onFinished(false) }
    }

    /** Test canceling the fetch message request with a callback */
    @Test
    public fun testFetchMessagesCallbackCanceled() {
        val callback = mockk<FetchMessagesCallback>(relaxUnitFun = true)

        val cancelable = inbox.fetchMessages(callback)

        assertNotNull(cancelable)
        cancelable.cancel()

        verify {
            mockDispatcher.dispatch(withArg { jobInfo ->
                assertEquals(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE, jobInfo.action)
            })
        }

        inbox.onUpdateMessagesFinished(false)

        verify { callback wasNot Called }

        confirmVerified(callback)
    }

    /** Test getting messages with or without a predicate */
    @Test
    public fun testGetMessages() {
        // regular style
        val messages = inbox.messages
        assertEquals(messages.size, inbox.count)

        // filtered style
        val filteredMessages = inbox.getMessages(testPredicate)
        assertEquals(filteredMessages.size, inbox.count / 2)
    }

    @Test
    public fun testGetUnreadMessages() {
        val messageIds = (1..4).map { "${it}_message_id" }.toSet()

        // Mark messages read
        inbox.markMessagesRead(messageIds)

        val unreadMessages = inbox.getUnreadMessages()
        assertEquals(unreadMessages.size.toLong(), 6)

        val filteredMessages = inbox.getUnreadMessages(testPredicate)
        assertEquals(filteredMessages.size.toLong(), 3)

        for (message: Message in filteredMessages) {
            val substring = message.messageId.replace("_message_id", "")
            val index = substring.toInt()

            assertEquals((index % 2), 0)
        }
    }

    @Test
    public fun testGetReadMessages() {
        val messageIds = (1..4).map { "${it}_message_id" }.toSet()

        // Mark messages read
        inbox.markMessagesRead(messageIds)

        val readMessages = inbox.getReadMessages()
        assertEquals(readMessages.size, 4)

        val filteredMessages = inbox.getReadMessages(testPredicate)
        assertEquals(filteredMessages.size, 2)

        for (message: Message in filteredMessages) {
            val substring = message.messageId.replace("_message_id", "")
            val index = substring.toInt()

            assertEquals((index % 2).toLong(), 0)
        }
    }

    /** Test init doesn't update the user or refresh if `FEATURE_MESSAGE_CENTER` is disabled. */
    @Test
    public fun testInitWhenDisabledDispatchesNoJobs() {
        inbox.setEnabled(false)
        inbox.init()

        verify(exactly = 0) { mockDispatcher.dispatch(any()) }
    }

    /** Verify that calls to `onPerformJob` are no-ops if `FEATURE_MESSAGE_CENTER` is disabled. */
    @Test
    public fun testOnPerformJobWhenDisabled() {
        val jobHandler = mockk<InboxJobHandler>()

        inbox.setEnabled(false)
        inbox.inboxJobHandler = jobHandler

        val jobResult = inbox.onPerformJob(mockk<UAirship>(), mockk<JobInfo>())

        assertEquals(JobResult.SUCCESS, jobResult)

        verify(exactly = 0) { jobHandler.performJob(any<JobInfo>()) }
    }

    /** Verify updateEnabledState when disabled. */
    @Test
    public fun testUpdateEnabledStateNotEnabled(): TestResult = runTest {
        inbox.setEnabled(false)
        inbox.updateEnabledState()

        advanceUntilIdle()

        coVerify {
            mockMessageDao.deleteAllMessages()
            spyActivityMonitor.removeApplicationListener(any())
            mockChannel.removeChannelListener(any())
            mockChannel.removeChannelRegistrationPayloadExtender(any())
            mockUser.removeListener(any())
        }
    }

    /**
     * Verify updateEnabledState when enabled.
     */
    @Test
    public fun testUpdateEnabledStateEnabled(): TestResult = runTest {
        every { mockUser.shouldUpdate() } returns false

        inbox.setEnabled(true)
        inbox.updateEnabledState()
        // Update again to make sure that we don't restart the Inbox if already started.
        inbox.updateEnabledState()

        advanceUntilIdle()

        coVerify {
            mockUser.addListener(any())
            spyActivityMonitor.addApplicationListener(any())
            mockMessageDao.getMessages()
            mockChannel.addChannelListener(any())
            mockUser.shouldUpdate()
            mockChannel.addChannelRegistrationPayloadExtender(any())
        }
    }

    private companion object {

        /**
         * Helper method to convert a list of rich push messages
         * to a map of message ids to messages
         *
         * @param messages List of messages to convert
         * @return A map of rich push messages
         */
        private fun createIdToMessageMap(
            messages: List<Message>
        ): Map<String, Message> = messages.associateBy { it.messageId }
    }
}
