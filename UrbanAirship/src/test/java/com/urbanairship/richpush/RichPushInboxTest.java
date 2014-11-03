/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.richpush;

import android.database.Cursor;

import com.urbanairship.RichPushTable;
import com.urbanairship.util.Util;

import org.json.JSONException;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RichPushInboxTest extends RichPushBaseTestCase {

    RichPushInbox inbox;

    @Override
    public void setUp() {
        super.setUp();

        this.insertRichPushRows(10, Util.getRichPushMessageJson());
        this.inbox = new RichPushInbox(this.richPushResolver);
    }

    /**
     * Tests the inbox reports the correct
     * number of messages that it was set up
     * with
     */
    @Test
    public void testNewRichPushInbox() {
        assertEquals(10, this.inbox.getCount());
        assertEquals(10, this.inbox.getUnreadCount());
        assertEquals(0, this.inbox.getReadCount());
    }

    /**
     * Test mark messages are marked deleted in the database
     * and the inbox.
     */
    @Test
    public void testMarkMessagesDeleted() throws JSONException, InterruptedException {
        HashSet<String> deletedIds = new HashSet<String>();
        deletedIds.add("1_message_id");
        deletedIds.add("3_message_id");
        deletedIds.add("6_message_id");

        this.inbox.deleteMessages(deletedIds);
        inbox.executor.awaitTermination(100, TimeUnit.MILLISECONDS);

        Cursor cursor = this.richPushResolver.getDeletedMessages();
        assertEquals(3, cursor.getCount());
        while (cursor.moveToNext()) {
            assertTrue(deletedIds.contains(cursor.getString(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_MESSAGE_ID))));
        }

        // Give the update thread a chance to update the inbox
        Thread.sleep(100);

        // Should have 3 less messages
        assertEquals(7, this.inbox.getCount());
        assertEquals(7, this.inbox.getUnreadCount());
        assertEquals(0, this.inbox.getReadCount());
    }

    /**
     * Test mark messages are marked read in the database
     * and the inbox.
     */
    @Test
    public void testMarkMessagesRead() throws InterruptedException {
        HashSet<String> markedReadIds = new HashSet<String>();
        markedReadIds.add("1_message_id");
        markedReadIds.add("3_message_id");
        markedReadIds.add("6_message_id");

        this.inbox.markMessagesRead(markedReadIds);
        inbox.executor.awaitTermination(100, TimeUnit.MILLISECONDS);

        Cursor cursor = richPushResolver.getReadMessages();
        assertEquals(3, cursor.getCount());
        while (cursor.moveToNext()) {
            assertTrue(markedReadIds.contains(cursor.getString(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_MESSAGE_ID))));
        }

        // Give the update thread a chance to update the inbox
        Thread.sleep(100);

        // Should have 3 read messages
        assertEquals(10, this.inbox.getCount());
        assertEquals(7, this.inbox.getUnreadCount());
        assertEquals(3, this.inbox.getReadCount());

        Map<String, RichPushMessage> readMessages = createIdToMessageMap(inbox.getReadMessages());
        Map<String, RichPushMessage> unreadMessages = createIdToMessageMap(inbox.getUnreadMessages());

        // Verify the read message are in the right lists
        for (String readId : markedReadIds) {
            assertTrue(readMessages.containsKey(readId));
            assertFalse(unreadMessages.containsKey(readId));
        }
    }

    /**
     * Test mark messages are marked unread in the database
     * and the inbox.
     */
    @Test
    public void testMarkMessagesUnread() throws InterruptedException {
        HashSet<String> messageIds = new HashSet<String>();
        messageIds.add("1_message_id");
        messageIds.add("3_message_id");
        messageIds.add("6_message_id");

        // Mark messages read
        inbox.markMessagesRead(messageIds);
        inbox.executor.awaitTermination(100, TimeUnit.MILLISECONDS);
        assertEquals(3, richPushResolver.getReadMessages().getCount());
        assertEquals(7, richPushResolver.getUnreadMessages().getCount());

        // Mark messages as unread
        inbox.markMessagesUnread(messageIds);
        inbox.executor.awaitTermination(100, TimeUnit.MILLISECONDS);

        assertEquals(0, richPushResolver.getReadMessages().getCount());
        assertEquals(10, richPushResolver.getUnreadMessages().getCount());

        // Give the update thread a chance to update the inbox
        Thread.sleep(100);

        assertEquals(10, this.inbox.getCount());
        assertEquals(10, this.inbox.getUnreadCount());
        assertEquals(0, this.inbox.getReadCount());
    }

    /**
     * Helper method to convert a list of rich push messages
     * to a map of message ids to messages
     *
     * @param messages List of messages to convert
     * @return A map of rich push messages
     */
    private Map<String, RichPushMessage> createIdToMessageMap(List<RichPushMessage> messages) {
        Map<String, RichPushMessage> messageMap = new HashMap<String, RichPushMessage>();

        for (RichPushMessage message : messages) {
            messageMap.put(message.getMessageId(), message);
        }

        return messageMap;
    }
}