package com.urbanairship.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestApplication
import com.urbanairship.UAirship
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.push.PushListener
import com.urbanairship.push.PushManager
import com.urbanairship.push.PushMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AirshipChatTest {

    private lateinit var mockConversation: Conversation
    private lateinit var mockPush: PushManager
    private lateinit var mockJobDispatcher: JobDispatcher
    private lateinit var airshipChat: AirshipChat
    private lateinit var dataStore: PreferenceDataStore

    @Before
    fun setUp() {
        mockConversation = mock()

        dataStore = PreferenceDataStore(TestApplication.getApplication())
        mockPush = mock()
        mockJobDispatcher = mock()
        airshipChat = AirshipChat(TestApplication.getApplication(), dataStore,
                mockPush, mockConversation, mockJobDispatcher)
    }

    @Test
    fun testEnableOnInit() {
        airshipChat.init()
        verify(mockConversation).isEnabled = true
    }

    @Test
    fun testDisable() {
        airshipChat.init()
        airshipChat.isEnabled = false
        verify(mockConversation).isEnabled = false
    }

    @Test
    fun testComponentDisabled() {
        airshipChat.init()
        airshipChat.isComponentEnabled = false
        verify(mockConversation).isEnabled = false
    }

    @Test
    fun testDataCollection() {
        airshipChat.init()
        clearInvocations(mockConversation)

        dataStore.put(UAirship.DATA_COLLECTION_ENABLED_KEY, false)
        verify(mockConversation).isEnabled = false
        verify(mockConversation).clearData()

        dataStore.put(UAirship.DATA_COLLECTION_ENABLED_KEY, true)
        verify(mockConversation).isEnabled = true
    }

    @Test
    fun testOpenChatListener() {
        val listener = mock<AirshipChat.OnShowChatListener>()
        whenever(listener.onOpenChat(anyOrNull())).thenReturn(true)

        airshipChat.openChatListener = listener

        airshipChat.openChat()
        verify(listener).onOpenChat(null)

        airshipChat.openChat("sup")
        verify(listener).onOpenChat("sup")
    }

    @Test
    fun testRefreshPushReceived() {
        val captor = argumentCaptor<PushListener>()
        airshipChat.init()
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
            val job = JobInfo.newBuilder().setAction("REFRESH_MESSAGES_ACTION").setAirshipComponent(AirshipChat::class.java).build()

            val result = airshipChat.onPerformJob(mock(), job)
            assertEquals(JobInfo.JOB_FINISHED, result)
            verify(mockConversation).refreshMessages()
        }
    }

    @ExperimentalCoroutinesApi
    @Test
    fun testRefreshMessagesFailed() {
        runBlockingTest {
            whenever(mockConversation.refreshMessages()).thenReturn(false)
            val job = JobInfo.newBuilder().setAction("REFRESH_MESSAGES_ACTION").setAirshipComponent(AirshipChat::class.java).build()

            val result = airshipChat.onPerformJob(mock(), job)
            assertEquals(JobInfo.JOB_RETRY, result)
            verify(mockConversation).refreshMessages()
        }
    }
}
