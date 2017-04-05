/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import com.urbanairship.BaseTestCase;

import org.json.JSONException;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;


public class AssociateIdentifiersEventTest extends BaseTestCase {


    @Test
    public void testEventType() {
        AssociateIdentifiersEvent event = new AssociateIdentifiersEvent(new AssociatedIdentifiers());
        assertEquals(event.getType(), "associate_identifiers");
    }

    /**
     * Test the event is invalid when the associated identifiers exceed 100.
     */
    @Test
    public void testInvalidEvent() {
        Map<String, String> ids = new HashMap<>();
        for (int i = 0; i < 101; i++) {
            ids.put(UUID.randomUUID().toString(), "value");
        }

        // Verify its invalid
        assertFalse(new AssociateIdentifiersEvent(new AssociatedIdentifiers(ids)).isValid());
    }

    /**
     * Test the event is valid if it contains 0 to 100 ids.
     */
    @Test
    public void testValidEvent() {
        // Verify 0 Ids is valid
        assertTrue(new AssociateIdentifiersEvent(new AssociatedIdentifiers()).isValid());

        // Add 100
        Map<String, String> ids = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            ids.put(UUID.randomUUID().toString(), "value");
        }

        // Verify 100 Ids is valid
        assertTrue(new AssociateIdentifiersEvent(new AssociatedIdentifiers(ids)).isValid());
    }

    /**
     * Test event data when limited ad tracking enabled.
     * @throws JSONException
     */
    @Test
    public void testEventData() throws JSONException {
        Map<String, String> ids = new HashMap<>();
        ids.put("com.urbanairship.aaid", "advertising Id");
        ids.put("phone", "867-5309");
        ids.put("com.urbanairship.limited_ad_tracking_enabled", "true");

        AssociateIdentifiersEvent event = new AssociateIdentifiersEvent(new AssociatedIdentifiers(ids));
        EventTestUtils.validateEventValue(event, "com.urbanairship.aaid", "advertising Id");
        EventTestUtils.validateEventValue(event, "phone", "867-5309");
        EventTestUtils.validateEventValue(event, "com.urbanairship.limited_ad_tracking_enabled", "true");
    }

    /**
     * Test event data when limited ad tracking disabled.
     * @throws JSONException
     */
    @Test
    public void testEventDataWithLimitedTrackingDisabled() throws JSONException {
        Map<String, String> ids = new HashMap<>();
        ids.put("com.urbanairship.aaid", "advertising Id");
        ids.put("phone", "867-5309");
        ids.put("com.urbanairship.limited_ad_tracking_enabled", "false");

        AssociateIdentifiersEvent event = new AssociateIdentifiersEvent(new AssociatedIdentifiers(ids));
        EventTestUtils.validateEventValue(event, "com.urbanairship.aaid", "advertising Id");
        EventTestUtils.validateEventValue(event, "phone", "867-5309");
        EventTestUtils.validateEventValue(event, "com.urbanairship.limited_ad_tracking_enabled", "false");
    }
}
