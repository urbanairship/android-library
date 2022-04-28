package com.urbanairship.chat

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestApplication
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.push.PushListener
import com.urbanairship.push.PushManager
import com.urbanairship.push.PushMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ChatTest {

    private var mockConversation: Conversation = mock()
    private var mockPush: PushManager = mock()
    private var mockJobDispatcher: JobDispatcher = mock()
    private var onShowChatListener: Chat.OnShowChatListener = mock()

    private lateinit var chat: Chat
    private lateinit var dataStore: PreferenceDataStore
    private lateinit var privacyManager: PrivacyManager

    @Before
    fun setUp() {
        dataStore = PreferenceDataStore.inMemoryStore(TestApplication.getApplication())
        privacyManager = PrivacyManager(dataStore, PrivacyManager.FEATURE_ALL)
        chat = Chat(TestApplication.getApplication(), dataStore,
                privacyManager, mockPush, mockConversation, mockJobDispatcher)
    }

    @Test
    fun testEnableOnInit() {
        chat.init()
        verify(mockConversation).isEnabled = true
    }

    @Test
    fun testDisable() {
        chat.init()
        chat.isEnabled = false
        verify(mockConversation).isEnabled = false
    }

    @Test
    fun testComponentDisabled() {
        chat.init()
        chat.isComponentEnabled = false
        verify(mockConversation).isEnabled = false
    }

    @Test
    fun testDataCollection() {
        chat.init()
        clearInvocations(mockConversation)

        privacyManager.disable(PrivacyManager.FEATURE_CHAT)
        verify(mockConversation).isEnabled = false
        verify(mockConversation).clearData()

        privacyManager.enable(PrivacyManager.FEATURE_CHAT)
        verify(mockConversation).isEnabled = true
    }

    @Test
    fun testOpenChatListener() {
        val listener = mock<Chat.OnShowChatListener>()
        whenever(listener.onOpenChat(anyOrNull())).thenReturn(true)

        chat.openChatListener = listener

        chat.openChat()
        verify(listener).onOpenChat(null)

        chat.openChat("sup")
        verify(listener).onOpenChat("sup")
    }

    @Test
    fun testRefreshPushReceived() {
        val captor = argumentCaptor<PushListener>()
        chat.init()
        verify(mockPush).addPushListener(captor.capture())

        assertNotNull(captor.firstValue)

        val message = PushMessage(mapOf("com.urbanairship.refresh_chat" to "true"))
        captor.firstValue.onPushReceived(message, false)

        verify(mockJobDispatcher).dispatch(argThat {
            this.action == "REFRESH_MESSAGES_ACTION"
        })
    }

    @ExperimentalCoroutinesApi
    @Test
    fun testRefreshMessages() {
        runBlockingTest {
            whenever(mockConversation.refreshMessages()).thenReturn(true)
            val job = JobInfo.newBuilder().setAction("REFRESH_MESSAGES_ACTION").setAirshipComponent(Chat::class.java).build()

            val result = chat.onPerformJob(mock(), job)
            assertEquals(JobResult.SUCCESS, result)
            verify(mockConversation).refreshMessages()
        }
    }

    @ExperimentalCoroutinesApi
    @Test
    fun testRefreshMessagesFailed() {
        runBlockingTest {
            whenever(mockConversation.refreshMessages()).thenReturn(false)
            val job = JobInfo.newBuilder().setAction("REFRESH_MESSAGES_ACTION").setAirshipComponent(Chat::class.java).build()

            val result = chat.onPerformJob(mock(), job)
            assertEquals(JobResult.RETRY, result)
            verify(mockConversation).refreshMessages()
        }
    }

    @Test
    fun testDeepLink() {
        val deepLink = Uri.parse("uairship://chat")
        chat.openChatListener = onShowChatListener

        assertTrue(chat.onAirshipDeepLink(deepLink))
        verify(onShowChatListener).onOpenChat(null)
    }

    @Test
    fun testDeepLinkTrailingSlash() {
        val deepLink = Uri.parse("uairship://chat/")
        chat.openChatListener = onShowChatListener

        assertTrue(chat.onAirshipDeepLink(deepLink))
        verify(onShowChatListener).onOpenChat(null)
    }

    @Test
    fun testDeepLinkArgs() {
        // uairship://chat?routing={"agent":"smith"}&chat_input=Hello Person!&prepopulated_messages=[{"msg":"msg1","url":"https://fakeu.rl","date":"2021-01-01T00:00:00Z","id":"asdfasdf"},{"msg":"msg2","url":"https://fakeu.rl"},"date":"2021-01-02T00:00:00Z","id":"fdsafdsa"}]

        val deepLink = Uri.parse("uairship://chat?routing=%7B%22agent%22%3A%22smith%22%7D&chat_input=Hello%20Person%21&prepopulated_messages=%5B%7B%22msg%22%3A%22msg1%22%2C%22url%22%3A%22https%3A%2F%2Ffakeu.rl%22%2C%22date%22%3A%222021-01-01T00%3A00%3A00Z%22%2C%22id%22%3A%22asdfasdf%22%7D%2C%7B%22msg%22%3A%22msg2%22%2C%22url%22%3A%22https%3A%2F%2Ffakeu.rl%22%2C%22date%22%3A%222021-01-02T00%3A00%3A00Z%22%2C%22id%22%3A%22fdsafdsa%22%7D%5D%0A%0A")
        chat.openChatListener = onShowChatListener

        assertTrue(chat.onAirshipDeepLink(deepLink))
        verify(onShowChatListener).onOpenChat("Hello Person!")
        verify(mockConversation).routing = ChatRouting(agent = "smith")

        val messages = listOf(ChatIncomingMessage("msg1", "https://fakeu.rl", "2021-01-01T00:00:00Z", "asdfasdf"),
                ChatIncomingMessage("msg2", "https://fakeu.rl", "2021-01-02T00:00:00Z", "fdsafdsa"))
        verify(mockConversation).addIncoming(messages)
    }

    @Test
    fun testDeepLinkEmptyChatArgs() {
        // uairship://chat?routing={"agent":"smith"}&chat_input=

        val deepLink = Uri.parse("uairship://chat?routing=%7B%22agent%22%3A%22smith%22%7D&chat_input=")
        chat.openChatListener = onShowChatListener

        assertTrue(chat.onAirshipDeepLink(deepLink))
        verify(onShowChatListener).onOpenChat("")
        verify(mockConversation).routing = ChatRouting(agent = "smith")
    }

    @Test
    fun testDeepLinkEmptyMessageArgs() {
        // uairship://chat?routing={"agent":"smith"}&chat_input=Hello Person!&prepopulated_messages=

        val deepLink = Uri.parse("uairship://chat?routing=%7B%22agent%22%3A%22smith%22%7D&chat_input=Hello%20Person%21&prepopulated_messages=")
        chat.openChatListener = onShowChatListener

        assertTrue(chat.onAirshipDeepLink(deepLink))
        verify(onShowChatListener).onOpenChat("Hello Person!")
        verify(mockConversation).routing = ChatRouting(agent = "smith")
    }

    @Test
    fun testSimpleStringsDeepLinkArgs() {
        // uairship://chat?route_agent=smith&prepopulated_message=msg1

        val deepLink = Uri.parse("uairship://chat?route_agent=smith&prepopulated_message=msg1")
        chat.openChatListener = onShowChatListener

        assertTrue(chat.onAirshipDeepLink(deepLink))
        verify(mockConversation).routing = ChatRouting(agent = "smith")

        val messages = listOf(ChatIncomingMessage("msg1", null, null, null))
        verify(mockConversation).addIncoming(messages)
    }

    @Test
    fun testInvalidDeepLinks() {
        chat.openChatListener = onShowChatListener

        val wrongHost = Uri.parse("uairship://what")
        assertFalse(chat.onAirshipDeepLink(wrongHost))

        val tooManyArgs = Uri.parse("uairship://chat/what")
        assertFalse(chat.onAirshipDeepLink(tooManyArgs))

        verify(onShowChatListener, never()).onOpenChat(any())
    }
}
