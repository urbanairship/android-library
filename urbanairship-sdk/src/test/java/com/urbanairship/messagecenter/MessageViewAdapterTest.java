/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.messagecenter;


import android.view.View;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.richpush.RichPushMessage;
import com.urbanairship.richpush.RichPushTestUtils;

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
            protected void bindView(View view, RichPushMessage message, int position) {

            }
        };
    }


    @Test
    public void testGetItem() {
        // Test empty message view adapter
        assertNull(messageViewAdapter.getItem(0));
        assertNull(messageViewAdapter.getItem(-1));

        // Set
        messageViewAdapter.set(Arrays.asList(RichPushTestUtils.createMessage("id-0", null, false), RichPushTestUtils.createMessage("id-1", null, false)));

        // Verify messages are available
        assertEquals("id-0", ((RichPushMessage)messageViewAdapter.getItem(0)).getMessageId());
        assertEquals("id-1", ((RichPushMessage)messageViewAdapter.getItem(1)).getMessageId());

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
        messageViewAdapter.set(Arrays.asList(RichPushTestUtils.createMessage("id-0", null, false), RichPushTestUtils.createMessage("id-1", null, false)));

        // Verify messages are available
        assertEquals("id-0".hashCode(), messageViewAdapter.getItemId(0));
        assertEquals("id-1".hashCode(), messageViewAdapter.getItemId(1));

        // Test non-empty message view adapter
        assertEquals(-1, messageViewAdapter.getItemId(2));
        assertEquals(-1, messageViewAdapter.getItemId(-1));
    }
}