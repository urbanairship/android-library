package com.urbanairship.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestApplication
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AirshipChatTest {

    private lateinit var mockConversation: Conversation
    private lateinit var airshipChat: AirshipChat

    @Before
    fun setUp() {
        mockConversation = mock()
        airshipChat = AirshipChat(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore, mockConversation)
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
    fun testOpenChatListener() {
        val listener = mock<AirshipChat.OnShowChatListener>()
        whenever(listener.onOpenChat(anyOrNull())).thenReturn(true)

        airshipChat.openChatListener = listener

        airshipChat.openChat()
        verify(listener).onOpenChat(null)

        airshipChat.openChat("sup")
        verify(listener).onOpenChat("sup")
    }
}
