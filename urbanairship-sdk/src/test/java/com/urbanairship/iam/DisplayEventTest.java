/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.BaseTestCase;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.EventTestUtils;

import org.json.JSONException;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

public class DisplayEventTest extends BaseTestCase {

    /**
     * Test display event.
     */
    @Test
    public void testDisplayEvent() throws JSONException {
        UAirship.shared().getAnalytics().setConversionSendId("send id");
        UAirship.shared().getAnalytics().setConversionMetadata("metadata");

        DisplayEvent event = new DisplayEvent("message id");

        EventTestUtils.validateEventValue(event, "id", "message id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", "send id");
        EventTestUtils.validateEventValue(event, "conversion_metadata", "metadata");
        assertEquals("in_app_display", event.getType());
        assertTrue(event.isValid());
    }

    /**
     * Test display event when the conversion send id is null.
     */
    @Test
    public void testDisplayEventNoConversionSendId() throws JSONException {
        UAirship.shared().getAnalytics().setConversionSendId(null);
        UAirship.shared().getAnalytics().setConversionMetadata(null);

        DisplayEvent event = new DisplayEvent("message id");

        EventTestUtils.validateEventValue(event, "id", "message id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", null);
        EventTestUtils.validateEventValue(event, "conversion_metadata", null);
        assertEquals("in_app_display", event.getType());
        assertTrue(event.isValid());

    }
}
