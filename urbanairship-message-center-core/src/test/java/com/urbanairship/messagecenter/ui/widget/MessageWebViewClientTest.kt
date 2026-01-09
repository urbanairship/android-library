/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter.ui.widget

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.Airship
import com.urbanairship.messagecenter.Inbox
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.MessageCenterTestUtils.createMessage
import com.urbanairship.messagecenter.User
import com.urbanairship.messagecenter.messageCenter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class MessageWebViewClientTest {

    private var context: android.content.Context = ApplicationProvider.getApplicationContext()
    private var mockInbox: Inbox = mockk(relaxed = true)
    private var mockUser: User = mockk(relaxed = true) {
        every { id } returns "test-user-id"
        every { password } returns "test-password"
    }
    private var messageWebView: MessageWebView = MessageWebView(context)
    private var client: MessageWebViewClient = MessageWebViewClient()

    @Before
    public fun setup() {
        mockkObject(Airship)
        every { Airship.messageCenter } returns mockk {
            every { inbox } returns mockInbox
            every { user } returns mockUser
        }
    }

    @After
    public fun teardown() {
        unmockkAll()
    }

    @Test
    public fun testGetMessageReturnsStoredMessageWhenUrlMatches() = runTest {
        val message = createMessage("message-id")

        // Load the message
        messageWebView.loadMessage(message)

        val result = client.getMessage(messageWebView)
        assertEquals(message.id, result?.id)
        assertEquals(message.bodyUrl, result?.bodyUrl)
    }

    @Test
    public fun testGetMessageFallsBackToRunBlockingWhenMessageIsNull() = runTest {
        val message = createMessage("message-id")

        // Create a mock MessageWebView without a stored message
        val emptyWebView = mockk<MessageWebView>(relaxed = true) {
            every { url } returns message.bodyUrl
        }

        // Mock the inbox to return the message
        coEvery { mockInbox.getMessageByUrl(message.bodyUrl) } returns message

        val result = client.getMessage(emptyWebView)
        assertEquals(message.id, result?.id)
        coVerify { mockInbox.getMessageByUrl(message.bodyUrl) }
    }

    @Test
    public fun testGetMessageFallsBackToRunBlockingWhenUrlDoesNotMatch() = runTest {
        val message = createMessage("message-id")
        val differentMessage = createMessage("different-id")

        // Create a mock MessageWebView with a stored message but different URL
        val webViewSpy = mockk<MessageWebView>(relaxed = true) {
            every { url } returns differentMessage.bodyUrl
            every { getCurrentMessage() } returns message
        }

        // Mock the inbox to return a different message
        coEvery { mockInbox.getMessageByUrl(differentMessage.bodyUrl) } returns differentMessage

        val result = client.getMessage(webViewSpy)
        // Should return the message from the URL lookup, not the stored one
        assertEquals(differentMessage.id, result?.id)
        coVerify { mockInbox.getMessageByUrl(differentMessage.bodyUrl) }
    }

    @Test
    public fun testGetMessageReturnsNullWhenUrlIsNull() = runTest {
        val message = createMessage("message-id")

        // Create a mock MessageWebView with a stored message but null URL
        val webViewSpy = mockk<MessageWebView>(relaxed = true) {
            every { url } returns null
            every { getCurrentMessage() } returns message
        }

        val result = client.getMessage(webViewSpy)
        assertNull(result)
    }

    @Test
    public fun testGetMessageReturnsNullWhenWebViewIsNotMessageWebView() = runTest {
        val regularWebView = mockk<WebView>(relaxed = true) {
            every { url } returns "https://example.com/message"
        }

        // Mock the inbox to return null
        coEvery { mockInbox.getMessageByUrl(any()) } returns null

        val result = client.getMessage(regularWebView)
        assertNull(result)
    }
}
