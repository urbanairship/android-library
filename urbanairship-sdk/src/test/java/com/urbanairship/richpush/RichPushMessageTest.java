/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

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

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import org.junit.Test;

import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

public class RichPushMessageTest extends BaseTestCase {
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
        RichPushMessage message = RichPushMessage.create(JsonValue.parseString(MCRAP_MESSAGE), true, false);

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

        RichPushMessage message = RichPushMessage.create(JsonValue.wrap(map), true, false);

        // Expiry
        assertEquals(10000l, message.getExpirationDateMS().longValue());
        assertTrue(message.isExpired());
    }
}
