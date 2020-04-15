/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class MessageViewAdapterTest {

    private MessageViewAdapter messageViewAdapter;

    @Before
    public void setup() {
        messageViewAdapter = new MessageViewAdapter(ApplicationProvider.getApplicationContext(), 0) {
            @Override
            protected void bindView(@NonNull View view, @NonNull Message message, int position) {

            }
        };
    }

    @Test
    public void testGetItem() {
        // Test empty message view adapter
        assertNull(messageViewAdapter.getItem(0));
        assertNull(messageViewAdapter.getItem(-1));

        // Set
        messageViewAdapter.set(Arrays.asList(MessageCenterTestUtils.createMessage("id-0", null, false), MessageCenterTestUtils.createMessage("id-1", null, false)));

        // Verify messages are available
        assertEquals("id-0", ((Message) messageViewAdapter.getItem(0)).getMessageId());
        assertEquals("id-1", ((Message) messageViewAdapter.getItem(1)).getMessageId());

        // Test non-empty message view adapter
        assertNull(messageViewAdapter.getItem(2));
        assertNull(messageViewAdapter.getItem(-1));
    }

    @Test
    public void testGetItemId() {
        // Test empty message view adapter
        assertEquals(-1, messageViewAdapter.getItemId(0));
        assertEquals(-1, messageViewAdapter.getItemId(-1));

        // Set
        messageViewAdapter.set(Arrays.asList(MessageCenterTestUtils.createMessage("id-0", null, false), MessageCenterTestUtils.createMessage("id-1", null, false)));

        // Verify messages are available
        assertEquals("id-0".hashCode(), messageViewAdapter.getItemId(0));
        assertEquals("id-1".hashCode(), messageViewAdapter.getItemId(1));

        // Test non-empty message view adapter
        assertEquals(-1, messageViewAdapter.getItemId(2));
        assertEquals(-1, messageViewAdapter.getItemId(-1));
    }

}
