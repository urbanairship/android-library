/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.messagecenter.MessageCenterTestUtils.createMessage
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class MessageViewAdapterTest {
    
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val messageViewAdapter = object : MessageViewAdapter(context, 0) {
        override fun bindView(view: View, message: Message, position: Int) {}
    }

    @Test
    public fun testGetItem() {
        // Test empty message view adapter
        assertNull(messageViewAdapter.getItem(0))
        assertNull(messageViewAdapter.getItem(-1))

        // Set
        messageViewAdapter.set(messages)

        // Verify messages are available
        assertEquals(messageId1, (messageViewAdapter.getItem(0) as Message?)?.messageId)
        assertEquals(messageId2, (messageViewAdapter.getItem(1) as Message?)?.messageId)

        // Test non-empty message view adapter
        assertNull(messageViewAdapter.getItem(2))
        assertNull(messageViewAdapter.getItem(-1))
    }

    @Test
    public fun testGetItemId() {
        // Test empty message view adapter
        assertEquals(-1, messageViewAdapter.getItemId(0))
        assertEquals(-1, messageViewAdapter.getItemId(-1))

        // Set
        messageViewAdapter.set(messages)

        // Verify messages are available
        assertEquals(messageId1.hashCode().toLong(), messageViewAdapter.getItemId(0))
        assertEquals(messageId2.hashCode().toLong(), messageViewAdapter.getItemId(1))

        // Test non-empty message view adapter
        assertEquals(-1, messageViewAdapter.getItemId(2))
        assertEquals(-1, messageViewAdapter.getItemId(-1))
    }

    private companion object {
        const val messageId1 = "id-0"
        const val messageId2 = "id-1"

        val messages = listOf(
            createMessage(messageId1, null, false),
            createMessage(messageId2, null, false)
        )
    }
}
