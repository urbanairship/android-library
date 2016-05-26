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

import com.urbanairship.TestApplication;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import junit.framework.Assert;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class RichPushTestUtils {

    private static final String MCRAP_TEMPLATE = "{\"unread\": true, \"message_sent\": \"2010-09-05 12:13 -0000\"," +
            "\"title\": \"Message title\"," +
            "\"message_url\": \"https://go.urbanairship.com/api/user/some_user_id/messages/message_id\"," +
            "\"message_body_url\": \"https://go.urbanairship.com/api/user/some_user_id/messages/message_id/body/\"," +
            "\"message_read_url\": \"https://go.urbanairship.com/api/user/some_user_id/messages/message_id/read/\"," +
            "\"extra\": {\"some_key\": \"some_value\"},\"content_type\": \"text/html\",\"content_size\": \"128\"}";

    public static void insertMessage(String messageId) {
        insertMessage(messageId, null);
    }

    public static void insertMessage(String messageId, Map<String, String> extras) {
        insertMessage(messageId, extras, false);
    }

    public static void insertMessage(String messageId, Map<String, String> extras, boolean expired) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(RichPushMessage.MESSAGE_ID_KEY, messageId);
        payload.put(RichPushMessage.MESSAGE_BODY_URL_KEY, String.format("https://go.urbanairship.com/api/user/tests/messages/%s/body/", messageId));
        payload.put(RichPushMessage.MESSAGE_READ_URL_KEY, String.format("https://go.urbanairship.com/api/user/tests/messages/%s/read/", messageId));
        payload.put(RichPushMessage.MESSAGE_URL_KEY, String.format("https://go.urbanairship.com/api/user/tests/messages/%s", messageId));
        payload.put(RichPushMessage.TITLE_KEY, String.format("%s title", messageId));
        payload.put(RichPushMessage.UNREAD_KEY, true);
        payload.put(RichPushMessage.MESSAGE_SENT_KEY, DateUtils.createIso8601TimeStamp(System.currentTimeMillis()));

        if (extras != null) {
            payload.put(RichPushMessage.EXTRA_KEY, extras);
        }

        if (expired) {
            payload.put(RichPushMessage.MESSAGE_EXPIRY_KEY, DateUtils.createIso8601TimeStamp(0));
        }

        try {
            RichPushResolver resolver = new RichPushResolver(TestApplication.getApplication());
            resolver.insertMessages(Arrays.asList(JsonValue.wrap(payload)));
        } catch (JsonException e) {
            Assert.fail(e.getMessage());
        }
    }


}
