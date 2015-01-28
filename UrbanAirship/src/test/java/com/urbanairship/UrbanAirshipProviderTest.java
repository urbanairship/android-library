/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

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

package com.urbanairship;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import com.urbanairship.richpush.RichPushBaseTestCase;
import com.urbanairship.util.Util;

import org.junit.Test;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UrbanAirshipProviderTest extends RichPushBaseTestCase {

    @Test
    @Config(shadows = { CustomShadowContentResolver.class })
    public void testGetType() {
        Uri messagesUri = UrbanAirshipProvider.getRichPushContentUri();
        assertEquals(UrbanAirshipProvider.RICH_PUSH_CONTENT_TYPE, this.resolver.getType(messagesUri));

        Uri messageUri = Uri.withAppendedPath(UrbanAirshipProvider.getRichPushContentUri(), "this.should.work");
        assertEquals(UrbanAirshipProvider.RICH_PUSH_CONTENT_ITEM_TYPE, this.resolver.getType(messageUri));

        Uri failureUri = Uri.parse("content://com.urbanairship/garbage");
        assertNull(this.resolver.getType(failureUri));
    }

    @Test
    public void testInsertRow() {
        ContentValues values = this.createRichPushContentValues("1", Util.getRichPushMessageJson());

        Uri newUri = this.resolver.insert(UrbanAirshipProvider.getRichPushContentUri(), values);
        assertFalse(UrbanAirshipProvider.getRichPushContentUri().equals(newUri));

        Cursor cursor = this.resolver.query(newUri, null, null, null, null);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("1_message_id", cursor.getString(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_MESSAGE_ID)));
        assertTrue("{\"some_key\":\"some_value\"}".equals(
                cursor.getString(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_EXTRA))));
        assertTrue(1 == cursor.getInt(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_UNREAD)));
        assertEquals("Message title", cursor.getString(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_TITLE)));
        cursor.close();
    }

    @Test
    public void testReplaceRow() {
        ContentValues values = this.createRichPushContentValues("2", Util.getRichPushMessageJson());

        Uri newUri = this.resolver.insert(UrbanAirshipProvider.getRichPushContentUri(), values);

        values.put(RichPushTable.COLUMN_NAME_EXTRA, "thisisadifferentvalue");

        Uri replaceUri = this.resolver.insert(UrbanAirshipProvider.getRichPushContentUri(), values);
        Logger.error("newUri: " + newUri.toString());
        Logger.error("replaceUri: " + replaceUri.toString());
        assertTrue(newUri.equals(replaceUri));

        Cursor cursor = this.resolver.query(replaceUri, null, null, null, null);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("2_message_id", cursor.getString(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_MESSAGE_ID)));
        assertEquals("thisisadifferentvalue", cursor.getString(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_EXTRA)));
        assertTrue(1 == cursor.getInt(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_UNREAD)));
        cursor.close();
    }

    @Test
    public void testInsertData() {
        this.insertRichPushRows(10, Util.getRichPushMessageJson());
        Cursor cursor = this.resolver.query(UrbanAirshipProvider.getRichPushContentUri(), null, null, null, null);
        assertEquals(10, cursor.getCount());
        cursor.close();
    }

    @Test
    public void testUpdateAllData() {
        this.insertRichPushRows(10, Util.getRichPushMessageJson());

        Cursor cursor = this.resolver.query(UrbanAirshipProvider.getRichPushContentUri(), null,
                RichPushTable.COLUMN_NAME_UNREAD + " = ?", new String[] { "1" }, null);
        assertEquals(10, cursor.getCount());
        cursor.close();

        ContentValues values = new ContentValues();
        values.put(RichPushTable.COLUMN_NAME_UNREAD, false);
        int updated = this.resolver.update(UrbanAirshipProvider.getRichPushContentUri(), values, null, null);
        assertEquals(10, updated);
    }

    @Test
    public void testUpdateSomeData() {
        this.insertRichPushRows(10, Util.getRichPushMessageJson());

        ContentValues values = new ContentValues();
        values.put(RichPushTable.COLUMN_NAME_UNREAD, false);
        int updated = this.resolver.update(UrbanAirshipProvider.getRichPushContentUri(), values,
                RichPushTable.COLUMN_NAME_MESSAGE_ID + " IN (?, ?, ?)",
                new String[] { "1_message_id", "3_message_id", "6_message_id" });
        assertEquals(3, updated);

        Cursor cursor = this.resolver.query(UrbanAirshipProvider.getRichPushContentUri(), null,
                RichPushTable.COLUMN_NAME_UNREAD + " = ?", new String[] { "1" }, null);
        assertEquals(7, cursor.getCount());
        cursor.close();
    }

    @Test
    public void testDeleteAllData() {
        this.insertRichPushRows(10, Util.getRichPushMessageJson());

        int deleted = this.resolver.delete(UrbanAirshipProvider.getRichPushContentUri(), null, null);
        assertEquals(10, deleted);
    }

    @Test
    public void testDeleteSomeData() {
        this.insertRichPushRows(10, Util.getRichPushMessageJson());

        int deleted = this.resolver.delete(UrbanAirshipProvider.getRichPushContentUri(),
                RichPushTable.COLUMN_NAME_MESSAGE_ID + " IN (?, ?, ?)",
                new String[] { "1_message_id", "3_message_id", "6_message_id" });
        assertEquals(3, deleted);
    }

}