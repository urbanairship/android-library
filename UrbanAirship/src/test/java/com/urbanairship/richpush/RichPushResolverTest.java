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

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RichPushResolverTest extends RichPushBaseTestCase {

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void testGetMessage() {
        this.insertRichPushRows(10, Util.getRichPushMessageJson());

        Cursor cursor = this.richPushResolver.getMessage("4_message_id");
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("4_message_id", cursor.getString(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_MESSAGE_ID)));
        assertTrue("{\"some_key\":\"some_value\"}".equals(
                cursor.getString(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_EXTRA))));
        assertTrue(1 == cursor.getInt(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_UNREAD)));
        assertEquals("Message title", cursor.getString(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_TITLE)));
        cursor.close();
    }

    @Test
    public void testGetAllMessages() {
        this.insertRichPushRows(10, Util.getRichPushMessageJson());

        Cursor cursor = this.richPushResolver.getAllMessages();
        assertEquals(10, cursor.getCount());
        cursor.close();
    }

    @Test
    public void testMarkMessageRead() {
        this.insertRichPushRows(1, Util.getRichPushMessageJson());

        Cursor cursor = this.richPushResolver.getMessage("1_message_id");
        cursor.moveToFirst();
        assertTrue(1 == cursor.getInt(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_UNREAD)));
        cursor.close();

        int marked = this.richPushResolver.markMessageRead("1_message_id");
        assertEquals(1, marked);

        cursor = this.richPushResolver.getMessage("1_message_id");
        cursor.moveToFirst();
        assertTrue(0 == cursor.getInt(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_UNREAD)));
        cursor.close();
    }

    @Test
    public void testMarkMessagesRead() {
        this.insertRichPushRows(10, Util.getRichPushMessageJson());

        Set<String> keys = new HashSet<>();
        keys.add("1_message_id");
        keys.add("2_message_id");
        keys.add("6_message_id");

        int updated = this.richPushResolver.markMessagesRead(keys);
        assertEquals(keys.size(), updated);

        Cursor cursor = this.richPushResolver.getUnreadMessages();
        assertEquals(7, cursor.getCount());
        cursor.close();
    }

    @Test
    public void testReadAndUnreadMessages() {
        this.insertRichPushRows(10, Util.getRichPushMessageJson());

        this.richPushResolver.markMessageRead("2_message_id");
        this.richPushResolver.markMessageRead("3_message_id");
        this.richPushResolver.markMessageRead("3_message_id");

        Cursor cursor = this.richPushResolver.getReadMessages();
        assertEquals(2, cursor.getCount());

        cursor = this.richPushResolver.getUnreadMessages();
        assertEquals(8, cursor.getCount());

        cursor.close();
    }

    @Test
    public void testDeleteMessage() {
        this.insertRichPushRows(10, Util.getRichPushMessageJson());

        int deleted = this.richPushResolver.deleteMessage("1_message_id");
        deleted += this.richPushResolver.deleteMessage("8_message_id");
        assertEquals(2, deleted);

        Cursor cursor = this.richPushResolver.getAllMessages();
        assertEquals(8, cursor.getCount());
        cursor.close();
    }

    @Test
    public void testDeleteMessages() {
        this.insertRichPushRows(10, Util.getRichPushMessageJson());

        Set<String> keys = new HashSet<>();
        keys.add("1_message_id");
        keys.add("2_message_id");
        keys.add("6_message_id");

        int deleted = this.richPushResolver.deleteMessages(keys);
        assertEquals(keys.size(), deleted);

        Cursor cursor = this.richPushResolver.getAllMessages();
        assertEquals(7, cursor.getCount());
        cursor.close();
    }

}
