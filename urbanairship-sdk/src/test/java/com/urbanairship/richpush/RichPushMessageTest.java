package com.urbanairship.richpush;

import android.database.Cursor;

import com.urbanairship.util.Util;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RichPushMessageTest extends RichPushBaseTestCase {

    @Test
    public void testCursorToMessage() throws JSONException {
        insertRichPushRows(1, Util.getRichPushMessageJson());

        Cursor cursor = richPushResolver.getAllMessages();
        cursor.moveToNext();

        RichPushMessage message = RichPushMessage.messageFromCursor(cursor);
        cursor.close();

        assertEquals("1_message_id", message.getMessageId());
        assertFalse(message.isRead());
        assertEquals("Message title", message.getTitle());
        assertNotNull(message.getExtras());
        assertEquals("some_value", message.getExtras().getString("some_key"));
    }

    @Test
    public void testCursorToMessageEmptyExtra() throws JSONException {
        JSONObject richPushMessageJson = Util.getRichPushMessageJson();
        richPushMessageJson.put("extra", new JSONObject());

        insertRichPushRows(1, richPushMessageJson);

        Cursor cursor = richPushResolver.getAllMessages();
        cursor.moveToNext();

        RichPushMessage message = RichPushMessage.messageFromCursor(cursor);
        cursor.close();

        assertNotNull(message.getExtras());
        assertEquals(0, message.getExtras().size());

        assertTrue(message.getExtras().isEmpty());
    }

    /**
     * Test the isExpired works properly.
     */
    @Test
    public void testIsExpired() {
        RichPushMessage message = new RichPushMessage("message-id");

        message.expirationMS = null;
        assertFalse("Null expirationMS should never expire a message.", message.isExpired());

        message.expirationMS = System.currentTimeMillis() - 10;
        assertTrue("Expiration before the current time should be expired.", message.isExpired());

        message.expirationMS = System.currentTimeMillis() + 100;
        assertFalse("Expiration after the current time should not be expired.", message.isExpired());

    }
}
