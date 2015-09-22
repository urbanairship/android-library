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

package com.urbanairship.analytics;

import com.urbanairship.BaseTestCase;

import org.json.JSONException;
import org.junit.Test;

import java.util.UUID;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;


public class AssociateIdentifiersEventTest extends BaseTestCase {

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

    @Test
    public void testEventData() throws JSONException {
        AssociatedIdentifiers ids = new AssociatedIdentifiers.Builder()
                .setAdvertisingId("advertising Id")
                .setIdentifier("phone", "867-5309")
                .create();

        AssociateIdentifiersEvent event = new AssociateIdentifiersEvent(ids);
        EventTestUtils.validateEventValue(event, "com.urbanairship.aaid", "advertising Id");
        EventTestUtils.validateEventValue(event, "phone", "867-5309");
    }
}