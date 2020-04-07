/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import androidx.annotation.NonNull;
import android.view.View;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

public class MessageViewAdapterTest extends BaseTestCase {

    private MessageViewAdapter messageViewAdapter;

    @Before
    public void setup() {
        messageViewAdapter = new MessageViewAdapter(TestApplication.getApplication(), 0) {
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
