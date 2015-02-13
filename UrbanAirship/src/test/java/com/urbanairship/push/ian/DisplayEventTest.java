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

package com.urbanairship.push.ian;

import com.urbanairship.RobolectricGradleTestRunner;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.EventTestUtils;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.Assert.assertEquals;

@RunWith(RobolectricGradleTestRunner.class)
public class DisplayEventTest {

    /**
     * Test display event.
     */
    @Test
    public void testDisplayEvent() throws JSONException {
        UAirship.shared().getAnalytics().setConversionSendId("send id");
        InAppNotification notification = new InAppNotification.Builder().setId("notification id").create();

        DisplayEvent event = new DisplayEvent(notification);

        EventTestUtils.validateEventValue(event, "id", "notification id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", "send id");
        assertEquals("ian_display", event.getType());
    }

    /**
     * Test display event when the conversion send id is null.
     */
    @Test
    public void testDisplayEventNoConversionSendId() throws JSONException {
        UAirship.shared().getAnalytics().setConversionSendId(null);
        InAppNotification notification = new InAppNotification.Builder().setId("notification id").create();

        DisplayEvent event = new DisplayEvent(notification);

        EventTestUtils.validateEventValue(event, "id", "notification id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", null);
        assertEquals("ian_display", event.getType());
    }
}
