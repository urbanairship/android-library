/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.BaseTestCase;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.EventTestUtils;
import com.urbanairship.util.DateUtils;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class ResolutionEventTest extends BaseTestCase {

    @Before
    public void before() {
        UAirship.shared().getAnalytics().setConversionSendId("send id");
        UAirship.shared().getAnalytics().setConversionMetadata("metadata");
    }

    /**
     * Test button click resolution event.
     */
    @Test
    public void testButtonClickResolutionEvent() throws JSONException {
        ButtonInfo buttonInfo = ButtonInfo.newBuilder()
                                          .setId("button id")
                                          .setLabel(TextInfo.newBuilder()
                                                            .setText("hi")
                                                            .build())
                                          .build();


        ResolutionEvent event = ResolutionEvent.messageResolution("iam id", ResolutionInfo.buttonPressed(buttonInfo , 3500));

        EventTestUtils.validateEventValue(event, "id", "iam id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", "send id");
        EventTestUtils.validateEventValue(event, "conversion_metadata", "metadata");
        EventTestUtils.validateNestedEventValue(event, "resolution", "type", "button_click");
        EventTestUtils.validateNestedEventValue(event, "resolution", "button_id", "button id");
        EventTestUtils.validateNestedEventValue(event, "resolution", "button_description", "hi");
        EventTestUtils.validateNestedEventValue(event, "resolution", "display_time", 3.5);
        assertEquals("in_app_resolution", event.getType());
    }


    /**
     * Test on click resolution event.
     */
    @Test
    public void testClickedResolutionEvent() throws JSONException {
        ResolutionEvent event = ResolutionEvent.messageResolution("iam id", ResolutionInfo.messageClicked(5500));

        EventTestUtils.validateEventValue(event, "id", "iam id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", "send id");
        EventTestUtils.validateEventValue(event, "conversion_metadata", "metadata");
        EventTestUtils.validateNestedEventValue(event, "resolution", "type", "message_click");
        EventTestUtils.validateNestedEventValue(event, "resolution", "display_time", 5.5);
        assertEquals("in_app_resolution", event.getType());
    }

    /**
     * Test user dismissed resolution event.
     */
    @Test
    public void testUserDismissedResolutionEvent() throws JSONException {
        ResolutionEvent event = ResolutionEvent.messageResolution("iam id", ResolutionInfo.dismissed(3500));

        EventTestUtils.validateEventValue(event, "id", "iam id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", "send id");
        EventTestUtils.validateEventValue(event, "conversion_metadata", "metadata");
        EventTestUtils.validateNestedEventValue(event, "resolution", "type", "user_dismissed");
        EventTestUtils.validateNestedEventValue(event, "resolution", "display_time", 3.5);
        assertEquals("in_app_resolution", event.getType());
    }

    /**
     * Test timed out resolution event.
     */
    @Test
    public void testTimedOutResolutionEvent() throws JSONException {
        ResolutionEvent event = ResolutionEvent.messageResolution("iam id", ResolutionInfo.timedOut(15000));

        EventTestUtils.validateEventValue(event, "id", "iam id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", "send id");
        EventTestUtils.validateEventValue(event, "conversion_metadata", "metadata");
        EventTestUtils.validateNestedEventValue(event, "resolution", "type", "timed_out");
        EventTestUtils.validateNestedEventValue(event, "resolution", "display_time", 15.0);
        assertEquals("in_app_resolution", event.getType());
    }


    /**
     * Test replaced resolution event.
     */
    @Test
    public void testReplacedResolutionEvent() throws JSONException {
        ResolutionEvent event = ResolutionEvent.legacyMessageReplaced("iam id", "replacement id");

        EventTestUtils.validateEventValue(event, "id", "iam id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", "send id");
        EventTestUtils.validateEventValue(event, "conversion_metadata", "metadata");
        EventTestUtils.validateNestedEventValue(event, "resolution", "type", "replaced");
        EventTestUtils.validateNestedEventValue(event, "resolution", "replacement_id", "replacement id");
        assertEquals("in_app_resolution", event.getType());
    }

    /**
     * Test direct open resolution event.
     */
    @Test
    public void testDirectOpenResolutionEvent() throws JSONException {
        ResolutionEvent event = ResolutionEvent.legacyMessagePushOpened("iam id");

        EventTestUtils.validateEventValue(event, "id", "iam id");
        EventTestUtils.validateNestedEventValue(event, "resolution", "type", "direct_open");
        assertEquals("in_app_resolution", event.getType());
    }

    /**
     * Test expired resolution event.
     */
    @Test
    public void testExpiredResolutionEvent() throws JSONException {
        long expiry = System.currentTimeMillis();

        ResolutionEvent event = ResolutionEvent.messageExpired("iam id", expiry);

        EventTestUtils.validateEventValue(event, "id", "iam id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", "send id");
        EventTestUtils.validateEventValue(event, "conversion_metadata", "metadata");
        EventTestUtils.validateNestedEventValue(event, "resolution", "type", "expired");
        EventTestUtils.validateNestedEventValue(event, "resolution", "expiry", DateUtils.createIso8601TimeStamp(expiry));
        assertEquals("in_app_resolution", event.getType());
    }


}
