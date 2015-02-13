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

import android.content.Context;

import com.urbanairship.RobolectricGradleTestRunner;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.EventTestUtils;
import com.urbanairship.push.notifications.NotificationActionButton;
import com.urbanairship.util.DateUtils;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)
public class ResolutionEventTest {

    private InAppNotification notification;

    @Before
    public void before() {
        notification = new InAppNotification.Builder()
                .setId("ian id")
                .setButtonGroupId("button group")
                .create();

        UAirship.shared().getAnalytics().setConversionSendId("send id");
    }

    /**
     * Test button click resolution event.
     */
    @Test
    public void testButtonClickResolutionEvent() throws JSONException {
        Context context = mock(Context.class);

        NotificationActionButton actionButton = new NotificationActionButton.Builder("button id")
                .setDescription("button description")
                .build();

        ResolutionEvent event = ResolutionEvent.createButtonClickedResolutionEvent(context, notification, actionButton, 3500);

        EventTestUtils.validateEventValue(event, "id", "ian id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", "send id");
        EventTestUtils.validateNestedEventValue(event, "resolution", "type", "button_click");
        EventTestUtils.validateNestedEventValue(event, "resolution", "button_id", "button id");
        EventTestUtils.validateNestedEventValue(event, "resolution", "button_group", "button group");
        EventTestUtils.validateNestedEventValue(event, "resolution", "button_description", "button description");
        EventTestUtils.validateNestedEventValue(event, "resolution", "display_time", 3.5);
    }

    /**
     * Test button click resolution event uses the label if the button
     * does not have a description.
     */
    @Test
    public void testButtonClickResolutionEventNoButtonDescription() throws JSONException {
        Context context = mock(Context.class);
        when(context.getString(android.R.string.cancel)).thenReturn("cancel");

        NotificationActionButton actionButton = new NotificationActionButton.Builder("button id")
                .setLabel(android.R.string.cancel)
                .build();

        ResolutionEvent event = ResolutionEvent.createButtonClickedResolutionEvent(context, notification, actionButton, 3500);

        EventTestUtils.validateEventValue(event, "id", "ian id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", "send id");
        EventTestUtils.validateNestedEventValue(event, "resolution", "type", "button_click");
        EventTestUtils.validateNestedEventValue(event, "resolution", "button_id", "button id");
        EventTestUtils.validateNestedEventValue(event, "resolution", "button_group", "button group");
        EventTestUtils.validateNestedEventValue(event, "resolution", "button_description", "cancel");
        EventTestUtils.validateNestedEventValue(event, "resolution", "display_time", 3.5);
    }

    /**
     * Test on click resolution event.
     */
    @Test
    public void testClickedResolutionEvent() throws JSONException {
        ResolutionEvent event = ResolutionEvent.createClickedResolutionEvent(notification, 5500);

        EventTestUtils.validateEventValue(event, "id", "ian id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", "send id");
        EventTestUtils.validateNestedEventValue(event, "resolution", "type", "message_click");
        EventTestUtils.validateNestedEventValue(event, "resolution", "display_time", 5.5);
    }

    /**
     * Test replaced resolution event.
     */
    @Test
    public void testReplacedResolutionEvent() throws JSONException {
        InAppNotification replacement = new InAppNotification.Builder().setId("replacement id").create();
        ResolutionEvent event = ResolutionEvent.createReplacedResolutionEvent(notification, replacement);

        EventTestUtils.validateEventValue(event, "id", "ian id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", "send id");
        EventTestUtils.validateNestedEventValue(event, "resolution", "type", "replaced");
        EventTestUtils.validateNestedEventValue(event, "resolution", "replacement_id", "replacement id");
    }

    /**
     * Test direct open resolution event.
     */
    @Test
    public void testDirectOpenResolutionEvent() throws JSONException {
        ResolutionEvent event = ResolutionEvent.createDirectOpenResolutionEvent(notification);

        EventTestUtils.validateEventValue(event, "id", "ian id");
        EventTestUtils.validateNestedEventValue(event, "resolution", "type", "direct_open");
    }

    /**
     * Test expired resolution event.
     */
    @Test
    public void testExpiredResolutionEvent() throws JSONException {
        ResolutionEvent event = ResolutionEvent.createExpiredResolutionEvent(notification);

        EventTestUtils.validateEventValue(event, "id", "ian id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", "send id");
        EventTestUtils.validateNestedEventValue(event, "resolution", "type", "expired");
        EventTestUtils.validateNestedEventValue(event, "resolution", "expiry", DateUtils.createIso8601TimeStamp(notification.getExpiry()));
    }

    /**
     * Test user dismissed resolution event.
     */
    @Test
    public void testUserDismissedResolutionEvent() throws JSONException {
        ResolutionEvent event = ResolutionEvent.createUserDismissedResolutionEvent(notification, 3500);

        EventTestUtils.validateEventValue(event, "id", "ian id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", "send id");
        EventTestUtils.validateNestedEventValue(event, "resolution", "type", "user_dismissed");
        EventTestUtils.validateNestedEventValue(event, "resolution", "display_time", 3.5);
    }

    /**
     * Test timed out resolution event.
     */
    @Test
    public void testTimedOutResolutionEvent() throws JSONException {
        ResolutionEvent event = ResolutionEvent.createTimedOutResolutionEvent(notification, 15000);

        EventTestUtils.validateEventValue(event, "id", "ian id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", "send id");
        EventTestUtils.validateNestedEventValue(event, "resolution", "type", "timed_out");
        EventTestUtils.validateNestedEventValue(event, "resolution", "display_time", 15.0);
    }


    /**
     * Test conversion send id is not added to the event data if it doesn't exist.
     */
    @Test
    public void testNoConversionSendId() throws JSONException {
        UAirship.shared().getAnalytics().setConversionSendId(null);
        ResolutionEvent event = ResolutionEvent.createTimedOutResolutionEvent(notification, 15000);

        EventTestUtils.validateEventValue(event, "id", "ian id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", null);
    }

}
