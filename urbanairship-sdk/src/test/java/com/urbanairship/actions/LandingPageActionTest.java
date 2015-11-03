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

package com.urbanairship.actions;

import android.content.Intent;

import com.urbanairship.BaseTestCase;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.shadows.ShadowApplication;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class LandingPageActionTest extends BaseTestCase {

    private LandingPageAction action;

    @Before
    public void setup() {
        action = new LandingPageAction();
    }

    /**
     * Test accepts arguments
     */
    @Test
    public void testAcceptsArguments() {
        // Basic URIs
        verifyAcceptsArgumentValue("www.urbanairship.com", true);

        // Content URIs
        verifyAcceptsArgumentValue("u:<~@rH7,ASuTABk.~>", true);

        // Payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("url", "http://example.com");
        payload.put("cache_on_receive", true);
        verifyAcceptsArgumentValue(payload, true);
    }

    /**
     * Test accepts arguments rejects payloads that do not
     * define a url
     */
    @Test
    public void testRejectsArguments() {
        verifyAcceptsArgumentValue(null, false);
        verifyAcceptsArgumentValue("", false);
        verifyAcceptsArgumentValue("u:", true);

        // Empty payload
        Map<String, Object> payload = new HashMap<>();
        verifyAcceptsArgumentValue(payload, false);
    }

    /**
     * Test perform for every situation the action accepts
     */
    @Test
    public void testPerform() {
        // Verify scheme less URIs turn into https
        verifyPerform("www.urbanairship.com", "https://www.urbanairship.com");

        // Verify common file URIs
        verifyPerform("file://urbanairship.com", "file://urbanairship.com");
        verifyPerform("https://www.urbanairship.com", "https://www.urbanairship.com");
        verifyPerform("http://www.urbanairship.com", "http://www.urbanairship.com");

        // Verify message URIs
        verifyPerform("message://message_id", "message://message_id");

        // Verify content URIs
        verifyPerform("u:<~@rH7,ASuTABk.~>", "https://dl.urbanairship.com/aaa/app_key/%3C%7E%40rH7%2CASuTABk.%7E%3E");

        // Verify basic payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("url", "http://example.com");
        verifyPerform(payload, "http://example.com");

        // Verify payload without a scheme
        payload.put("url", "www.example.com");
        verifyPerform(payload, "https://www.example.com");
    }

    private void verifyPerform(Object value, String expectedIntentData) {
        ShadowApplication application = ShadowApplication.getInstance();

        @Action.Situation int[] situations = new int[] {
                Action.SITUATION_PUSH_OPENED,
                Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION
        };

        for (@Action.Situation int situation : situations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, value);

            ActionResult result = action.perform(args);
            assertTrue("Should return 'null' result for situation " + situation, result.getValue().isNull());

            Intent intent = application.getNextStartedActivity();
            assertEquals("Invalid intent action for situation " + situation,
                    LandingPageAction.SHOW_LANDING_PAGE_INTENT_ACTION, intent.getAction());

            assertEquals("Invalid intent flags for situation " + situation,
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP, intent.getFlags());

            assertEquals("Wrong intent data for situation " + situation,
                    expectedIntentData, intent.getDataString());
        }

    }

    private void verifyAcceptsArgumentValue(Object value, boolean shouldAccept) {
        @Action.Situation int[] situations = new int[] {
                Action.SITUATION_PUSH_OPENED,
                Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION,
                Action.SITUATION_PUSH_RECEIVED,
                Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON
        };

        for (@Action.Situation int situation : situations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, value);
            if (shouldAccept) {
                assertTrue("Should accept arguments in situation " + situation,
                        action.acceptsArguments(args));
            } else {
                assertFalse("Should reject arguments in situation " + situation,
                        action.acceptsArguments(args));
            }

        }
    }
}
