package com.urbanairship.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestApplication
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

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
}
