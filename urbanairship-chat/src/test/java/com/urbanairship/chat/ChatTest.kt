package com.urbanairship.chat

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestApplication
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
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
            assertEquals(JobInfo.JOB_FINISHED, result)
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
            assertEquals(JobInfo.JOB_RETRY, result)
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
        // uairship://chat?routing={"agent":"smith"}&chat_input=Hello Person!
        val deepLink = Uri.parse("uairship://chat?routing=%7B%22agent%22%3A%22smith%22%7D&chat_input=Hello%20Person%21")
        chat.openChatListener = onShowChatListener

        assertTrue(chat.onAirshipDeepLink(deepLink))
        verify(onShowChatListener).onOpenChat("Hello Person!")
        verify(mockConversation).routing = ChatRouting(agent = "smith")
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