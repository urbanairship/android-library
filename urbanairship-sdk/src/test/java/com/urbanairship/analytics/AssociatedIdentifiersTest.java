
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
import com.urbanairship.analytics.AssociatedIdentifiers;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;


public class AssociatedIdentifiersTest extends BaseTestCase {

    // TODO: Remove tests using AssociatedIdentifiers.Builder() in 8.0.0, since
    // AssociatedIdentifiers.Builder() have been marked to be removed in 8.0.0
    /**
     * Test the ID mapping
     */
    @Test
    public void testIds() {
        AssociatedIdentifiers identifiers = new AssociatedIdentifiers.Builder()
                .setAdvertisingId("advertising Id")
                .setLimitedAdTrackingEnabled(true)
                .setIdentifier("custom key", "custom value")
                .create();

        assertEquals(identifiers.getIds().size(), 3);
        assertEquals("custom value", identifiers.getIds().get("custom key"));
        assertEquals("advertising Id", identifiers.getIds().get("com.urbanairship.aaid"));
        assertEquals("true", identifiers.getIds().get("com.urbanairship.limited_ad_tracking_enabled"));
    }

    /**
     * Test the ID mapping when limited ad tracking is disabled
     */
    @Test
    public void testIdsLimitedAdTrackingDisabled() {
        AssociatedIdentifiers identifiers = new AssociatedIdentifiers.Builder()
                .setAdvertisingId("advertising Id")
                .setLimitedAdTrackingEnabled(false)
                .setIdentifier("custom key", "custom value")
                .create();

        assertEquals(identifiers.getIds().size(), 3);
        assertEquals("custom value", identifiers.getIds().get("custom key"));
        assertEquals("advertising Id", identifiers.getIds().get("com.urbanairship.aaid"));
        assertEquals("false", identifiers.getIds().get("com.urbanairship.limited_ad_tracking_enabled"));
    }

    /**
     * Test the ID mapping with Editor
     */
    @Test
    public void testIdsEditor() {
        AssociatedIdentifiers identifiers = new AssociatedIdentifiers.Editor()
                .setAdvertisingId("advertising Id", true)
                .addIdentifier("custom key", "custom value")
                .apply();

        assertEquals(identifiers.getIds().size(), 3);
        assertEquals("custom value", identifiers.getIds().get("custom key"));
        assertEquals("advertising Id", identifiers.getIds().get("com.urbanairship.aaid"));
        assertEquals("true", identifiers.getIds().get("com.urbanairship.limited_ad_tracking_enabled"));
    }

    /**
     * Test the ID mapping when limited ad tracking is disabled
     */
    @Test
    public void testIdsLimitedAdTrackingDisabledEditor() {
        AssociatedIdentifiers identifiers = new AssociatedIdentifiers.Editor()
                .setAdvertisingId("advertising Id", false)
                .addIdentifier("custom key", "custom value")
                .apply();

        assertEquals(identifiers.getIds().size(), 3);
        assertEquals("custom value", identifiers.getIds().get("custom key"));
        assertEquals("advertising Id", identifiers.getIds().get("com.urbanairship.aaid"));
        assertEquals("false", identifiers.getIds().get("com.urbanairship.limited_ad_tracking_enabled"));
    }

    /**
     * Test removing advertising ID with Editor
     */
    @Test
    public void testRemoveAdvertisingId() {
        AssociatedIdentifiers identifiers = new AssociatedIdentifiers.Editor()
                .setAdvertisingId("advertising Id", true)
                .addIdentifier("custom key", "custom value")
                .apply();

        assertEquals(identifiers.getIds().size(), 3);
        assertEquals("custom value", identifiers.getIds().get("custom key"));
        assertEquals("advertising Id", identifiers.getIds().get("com.urbanairship.aaid"));
        assertEquals("true", identifiers.getIds().get("com.urbanairship.limited_ad_tracking_enabled"));

        Map<String, String> ids = new HashMap<>(identifiers.getIds());
        AssociatedIdentifiers updatedIdentifiers = new AssociatedIdentifiers.Editor(ids)
                .removeAdvertisingId()
                .apply();

        assertEquals(updatedIdentifiers.getIds().size(), 1);
        assertEquals("custom value", updatedIdentifiers.getIds().get("custom key"));
        assertNull(updatedIdentifiers.getIds().get("com.urbanairship.aaid"));
    }

    /**
     * Test remove identifier with Editor
     */
    @Test
    public void testRemoveIdentifier() {
        AssociatedIdentifiers identifiers = new AssociatedIdentifiers.Editor()
                .setAdvertisingId("advertising Id", true)
                .addIdentifier("custom key", "custom value")
                .apply();

        assertEquals(identifiers.getIds().size(), 3);
        assertEquals("custom value", identifiers.getIds().get("custom key"));
        assertEquals("advertising Id", identifiers.getIds().get("com.urbanairship.aaid"));
        assertEquals("true", identifiers.getIds().get("com.urbanairship.limited_ad_tracking_enabled"));

        Map<String, String> ids = new HashMap<>(identifiers.getIds());
        AssociatedIdentifiers updatedIdentifiers = new AssociatedIdentifiers.Editor(ids)
                .removeIdentifier("custom key")
                .apply();

        assertEquals(updatedIdentifiers.getIds().size(), 2);
        assertNull(updatedIdentifiers.getIds().get("custom key"));
        assertEquals("advertising Id", updatedIdentifiers.getIds().get("com.urbanairship.aaid"));
        assertEquals("true", updatedIdentifiers.getIds().get("com.urbanairship.limited_ad_tracking_enabled"));
    }

    /**
     * Test clear all identifiers with Editor
     */
    @Test
    public void testClearAllIdentifiers() {
        AssociatedIdentifiers identifiers = new AssociatedIdentifiers.Editor()
                .setAdvertisingId("advertising Id", true)
                .addIdentifier("custom key", "custom value")
                .apply();

        assertEquals(identifiers.getIds().size(), 3);
        assertEquals("custom value", identifiers.getIds().get("custom key"));
        assertEquals("advertising Id", identifiers.getIds().get("com.urbanairship.aaid"));
        assertEquals("true", identifiers.getIds().get("com.urbanairship.limited_ad_tracking_enabled"));

        Map<String, String> ids = new HashMap<>(identifiers.getIds());
        AssociatedIdentifiers updatedIdentifiers = new AssociatedIdentifiers.Editor(ids)
                .clearAll()
                .apply();

        assertEquals(updatedIdentifiers.getIds().size(), 0);
    }
}
