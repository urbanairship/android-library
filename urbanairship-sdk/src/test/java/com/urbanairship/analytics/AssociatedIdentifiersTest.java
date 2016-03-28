
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

import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;


public class AssociatedIdentifiersTest extends BaseTestCase {

    AssociatedIdentifiers identifiers;
    private AssociatedIdentifiers.Editor editor;

    @Before
    public void setup() {
        identifiers = new AssociatedIdentifiers();
        editor = new AssociatedIdentifiers.Editor(identifiers) {
            @Override
            void onApply(AssociatedIdentifiers theIdentifiers) {
                identifiers = theIdentifiers;
            }
        };
    }

    /**
     * Test the ID mapping
     */
    @Test
    public void testIds() {

        editor.setAdvertisingId("advertising Id", true)
              .addIdentifier("custom key", "custom value")
              .apply();

        assertEquals(identifiers.getIds().size(), 3);
        assertEquals("custom value", identifiers.getIds().get("custom key"));
        assertEquals(identifiers.getAdvertisingId(), identifiers.getIds().get("com.urbanairship.aaid"));
        assertTrue(identifiers.isLimitAdTrackingEnabled());
    }

    /**
     * Test the ID mapping when limited ad tracking is disabled
     */
    @Test
    public void testIdsLimitedAdTrackingDisabled() {

        editor.setAdvertisingId("advertising Id", false)
              .addIdentifier("custom key", "custom value")
              .apply();

        assertEquals(identifiers.getIds().size(), 3);
        assertEquals("custom value", identifiers.getIds().get("custom key"));
        assertEquals(identifiers.getAdvertisingId(), identifiers.getIds().get("com.urbanairship.aaid"));
        assertFalse(identifiers.isLimitAdTrackingEnabled());
    }

    /**
     * Test removing advertising ID (and limitedAdTrackingEnabled)
     */
    @Test
    public void testRemoveAdvertisingId() {

        editor.setAdvertisingId("advertising Id", true)
              .addIdentifier("custom key", "custom value")
              .apply();

        assertEquals(identifiers.getIds().size(), 3);
        assertEquals("custom value", identifiers.getIds().get("custom key"));
        assertEquals(identifiers.getAdvertisingId(), identifiers.getIds().get("com.urbanairship.aaid"));
        assertTrue(identifiers.isLimitAdTrackingEnabled());

        editor.removeAdvertisingId()
              .apply();

        assertEquals(identifiers.getIds().size(), 1);
        assertEquals("custom value", identifiers.getIds().get("custom key"));
        assertNull(identifiers.getIds().get("com.urbanairship.aaid"));
        assertNull(identifiers.getIds().get("com.urbanairship.limited_ad_tracking_enabled"));
    }

    /**
     * Test remove identifier
     */
    @Test
    public void testRemoveIdentifier() {

        editor.setAdvertisingId("advertising Id", true)
              .addIdentifier("custom key", "custom value")
              .apply();

        assertEquals(identifiers.getIds().size(), 3);
        assertEquals("custom value", identifiers.getIds().get("custom key"));
        assertEquals(identifiers.getAdvertisingId(), identifiers.getIds().get("com.urbanairship.aaid"));
        assertTrue(identifiers.isLimitAdTrackingEnabled());

        editor.removeIdentifier("custom key")
              .apply();

        assertEquals(identifiers.getIds().size(), 2);
        assertNull(identifiers.getIds().get("custom key"));
        assertEquals(identifiers.getAdvertisingId(), identifiers.getIds().get("com.urbanairship.aaid"));
        assertTrue(identifiers.isLimitAdTrackingEnabled());
    }

    /**
     * Test clear all identifiers
     */
    @Test
    public void testClearIdentifiers() {

        editor.setAdvertisingId("advertising Id", true)
              .addIdentifier("custom key", "custom value")
              .apply();

        assertEquals(identifiers.getIds().size(), 3);
        assertEquals("custom value", identifiers.getIds().get("custom key"));
        assertEquals(identifiers.getAdvertisingId(), identifiers.getIds().get("com.urbanairship.aaid"));
        assertTrue(identifiers.isLimitAdTrackingEnabled());

        editor.clear().apply();
        assertEquals(identifiers.getIds().size(), 0);
    }
}
