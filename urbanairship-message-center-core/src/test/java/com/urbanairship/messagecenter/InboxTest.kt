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
import com.urbanairship.TestClock
import com.urbanairship.TestTaskSleeper
import com.urbanairship.UAirship
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.channel.AirshipChannelListener
import com.urbanairship.channel.ChannelRegistrationPayload
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.messagecenter.core.Inbox.FetchMessagesCallback
import com.urbanairship.messagecenter.MessageCenterTestUtils.createMessage
import com.urbanairship.messagecenter.core.Inbox
import com.urbanairship.messagecenter.core.InboxJobHandler
import com.urbanairship.messagecenter.core.Message
import com.urbanairship.messagecenter.core.MessageDao
import com.urbanairship.messagecenter.core.MessageDatabase
import com.urbanairship.messagecenter.core.MessageEntity
import com.urbanairship.messagecenter.core.User
import com.urbanairship.mockk.clearInvocations
import com.urbanairship.remoteconfig.RemoteAirshipConfig
import com.urbanairship.remoteconfig.RemoteConfig
import java.util.Date
import kotlin.time.Duration.Companion.seconds
import app.cash.turbine.test
import io.mockk.Called
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
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
    private val spyActivityMonitor = spyk(GlobalActivityMonitor.shared(context))

    private val db = MessageDatabase.createInMemoryDatabase(context, testDispatcher)
    private val spyMessageDao: MessageDao = spyk(db.dao)

    private val clock = TestClock()
    private val taskSleeper = TestTaskSleeper(clock) { sleep ->
        clock.currentTimeMillis += sleep.inWholeMilliseconds
    }

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
        messageDao = spyMessageDao,
        activityMonitor = spyActivityMonitor,
        airshipChannel = mockChannel,
        privacyManager = privacyManager,
        config = runtimeConfig,
        taskSleeper = taskSleeper,
        clock = clock,
        dispatcher = testDispatcher
    )

    private var testPredicate: Predicate<Message> = Predicate<Message> { message ->
        val substring = message.id.replace("_message_id", "")
        val index = substring.toInt()
        // Only the "even" messages
        index % 2 == 0
    }


    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)

        MessageCenterTestUtils.setup()

        spyMessageDao.queryClock = clock

        inbox.setEnabled(true)
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
    public fun testNewRichPushInbox(): TestResult = runTest {
        insertTestMessages()

        assertEquals(10, inbox.getCount())
        assertEquals(10, inbox.getUnreadCount())
        assertEquals(0, inbox.getReadCount())
    }

    /** Test mark messages are marked deleted in the database and the inbox. */
    @Test
    public fun testMarkMessagesDeleted(): TestResult = runTest {
        insertTestMessages()

        assertEquals(10, inbox.getCount())
        advanceUntilIdle()

        val deletedIds = setOf("1_message_id", "3_message_id", "6_message_id")

        inbox.deleteMessages(deletedIds)
        mainLooper.runToEndOfTasks()
        advanceUntilIdle()

        // Should have 3 less messages
        val count = inbox.getCount()
        assertEquals(7, count)

        val unreadCount = inbox.getUnreadCount()
        assertEquals(7, unreadCount)

        val readCount = inbox.getReadCount()
        assertEquals(0, readCount)

        val messageIds = inbox.getMessageIds()

        for (deletedId: String? in deletedIds) {
            assertFalse(messageIds.contains(deletedId))
        }
    }

    /** Test mark messages are all marked deleted in the database and the inbox. */
    @Test
    public fun testMarkAllMessagesDeleted(): TestResult = runTest {
        insertTestMessages()

        assertEquals(10, inbox.getCount())
        advanceUntilIdle()

        inbox.deleteAllMessages()
        mainLooper.runToEndOfTasks()
        advanceUntilIdle()

        // Should have 0 messages
        val count = inbox.getCount()
        assertEquals(0, count)

        val unreadCount = inbox.getUnreadCount()
        assertEquals(0, unreadCount)

        val readCount = inbox.getReadCount()
        assertEquals(0, readCount)

        val messageIds = inbox.getMessageIds()
        assertEquals(0, messageIds.size)
    }

    /** Test mark messages are marked read in the database and the inbox. */
    @Test
    public fun testMarkMessagesRead(): TestResult = runTest {
        insertTestMessages()

        val markedReadIds = setOf("1_message_id", "3_message_id", "6_message_id")

        inbox.markMessagesRead(markedReadIds)
        advanceUntilIdle()

        assertEquals(3, inbox.getReadCount())

        // Should have 3 read messages
        assertEquals(10, inbox.getCount())
        assertEquals(7, inbox.getUnreadCount())
        assertEquals(3, inbox.getReadCount())

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
    public fun testMarkMessagesUnread(): TestResult = runTest {
        insertTestMessages()

        assertEquals(10, inbox.getCount())

        val messageIds = setOf("1_message_id", "3_message_id", "6_message_id")

        // Mark messages read
        inbox.markMessagesRead(messageIds)
        advanceUntilIdle()

        assertEquals(3, inbox.getReadCount())
        assertEquals(7, inbox.getUnreadCount())

        // Mark messages as unread
        inbox.markMessagesUnread(messageIds)
        advanceUntilIdle()

        assertEquals(10, inbox.getCount())
        assertEquals(10, inbox.getUnreadCount())
        assertEquals(0, inbox.getReadCount())
    }

    /** Test fetch messages starts the AirshipService. */
    @Test
    public fun testFetchMessages() {
        inbox.fetchMessages(null, null)

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
        inbox.fetchMessages(null)

        // Try to refresh again
        inbox.fetchMessages(null)

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
        inbox.fetchMessages(null)

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

    @Test
    public fun testRefreshOnMessageExpires(): TestResult = runTest {
        val now = 1000L

        clock.currentTimeMillis = now

        val messages = listOf(
            createMessage("no_expiry", expirationDate = null),
            createMessage("expires_soon", expirationDate = Date(now + 1000)),
            createMessage("expires_later", expirationDate = Date(now + 2000))
        ).mapNotNull {
            MessageEntity.createMessageFromPayload(it.id, it.rawMessageJson)
        }

        spyMessageDao.getMessagesFlow().test {
            // Sanity check
            val initial = awaitItem()
            assertEquals(0, initial.size)

            // Insert test messages
            spyMessageDao.insertMessages(messages)
            val inserted = awaitItem()
            assertEquals(3, inserted.size)

            // Notify the inbox
            inbox.onUpdateMessagesFinished(true)
            advanceUntilIdle()
            mainLooper.runToEndOfTasks()

            ensureAllEventsConsumed()
        }

        // Verify that we set up a sleep for the earliest expiration time
        assertEquals(1.seconds, taskSleeper.sleeps.firstOrNull())

        verify {
            mockDispatcher.dispatch(withArg { jobInfo ->
                assertEquals(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE, jobInfo.action)
            })
        }
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
    public fun testGetMessages(): TestResult = runTest {
        insertTestMessages()

        // regular style
        val messages = inbox.getMessages()
        advanceUntilIdle()

        assertEquals(10, messages.size)
        assertEquals(messages.size, inbox.getCount())

        // filtered style
        val filteredMessages = inbox.getMessages(testPredicate)
        assertEquals(inbox.getCount() / 2, filteredMessages.size)
    }

    @Test
    public fun testGetUnreadMessages(): TestResult = runTest {
        insertTestMessages()

        val messageIds = (1..4).map { "${it}_message_id" }.toSet()

        // Mark messages read
        inbox.markMessagesRead(messageIds)
        advanceUntilIdle()

        assertEquals(6, inbox.getUnreadCount())

        val unreadMessages = inbox.getUnreadMessages()
        assertEquals(6, unreadMessages.size)

        val filteredMessages = inbox.getUnreadMessages(testPredicate)
        assertEquals(3, filteredMessages.size)

        for (message: Message in filteredMessages) {
            val substring = message.id.replace("_message_id", "")
            val index = substring.toInt()

            assertEquals(0, index % 2)
        }
    }

    @Test
    public fun testGetReadMessages(): TestResult = runTest {
        insertTestMessages()

        val messageIds = (1..4).map { "${it}_message_id" }.toSet()

        // Mark messages read
        inbox.markMessagesRead(messageIds)
        advanceUntilIdle()

        val readMessages = inbox.getReadMessages()
        assertEquals(readMessages.size, 4)

        val filteredMessages = inbox.getReadMessages(testPredicate)
        assertEquals(filteredMessages.size, 2)

        for (message: Message in filteredMessages) {
            val substring = message.id.replace("_message_id", "")
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
            spyMessageDao.deleteAllMessages()
            spyActivityMonitor.removeApplicationListener(any())
            mockChannel.removeChannelListener(any())
            mockChannel.removeChannelRegistrationPayloadExtender(any())
            mockUser.removeListener(any())
        }
    }

    /** Verify updateEnabledState when enabled. */
    @Test
    public fun testUpdateEnabledStateEnabled(): TestResult = runTest {
        every { mockUser.shouldUpdate() } returns false

        inbox.setEnabled(true)
        inbox.updateEnabledState()
        advanceUntilIdle()
        mainLooper.runToEndOfTasks()

        // Update again to make sure that we don't restart the Inbox if already started.
        inbox.updateEnabledState()
        advanceUntilIdle()
        mainLooper.runToEndOfTasks()

        coVerify {
            mockUser.addListener(any())
            spyActivityMonitor.addApplicationListener(any())
            //spyMessageDao.getMessages()
            mockChannel.addChannelListener(any())
            mockUser.shouldUpdate()
            mockChannel.addChannelRegistrationPayloadExtender(any())
        }
    }

    private fun insertTestMessages() {
        val messageEntities = mutableListOf<MessageEntity>()

        // Populate the MCRAP database with 10 messages
        for (i in 0..9) {
            val message = createMessage("${i + 1}_message_id", null)
            val entity = requireNotNull(
                MessageEntity.createMessageFromPayload(message.id, message.rawMessageJson)
            )
            messageEntities.add(entity)
        }

        // Put 5 more expired messages in there (these should not show up after refresh)
        for (i in 10..14) {
            val message = createMessage("${i + 1}_message_id", null, Date(0))
            val entity = requireNotNull(
                MessageEntity.createMessageFromPayload(message.id, message.rawMessageJson)
            )

            messageEntities.add(entity)
        }

        val job = MainScope().launch {
            spyMessageDao.insertMessages(messageEntities)
            inbox.notifyInboxUpdated()
        }

        testDispatcher.scheduler.advanceUntilIdle()

        runBlocking {
            job.join()
        }

        clearInvocations(spyMessageDao)
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
        ): Map<String, Message> = messages.associateBy { it.id }
    }
}
