/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class MessageTest {

    private static final String MCRAP_MESSAGE = "{\"content_size\":44,\"extra\":{}," +
            "\"message_url\":\"https:\\/\\/device-api.urbanairship.com\\/api\\/user\\/test\\/messages\\/message\\/MESSAGE_ID\\/\"," +
            "\"title\":\"MESSAGE_TITLE\",\"message_sent\":\"2015-09-23 16:46:26\",\"options\":{\"asset_hosted\":\"true\"}," +
            "\"message_id\":\"MESSAGE_ID\",\"message_body_url\":\"https:\\/\\/dl.urbanairship.com\\/binary\\/token\\/app\\/MESSAGE_ID\\/body\\/\"," +
            "\"message_read_url\":\"https:\\/\\/device-api.urbanairship.com\\/api\\/user\\/test\\/messages\\/message\\/MESSAGE_ID\\/read\\/\"," +
            "\"unread\":false,\"content_type\":\"text\\/html\", \"extra\": {\"some_key\": \"some_value\"}}";

    /**
     * Test message parses its data correctly.
     */
    @Test
    public void testMessage() throws JsonException {
        Message message = Message.create(JsonValue.parseString(MCRAP_MESSAGE), true, false);

        assertEquals("MESSAGE_ID", message.getMessageId());
        assertEquals("MESSAGE_TITLE", message.getTitle());
        assertEquals("https://dl.urbanairship.com/binary/token/app/MESSAGE_ID/body/", message.getMessageBodyUrl());
        assertEquals("https://device-api.urbanairship.com/api/user/test/messages/message/MESSAGE_ID/read/", message.getMessageReadUrl());
        assertEquals("https://device-api.urbanairship.com/api/user/test/messages/message/MESSAGE_ID/", message.getMessageUrl());
        assertEquals(1443026786000l, message.getSentDateMS());
        assertFalse(message.isRead());
        assertFalse(message.isDeleted());

        // Extras
        assertEquals(1, message.getExtras().size());
        assertEquals("some_value", message.getExtras().getString("some_key"));
        assertEquals("some_value", message.getExtrasMap().get("some_key"));

        // Expiry
        assertNull(message.getExpirationDate());
        assertNull(message.getExpirationDateMS());
        assertFalse(message.isExpired());

        // Raw message JSON
        assertEquals(JsonValue.parseString(MCRAP_MESSAGE), message.getRawMessageJson());
    }

    /**
     * Test message parses its data correctly.
     */
    @Test
    public void testMessageExpiry() throws JsonException {
        // Add expiry
        Map<String, JsonValue> map = JsonValue.parseString(MCRAP_MESSAGE).getMap().getMap();
        map.put("message_expiry", JsonValue.wrap(DateUtils.createIso8601TimeStamp(10000l)));

        Message message = Message.create(JsonValue.wrap(map), true, false);

        // Expiry
        assertEquals(10000l, message.getExpirationDateMS().longValue());
        assertTrue(message.isExpired());
    }

}
