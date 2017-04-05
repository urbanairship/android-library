/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.richpush;

import com.urbanairship.BaseTestCase;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import java.util.HashSet;
import java.util.Set;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.fail;
import static org.junit.Assert.assertEquals;

public class RichPushResolverTest extends BaseTestCase {

    private RichPushResolver resolver;

    @Before
    public void setUp() {
        resolver = new RichPushResolver(RuntimeEnvironment.application);

        // Populate the MCRAP database with 10 messages
        for (int i = 0; i < 10; i++) {
            RichPushTestUtils.insertMessage(String.valueOf(i + 1) + "_message_id");
        }
    }

    /**
     * Test get messages returns all the messages.
     */
    @Test
    public void testGetMessages() {
        assertEquals(10, resolver.getMessages().size());
    }

    /**
     * Test marking messages as read.
     */
    @Test
    public void testMarkMessagesRead() {
        Set<String> keys = new HashSet<>();
        keys.add("1_message_id");
        keys.add("2_message_id");
        keys.add("6_message_id");

        int updated = resolver.markMessagesRead(keys);
        assertEquals(keys.size(), updated);

        for (RichPushMessage message : resolver.getMessages()) {
            if (!message.isRead()) {
                continue;
            }

            if (keys.contains(message.getMessageId())) {
                keys.remove(message.getMessageId());
            } else {
                fail("Unexpected message read: " + message);
            }
        }

        assertEquals(0, keys.size());
        assertEquals(10, resolver.getMessages().size());
    }

    /**
     * Test marking messages as read from the origin.
     */
    @Test
    public void testMarkMessagesReadOrigin() {
        Set<String> keys = new HashSet<>();
        keys.add("1_message_id");
        keys.add("2_message_id");
        keys.add("6_message_id");

        int updated = resolver.markMessagesReadOrigin(keys);
        assertEquals(keys.size(), updated);
        assertEquals(10, resolver.getMessages().size());
    }

    /**
     * Test marking messages as unread.
     */
    @Test
    public void testMarkMessagesUnread() {
        Set<String> keys = new HashSet<>();
        keys.add("1_message_id");
        keys.add("2_message_id");
        keys.add("6_message_id");

        int updated = resolver.markMessagesRead(keys);
        assertEquals(keys.size(), updated);

        updated = resolver.markMessagesUnread(keys);
        assertEquals(keys.size(), updated);

        for (RichPushMessage message : resolver.getMessages()) {
            assertFalse(message.isRead());
        }
    }

    /**
     * Test marking messages for deletion.
     */
    @Test
    public void testMarkMessagesDeleted() {
        Set<String> keys = new HashSet<>();
        keys.add("1_message_id");
        keys.add("2_message_id");
        keys.add("6_message_id");

        int updated = resolver.markMessagesDeleted(keys);
        assertEquals(keys.size(), updated);

        for (RichPushMessage message : resolver.getMessages()) {
            if (!message.isDeleted()) {
                continue;
            }

            if (keys.contains(message.getMessageId())) {
                keys.remove(message.getMessageId());
            } else {
                fail("Unexpected message marked for deletion: " + message);
            }
        }

        assertEquals(0, keys.size());
        assertEquals(10, resolver.getMessages().size());
    }

    /**
     * Test deleting messages.
     */
    @Test
    public void testDeleteMessages() {
        Set<String> keys = new HashSet<>();
        keys.add("1_message_id");
        keys.add("2_message_id");
        keys.add("6_message_id");

        int deleted = resolver.deleteMessages(keys);
        assertEquals(keys.size(), deleted);

        for (RichPushMessage message : resolver.getMessages()) {
            assertFalse(keys.contains(message.getMessageId()));
        }

        assertEquals(7, resolver.getMessages().size());
    }

    /**
     * Test getting the messages IDs that have been marked for deletion.
     */
    @Test
    public void testGetDeletedMessageIds() {
        Set<String> keys = new HashSet<>();
        keys.add("1_message_id");
        keys.add("2_message_id");
        keys.add("6_message_id");

        int updated = resolver.markMessagesDeleted(keys);
        assertEquals(keys.size(), updated);

        assertEquals(keys, resolver.getDeletedMessageIds());
        assertEquals(10, resolver.getMessages().size());
    }

    /**
     * Test getting the message IDs of messages that are unread on the client but not the
     * origin.
     */
    @Test
    public void testGetUnreadMessageIds() {
        Set<String> keys = new HashSet<>();
        keys.add("1_message_id");
        keys.add("2_message_id");
        keys.add("6_message_id");

        int updated = resolver.markMessagesRead(keys);
        assertEquals(keys.size(), updated);

        assertEquals(keys, resolver.getReadUpdatedMessageIds());
        assertEquals(10, resolver.getMessages().size());
    }

}
