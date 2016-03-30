
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;


public class AssociatedIdentifiersTest extends BaseTestCase {

    private AssociatedIdentifiers identifiers;
    private AssociatedIdentifiers.Editor editor;

    // Set by the AssociatedIdentifiers editor onApply
    private Boolean clear;
    private Map<String, String> idsToAdd;
    private List<String> idsToRemove;

    @Before
    public void setup() {
        clear = null;
        idsToAdd = null;
        idsToRemove = null;
        identifiers = new AssociatedIdentifiers();

        editor = new AssociatedIdentifiers.Editor() {
            @Override
            void onApply(boolean clear, Map<String, String> idsToAdd, List<String> idsToRemove) {
                AssociatedIdentifiersTest.this.clear = clear;
                AssociatedIdentifiersTest.this.idsToAdd = idsToAdd;
                AssociatedIdentifiersTest.this.idsToRemove = idsToRemove;
            }
        };
    }

    /**
     * Test the ID mapping
     */
    @Test
    public void testIds() {

        Map<String, String> ids = new HashMap<>();
        ids.put("com.urbanairship.aaid", "advertising ID");
        ids.put("com.urbanairship.limited_ad_tracking_enabled", "true");
        ids.put("custom key", "custom value");

        AssociatedIdentifiers identifiers = new AssociatedIdentifiers(ids);

        assertEquals(identifiers.getIds().size(), 3);
        assertEquals("custom value", identifiers.getIds().get("custom key"));
        assertEquals(identifiers.getAdvertisingId(), identifiers.getIds().get("com.urbanairship.aaid"));
        assertTrue(identifiers.isLimitAdTrackingEnabled());
    }

    /**
     * Test setAdvertisingId
     */
    @Test
    public void testSetAdvertisingId() {

        editor.setAdvertisingId("advertising Id", true)
              .addIdentifier("custom key", "custom value")
              .apply();

        assertEquals(3, idsToAdd.size());
        assertEquals("advertising Id", idsToAdd.get("com.urbanairship.aaid"));
        assertEquals("true", idsToAdd.get("com.urbanairship.limited_ad_tracking_enabled"));
        assertEquals("custom value", idsToAdd.get("custom key"));

        assertEquals(0, idsToRemove.size());
        assertFalse(clear);
    }

    /**
     * Test the ID mapping when limit ad tracking is disabled
     */
    @Test
    public void testLimitAdTrackingDisabled() {

        editor.setAdvertisingId("advertising Id", false)
              .addIdentifier("custom key", "custom value")
              .apply();

        assertEquals(3, idsToAdd.size());
        assertEquals("advertising Id", idsToAdd.get("com.urbanairship.aaid"));
        assertEquals("false", idsToAdd.get("com.urbanairship.limited_ad_tracking_enabled"));
        assertEquals("custom value", idsToAdd.get("custom key"));

        assertEquals(0, idsToRemove.size());
        assertFalse(clear);
    }

    /**
     * Test removing advertising ID (and limitedAdTrackingEnabled)
     */
    @Test
    public void testRemoveAdvertisingId() {

        editor.removeAdvertisingId()
              .apply();

        assertEquals(0, idsToAdd.size());
        assertEquals(2, idsToRemove.size());
        assertTrue(idsToRemove.contains("com.urbanairship.aaid"));
        assertTrue(idsToRemove.contains("com.urbanairship.limited_ad_tracking_enabled"));
        assertFalse(clear);
    }

    /**
     * Test remove identifier
     */
    @Test
    public void testRemoveIdentifier() {

        editor.removeIdentifier("custom key")
              .apply();

        assertEquals(0, idsToAdd.size());
        assertEquals(1, idsToRemove.size());
        assertTrue(idsToRemove.contains("custom key"));
        assertFalse(clear);
    }

    /**
     * Test clear all identifiers
     */
    @Test
    public void testClearIdentifiers() {

        editor.clear().apply();

        assertEquals(0, idsToAdd.size());
        assertEquals(0, idsToRemove.size());
        assertTrue(clear);
    }
}
