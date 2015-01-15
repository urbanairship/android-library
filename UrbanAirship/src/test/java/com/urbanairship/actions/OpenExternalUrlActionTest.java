/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

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
import android.net.Uri;

import com.urbanairship.RobolectricGradleTestRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.shadows.ShadowApplication;

import java.net.MalformedURLException;
import java.net.URL;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

@RunWith(RobolectricGradleTestRunner.class)
public class OpenExternalUrlActionTest {

    private OpenExternalUrlAction action;


    @Before
    public void setup() {
        action = new OpenExternalUrlAction();
    }

    /**
     * Test accepts arguments
     */
    @Test
    public void testAcceptsArguments() throws MalformedURLException {
        ActionArguments args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, "http://example.com");
        assertTrue("Should accept valid url string", action.acceptsArguments(args));

        args = ActionTestUtils.createArgs(Situation.WEB_VIEW_INVOCATION, "adfadfafdsaf adfa dsfadfsa example");
        assertTrue("Should accept any string", action.acceptsArguments(args));

        args = ActionTestUtils.createArgs(Situation.PUSH_OPENED, new URL("http://example.com"));
        assertTrue("Should accept valid url", action.acceptsArguments(args));

        args = ActionTestUtils.createArgs(Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON, new URL("http://example.com"));
        assertTrue("Should accept Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON", action.acceptsArguments(args));

        args = ActionTestUtils.createArgs(Situation.PUSH_RECEIVED, new URL("http://example.com"));
        assertFalse("Should not accept Situation.PUSH_RECEIVED", action.acceptsArguments(args));
    }

    /**
     * Test perform tries to start an activity with the URL
     */
    @Test
    public void testPerform() throws MalformedURLException {
        ActionArguments args = ActionTestUtils.createArgs(Situation.WEB_VIEW_INVOCATION, "http://example.com");
        ActionResult result = action.perform(args);

        assertEquals("Value should be the uri", "http://example.com", result.getValue().toString());
        validateLastActivity("http://example.com");

        args = ActionTestUtils.createArgs(Situation.WEB_VIEW_INVOCATION, "adfadfafdsaf adfa dsfadfsa example");
        result = action.perform(args);
        assertEquals("Value should be the uri", "adfadfafdsaf adfa dsfadfsa example", result.getValue().toString());
        validateLastActivity("adfadfafdsaf adfa dsfadfsa example");

        args = ActionTestUtils.createArgs(Situation.WEB_VIEW_INVOCATION, new URL("http://example.com"));
        result = action.perform(args);
        assertEquals("Value should be the uri", "http://example.com", result.getValue().toString());
        validateLastActivity("http://example.com");

        args = ActionTestUtils.createArgs(Situation.WEB_VIEW_INVOCATION, Uri.parse("http://example.com"));
        result = action.perform(args);
        assertEquals("Value should be the uri", "http://example.com", result.getValue().toString());
        validateLastActivity("http://example.com");
    }

    /**
     * Helper method to validate the activity is launched correctly from
     * the open url action
     */
    private void validateLastActivity(String expectedUri) {
        ShadowApplication application = Robolectric.getShadowApplication();
        Intent intent = application.getNextStartedActivity();
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.getFlags());
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals(expectedUri, intent.getDataString());
    }
}
