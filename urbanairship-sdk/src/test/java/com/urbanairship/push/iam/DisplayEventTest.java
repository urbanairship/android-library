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

package com.urbanairship.push.iam;

import com.urbanairship.BaseTestCase;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.EventTestUtils;

import org.json.JSONException;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class DisplayEventTest extends BaseTestCase {

    /**
     * Test display event.
     */
    @Test
    public void testDisplayEvent() throws JSONException {
        UAirship.shared().getAnalytics().setConversionSendId("send id");
        InAppMessage message = new InAppMessage.Builder().setId("message id").create();

        DisplayEvent event = new DisplayEvent(message);

        EventTestUtils.validateEventValue(event, "id", "message id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", "send id");
        assertEquals("in_app_display", event.getType());
        assertTrue(event.isValid());
    }

    /**
     * Test display event when the conversion send id is null.
     */
    @Test
    public void testDisplayEventNoConversionSendId() throws JSONException {
        UAirship.shared().getAnalytics().setConversionSendId(null);
        InAppMessage message = new InAppMessage.Builder().setId("message id").create();

        DisplayEvent event = new DisplayEvent(message);

        EventTestUtils.validateEventValue(event, "id", "message id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", null);
        assertEquals("in_app_display", event.getType());
        assertTrue(event.isValid());

    }

    /**
     * Test display event is not valid if the id is empty.
     */
    @Test
    public void testInvalidEvent() throws JSONException {
        InAppMessage message = new InAppMessage.Builder().create();

        DisplayEvent event = new DisplayEvent(message);
        assertFalse(event.isValid());
    }
}
