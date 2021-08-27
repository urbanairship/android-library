package com.urbanairship.chat

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestActivityMonitor
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestApplication
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.chat.api.ChatApiClient
import com.urbanairship.chat.api.ChatConnection
import com.urbanairship.chat.api.ChatResponse
import com.urbanairship.chat.data.ChatDatabase
import com.urbanairship.chat.data.MessageEntity
import com.urbanairship.config.AirshipUrlConfig
import com.urbanairship.util.DateUtils
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertNull
import junit.framework.Assert.assertTrue
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ConversationTest {

    private lateinit var mockChannel: AirshipChannel
    private lateinit var mockConnection: ChatConnection
    private lateinit var mockApiClient: ChatApiClient
    private lateinit var mockChat: Chat
    private lateinit var chatDatabase: ChatDatabase
    private lateinit var testActivityMonitor: TestActivityMonitor

    private lateinit var dataStore: PreferenceDataStore
    private lateinit var conversation: Conversation
    private lateinit var chatListener: ChatConnection.ChatListener

    private lateinit var testDispatcher: TestCoroutineDispatcher
    private lateinit var runtimeConfig: TestAirshipRuntimeConfig

    private lateinit var mockLifeCycleOwner: LifecycleOwner
    private lateinit var lifeCycleRegistry: LifecycleRegistry

    @Before
    fun setUp() {
        mockChannel = mock()
        mockConnection = mock()
        mockApiClient = mock()
        mockChat = mock()
        mockChannel = mock()
        mockLifeCycleOwner = mock()

        chatDatabase = Room.inMemoryDatabaseBuilder(TestApplication.getApplication(), ChatDatabase::class.java)
                .allowMainThreadQueries()
                .build()

        testDispatcher = TestCoroutineDispatcher()

        Dispatchers.setMain(testDispatcher)

        testActivityMonitor = TestActivityMonitor()

        dataStore = PreferenceDataStore.inMemoryStore(TestApplication.getApplication())

        val captor = ArgumentCaptor.forClass(ChatConnection.ChatListener::class.java)

        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig()
        runtimeConfig.urlConfig = AirshipUrlConfig.newBuilder()
                .setChatSocketUrl("wss://test.urbanairship.com")
                .setChatUrl("https://test.urbanairship.com")
                .build()

        conversation = Conversation(TestApplication.getApplication(), dataStore, runtimeConfig,
                mockChannel, chatDatabase, mockConnection, mockApiClient, testActivityMonitor,
                testDispatcher, testDispatcher)

        lifeCycleRegistry = LifecycleRegistry(mockLifeCycleOwner)

        verify(mockConnection).chatListener = captor.capture()
        chatListener = captor.value

        conversation.isEnabled = true
        Mockito.clearInvocations(mockConnection)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun testConnectionCreatesUvp() = testDispatcher.runBlockingTest {
        whenever(mockChannel.id).thenReturn("some-channel")
        whenever(mockApiClient.fetchUvp("some-channel")).thenReturn("some-uvp")

        conversation.connect()

        verify(mockApiClient).fetchUvp("some-channel")
    }

    @Test

    fun testConnectionRequestsConversation() = testDispatcher.runBlockingTest {
        whenever(mockChannel.id).thenReturn("some-channel")
        whenever(mockApiClient.fetchUvp("some-channel")).thenReturn("some-uvp")

        testActivityMonitor.foreground()

        verify(mockConnection).open("some-uvp")
        verify(mockConnection).fetchConversation()
    }

    @Test
    fun testForegroundConnects() = testDispatcher.runBlockingTest {
        whenever(mockChannel.id).thenReturn("some-channel")
        whenever(mockApiClient.fetchUvp("some-channel")).thenReturn("some-uvp")

        testActivityMonitor.foreground()
        lifeCycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        verify(mockConnection).open("some-uvp")
    }

    @Test
    fun testSendMessageAfterSync() = testDispatcher.runBlockingTest {
        connect()

        val response = ChatResponse.ConversationLoaded(conversation = ChatResponse.ConversationLoaded.ConversationPayload(null))
        chatListener.onChatResponse(response)

        conversation.routing = ChatRouting("agent!")
        conversation.sendMessage("hello")
        verify(mockConnection).sendMessage(Mockito.eq("hello"), Mockito.isNull(), Mockito.anyString(), eq(ChatDirection.OUTGOING), Mockito.eq(null), Mockito.eq(ChatRouting("agent!")))
    }

    @Test
    fun testSendMessageBeforeSync() = testDispatcher.runBlockingTest {
        connect()
        conversation.routing = ChatRouting("agent!")
        conversation.sendMessage("hello")

        verify(mockConnection, Mockito.never()).sendMessage(Mockito.eq("hello"), Mockito.isNull(), Mockito.anyString(), eq(ChatDirection.OUTGOING), Mockito.eq(null), Mockito.eq(ChatRouting("agent!")))

        val response = ChatResponse.ConversationLoaded(conversation = ChatResponse.ConversationLoaded.ConversationPayload(null))
        chatListener.onChatResponse(response)

        verify(mockConnection).sendMessage(Mockito.eq("hello"), Mockito.isNull(), Mockito.anyString(), eq(ChatDirection.OUTGOING), Mockito.eq(null), Mockito.eq(ChatRouting("agent!")))
    }

    @Test
    fun testSendIncomingAfterSync() = testDispatcher.runBlockingTest {
        connect()

        val response = ChatResponse.ConversationLoaded(conversation = ChatResponse.ConversationLoaded.ConversationPayload(null))
        chatListener.onChatResponse(response)

        conversation.routing = ChatRouting("agent!")

        val date = DateUtils.parseIso8601("2021-01-01T00:00:00Z")
        val messages = listOf(ChatIncomingMessage("msg1", null, "2021-01-01T00:00:00Z", "asdfasdf"))

        conversation.addIncoming(messages)
        verify(mockConnection).sendMessage(Mockito.eq("msg1"), Mockito.isNull(), Mockito.anyString(), eq(ChatDirection.INCOMING), Mockito.eq(date), Mockito.eq(ChatRouting("agent!")))
    }

    @Test
    fun testSendIncomingBeforeSync() = testDispatcher.runBlockingTest {
        connect()
        conversation.routing = ChatRouting("agent!")

        val date = DateUtils.parseIso8601("2021-01-01T00:00:00Z")
        val messages = listOf(ChatIncomingMessage("msg1", null, "2021-01-01T00:00:00Z", "asdfasdf"))
        conversation.addIncoming(messages)

        verify(mockConnection, Mockito.never()).sendMessage(Mockito.eq("msg1"), Mockito.isNull(), Mockito.anyString(), eq(ChatDirection.INCOMING), Mockito.eq(date), Mockito.eq(ChatRouting("agent!")))

        val response = ChatResponse.ConversationLoaded(conversation = ChatResponse.ConversationLoaded.ConversationPayload(null))
        chatListener.onChatResponse(response)

        verify(mockConnection).sendMessage(Mockito.eq("msg1"), Mockito.isNull(), Mockito.anyString(), eq(ChatDirection.INCOMING), Mockito.eq(date), Mockito.eq(ChatRouting("agent!")))
    }

    @Test
    fun testSendMessageBeforeConnect() = testDispatcher.runBlockingTest {
        conversation.routing = ChatRouting("agent!")
        conversation.sendMessage("hello")
        connect()

        verify(mockConnection, Mockito.never()).sendMessage(Mockito.eq("hello"), Mockito.isNull(), Mockito.anyString(), eq(ChatDirection.OUTGOING), Mockito.eq(null), Mockito.eq(ChatRouting("agent!")))

        val response = ChatResponse.ConversationLoaded(conversation = ChatResponse.ConversationLoaded.ConversationPayload(null))
        chatListener.onChatResponse(response)

        verify(mockConnection).sendMessage(Mockito.eq("hello"), Mockito.isNull(), Mockito.anyString(), eq(ChatDirection.OUTGOING), Mockito.eq(null), Mockito.eq(ChatRouting("agent!")))
    }

    @Test
    fun testBackgroundClosesConnectionIfPendingSent() = testDispatcher.runBlockingTest {
        connect()
        verify(mockConnection).open("some-uvp")

        lifeCycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        testActivityMonitor.background()
        verify(mockConnection, Mockito.never()).close()

        val response = ChatResponse.ConversationLoaded(conversation = ChatResponse.ConversationLoaded.ConversationPayload(null))
        chatListener.onChatResponse(response)

        verify(mockConnection).close()
    }

    @Test
    fun testBackgroundPendingMessages() = testDispatcher.runBlockingTest {
        connect()
        conversation.routing = ChatRouting("agent!")
        conversation.sendMessage("hello")
        val requestId = chatDatabase.chatDao().getPendingMessages()[0].messageId

        lifeCycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        testActivityMonitor.background()
        verify(mockConnection, Mockito.never()).close()

        val response = ChatResponse.ConversationLoaded(conversation = ChatResponse.ConversationLoaded.ConversationPayload(null))
        chatListener.onChatResponse(response)
        verify(mockConnection, Mockito.never()).close()

        verify(mockConnection).sendMessage("hello", null, requestId, ChatDirection.OUTGOING, null, ChatRouting("agent!"))
        verify(mockConnection, Mockito.never()).close()

        val message = ChatResponse.Message("some-id", DateUtils.createIso8601TimeStamp(0), 1, "hello", null, requestId)
        val messageResponsePayload = ChatResponse.MessageReceived.MessageReceivedPayload(true, message)
        val messageResponse = ChatResponse.MessageReceived(messageResponsePayload)
        chatListener.onChatResponse(messageResponse)

        verify(mockConnection).close()
    }

    @Test
    fun testFetchMessagesPending() = testDispatcher.runBlockingTest {
        connect()
        conversation.routing = ChatRouting("agent!")
        val date = DateUtils.parseIso8601("2021-01-01T00:00:00Z")
        val incoming = listOf(ChatIncomingMessage("msg1", "https://fakeu.rl", "2021-01-01T00:00:00Z", "asdfasdf"))

        conversation.addIncoming(incoming)
        conversation.sendMessage("hello")

        val result = conversation.getMessages().result
        val expected = listOf(ChatMessage(result?.get(0)?.messageId!!, "hello", result?.get(0)?.createdOn!!, ChatDirection.OUTGOING, null, true), ChatMessage("asdfasdf", "msg1", date, ChatDirection.INCOMING, "https://fakeu.rl", false))

        assertEquals(result, expected)
    }

    @Test
    fun testConnectionStatus() = testDispatcher.runBlockingTest {
        connect()
        assertFalse(conversation.isConnected)

        chatListener.onOpen()
        assertTrue(conversation.isConnected)

        chatListener.onClose(ChatConnection.CloseReason.Manual)
        assertFalse(conversation.isConnected)
    }

    @Test
    fun testConnectWhileDisabled() = testDispatcher.runBlockingTest {
        conversation.isEnabled = false
        connect()
        verify(mockConnection, never()).open(any())
    }

    @Test
    fun testConnectWhenNotConfigured() = testDispatcher.runBlockingTest {
        runtimeConfig.urlConfig = AirshipUrlConfig.newBuilder().build()
        connect()
        verify(mockConnection, never()).open(any())
    }

    @Test
    fun testDisableWhileConnected() = testDispatcher.runBlockingTest {
        connect()
        conversation.isEnabled = false
        verify(mockConnection).close()
    }

    @Test
    fun testWipeDataClosesConnection() = testDispatcher.runBlockingTest {
        connect()
        conversation.clearData()
        verify(mockConnection).close()
    }

    @Test
    fun testClearDataDeletesMessages() = testDispatcher.runBlockingTest {
        whenever(mockChannel.id).thenReturn("some-channel")
        whenever(mockApiClient.fetchUvp("some-channel")).thenReturn("some-uvp")

        conversation.sendMessage("some-message")
        assertTrue(chatDatabase.chatDao().hasPendingMessages())

        conversation.clearData()
        assertFalse(chatDatabase.chatDao().hasPendingMessages())
    }

    @Test
    fun testClearDataDeletesUvp() = testDispatcher.runBlockingTest {
        whenever(mockChannel.id).thenReturn("some-channel")
        whenever(mockApiClient.fetchUvp("some-channel")).thenReturn("some-uvp")
        conversation.connect()
        testActivityMonitor.foreground()

        assertEquals("some-uvp", dataStore.getString("com.urbanairship.chat.UVP", null))
        conversation.clearData()

        assertNull(dataStore.getString("com.urbanairship.chat.UVP", null))
    }

    fun testRefresh() = testDispatcher.runBlockingTest {
        whenever(mockChannel.id).thenReturn("some-channel")
        whenever(mockApiClient.fetchUvp("some-channel")).thenReturn("some-uvp")
        whenever(mockConnection.open("some-uvp")).then {
            whenever(mockConnection.isOpenOrOpening).thenReturn(true)
            mockChannel
        }

        whenever(mockConnection.fetchConversation()).then {
            val response = ChatResponse.ConversationLoaded(conversation = ChatResponse.ConversationLoaded.ConversationPayload(null))
            chatListener.onChatResponse(response)
            true
        }

        assertTrue(conversation.refreshMessages(300000))
    }

    fun testGetMessages() = testDispatcher.runBlockingTest {
        assertEquals(emptyList<ChatMessage>(), conversation.getMessages())

        val message = randomMessageEntity()
        chatDatabase.chatDao().upsert(message)

        assertEquals(listOf(message), conversation.getMessages())
    }

    fun testGetMessagesSortAndLimit() = testDispatcher.runBlockingTest {
        val allMessages = List(100) { randomMessageEntity() }
        allMessages.forEach { message -> chatDatabase.chatDao().upsert(message) }

        val expected = allMessages
                .sortedWith(compareBy(MessageEntity::isPending, MessageEntity::createdOn))
                .take(50)

        conversation.getMessages().addResultCallback { messages ->
            assertEquals(50, messages?.size)
            assertEquals(expected, messages)
        }
    }

    fun testConversationListener() = testDispatcher.runBlockingTest {
        val listener = TestListener()
        conversation.addConversationListener(listener)

        // Sanity check.
        assertEquals(0, listener.callCount)

        // Verify listener is notified when the conversation is loaded.
        chatListener.onChatResponse(ChatResponse.ConversationLoaded(
                ChatResponse.ConversationLoaded.ConversationPayload(null)))

        assertEquals(1, listener.callCount)

        val message = ChatResponse.Message("id", "0", 0, "text", requestId = "req-id")

        // Verify listener is notified when a message is sent successfully.
        chatListener.onChatResponse(ChatResponse.MessageReceived(
                ChatResponse.MessageReceived.MessageReceivedPayload(true, message)))

        assertEquals(2, listener.callCount)

        // Verify listener is notified when a new message is received.
        chatListener.onChatResponse(ChatResponse.NewMessage(
                ChatResponse.NewMessage.NewMessagePayload(message)))

        assertEquals(3, listener.callCount)

        // Verify listener is no longer called after removal.
        conversation.removeConversationListener(listener)

        chatListener.onChatResponse(ChatResponse.ConversationLoaded(
                ChatResponse.ConversationLoaded.ConversationPayload(null)))

        assertEquals(3, listener.callCount)
    }

    private fun connect() {
        whenever(mockChannel.id).thenReturn("some-channel")
        whenever(mockApiClient.fetchUvp("some-channel")).thenReturn("some-uvp")

        testActivityMonitor.foreground()
        lifeCycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        whenever(mockConnection.isOpenOrOpening).thenReturn(true)
    }

    private fun randomMessageEntity(): MessageEntity {
        val isAttachment = listOf(true, false).random()
        return MessageEntity(
                messageId = UUID.randomUUID().toString(),
                text = if (isAttachment) null else UUID.randomUUID().toString(),
                attachment = if (isAttachment) "https://example.com/some.gif" else null,
                createdOn = Random.nextLong(28800, 1924934400),
                direction = listOf(ChatDirection.INCOMING, ChatDirection.OUTGOING).random(),
                isPending = listOf(true, false).random()
        )
    }

    private class TestListener : ConversationListener {
        private val updateCallCount = AtomicInteger(0)

        val callCount: Int
            get() = updateCallCount.get()

        override fun onConversationUpdated() {
            updateCallCount.incrementAndGet()
        }
    }
}
