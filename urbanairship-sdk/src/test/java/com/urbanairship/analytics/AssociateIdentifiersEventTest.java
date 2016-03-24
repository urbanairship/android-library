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

package com.urbanairship.analytics;

import com.urbanairship.BaseTestCase;

import org.json.JSONException;
import org.junit.Test;

import java.util.UUID;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;


public class AssociateIdentifiersEventTest extends BaseTestCase {

    // TODO: Remove tests using AssociatedIdentifiers.Builder() in 8.0.0, since
    // AssociatedIdentifiers.Builder() have been marked to be removed in 8.0.0
    @Test
    public void testEventType() {
        AssociateIdentifiersEvent event = new AssociateIdentifiersEvent(new AssociatedIdentifiers.Builder().create());
        assertEquals(event.getType(), "associate_identifiers");
    }

    /**
     * Test the event is invalid when the associated identifiers exceed 100.
     */
    @Test
    public void testInvalidEvent() {
        AssociatedIdentifiers.Builder builder = new AssociatedIdentifiers.Builder();
        for (int i = 0; i < 101; i++) {
            builder.setIdentifier(UUID.randomUUID().toString(), "value");
        }

        // Verify its invalid
        assertFalse(new AssociateIdentifiersEvent(builder.create()).isValid());
    }

    /**
     * Test the event is valid if it contains 0 to 100 ids.
     */
    @Test
    public void testValidEvent() {
        AssociatedIdentifiers.Builder builder = new AssociatedIdentifiers.Builder();

        // Verify 0 Ids is valid
        assertTrue(new AssociateIdentifiersEvent(builder.create()).isValid());

        // Add 100
        for (int i = 0; i < 100; i++) {
            builder.setIdentifier(UUID.randomUUID().toString(), "value");
        }

        // Verify 100 Ids is valid
        assertTrue(new AssociateIdentifiersEvent(builder.create()).isValid());
    }

    /**
     * Test event data when limited ad tracking enabled.
     * @throws JSONException
     */
    @Test
    public void testEventData() throws JSONException {
        AssociatedIdentifiers ids = new AssociatedIdentifiers.Builder()
                .setAdvertisingId("advertising Id")
                .setLimitedAdTrackingEnabled(true)
                .setIdentifier("phone", "867-5309")
                .create();

        AssociateIdentifiersEvent event = new AssociateIdentifiersEvent(ids);
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
        AssociatedIdentifiers ids = new AssociatedIdentifiers.Builder()
                .setAdvertisingId("advertising Id")
                .setLimitedAdTrackingEnabled(false)
                .setIdentifier("phone", "867-5309")
                .create();

        AssociateIdentifiersEvent event = new AssociateIdentifiersEvent(ids);
        EventTestUtils.validateEventValue(event, "com.urbanairship.aaid", "advertising Id");
        EventTestUtils.validateEventValue(event, "phone", "867-5309");
        EventTestUtils.validateEventValue(event, "com.urbanairship.limited_ad_tracking_enabled", "false");
    }

    /**
     * Test event type with Editor
     */
    @Test
    public void testEventTypeEditor() {
        AssociateIdentifiersEvent event = new AssociateIdentifiersEvent(new AssociatedIdentifiers.Editor().apply());
        assertEquals(event.getType(), "associate_identifiers");
    }

    /**
     * Test the event is invalid when the associated identifiers exceed 100 with Editor.
     */
    @Test
    public void testInvalidEventEditor() {
        AssociatedIdentifiers.Editor editor = new AssociatedIdentifiers.Editor();
        for (int i = 0; i < 101; i++) {
            editor.addIdentifier(UUID.randomUUID().toString(), "value");
        }

        // Verify its invalid
        assertFalse(new AssociateIdentifiersEvent(editor.apply()).isValid());
    }

    /**
     * Test the event is valid if it contains 0 to 100 ids with Editor.
     */
    @Test
    public void testValidEventEditor() {
        AssociatedIdentifiers.Editor editor = new AssociatedIdentifiers.Editor();

        // Verify 0 Ids is valid
        assertTrue(new AssociateIdentifiersEvent(editor.apply()).isValid());

        // Add 100
        for (int i = 0; i < 100; i++) {
            editor.addIdentifier(UUID.randomUUID().toString(), "value");
        }

        // Verify 100 Ids is valid
        assertTrue(new AssociateIdentifiersEvent(editor.apply()).isValid());
    }

    /**
     * Test event data when limited ad tracking enabled with Editor.
     * @throws JSONException
     */
    @Test
    public void testEventDataEditor() throws JSONException {
        AssociatedIdentifiers ids = new AssociatedIdentifiers.Editor()
                .setAdvertisingId("advertising Id", true)
                .addIdentifier("phone", "867-5309")
                .apply();

        AssociateIdentifiersEvent event = new AssociateIdentifiersEvent(ids);
        EventTestUtils.validateEventValue(event, "com.urbanairship.aaid", "advertising Id");
        EventTestUtils.validateEventValue(event, "phone", "867-5309");
        EventTestUtils.validateEventValue(event, "com.urbanairship.limited_ad_tracking_enabled", "true");
    }

    /**
     * Test event data when limited ad tracking disabled with Editor.
     * @throws JSONException
     */
    @Test
    public void testEventDataWithLimitedTrackingDisabledEditor() throws JSONException {
        AssociatedIdentifiers ids = new AssociatedIdentifiers.Editor()
                .setAdvertisingId("advertising Id", false)
                .addIdentifier("phone", "867-5309")
                .apply();

        AssociateIdentifiersEvent event = new AssociateIdentifiersEvent(ids);
        EventTestUtils.validateEventValue(event, "com.urbanairship.aaid", "advertising Id");
        EventTestUtils.validateEventValue(event, "phone", "867-5309");
        EventTestUtils.validateEventValue(event, "com.urbanairship.limited_ad_tracking_enabled", "false");
    }
}
