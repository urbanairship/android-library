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

package com.urbanairship.util;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public abstract class Util {

    private static final SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yyyy-MM-dd HH:mm Z");
    private static final String richPushJsonString = "{\"unread\": true, \"message_sent\": \"2010-09-05 12:13 -0000\"," +
            "\"title\": \"Message title\"," +
            "\"message_url\": \"https://go.urbanairship.com/api/user/some_user_id/messages/message_id\"," +
            "\"message_body_url\": \"https://go.urbanairship.com/api/user/some_user_id/messages/message_id/body/\"," +
            "\"message_read_url\": \"https://go.urbanairship.com/api/user/some_user_id/messages/message_id/read/\"," +
            "\"extra\": {\"some_key\": \"some_value\"},\"content_type\": \"text/html\",\"content_size\": \"128\"}";

    static {
        dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public static String getTimestamp() {
        return dateFormatGmt.format(new Date());
    }

    public static JSONObject getRichPushMessageJson() {
        JSONObject richPushJson = null;
        try {
            richPushJson = new JSONObject(richPushJsonString);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return richPushJson;
    }

}
