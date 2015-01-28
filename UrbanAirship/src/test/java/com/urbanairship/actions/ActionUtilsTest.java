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

import android.os.Bundle;

import com.urbanairship.RobolectricGradleTestRunner;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

@RunWith(RobolectricGradleTestRunner.class)
public class ActionUtilsTest {

    @Before
    public void setup() {
        ActionRegistry.shared().registerAction(new TestAction(), "testName", "anotherTestName");
    }

    @After
    public void tearDown() {
        ActionRegistry.shared().unregisterAction("testName");
    }

    /**
     * Test that parsing a push bundle for action names
     */
    @Test
    public void testParseActionNames() throws JSONException {
        JSONObject actionsPayload = new JSONObject();
        actionsPayload.put("action", "value");
        actionsPayload.put("anotherAction", JSONObject.NULL);

        Bundle bundle = new Bundle();
        bundle.putString("com.urbanairship.actions", actionsPayload.toString());

        Set<String> actionNames = ActionUtils.parseActionNames(bundle);

        assertEquals("Unexpected action names", 2, actionNames.size());
        assertTrue("Missing action name", actionNames.contains("action"));
        assertTrue("Missing action name", actionNames.contains("anotherAction"));
    }

    /**
     * Test that parsing an empty bundle for action names
     */
    @Test
    public void testParseActionNamesEmptyPayload() throws JSONException {
        Set<String> actionNames = ActionUtils.parseActionNames(new Bundle());
        assertTrue("Action name set should be empty.", actionNames.isEmpty());
    }

    /**
     * Test checking if a push bundle contains registered actions
     */
    @Test
    public void testContainsRegisteredActions() throws JSONException {
        JSONObject actionsPayload = new JSONObject();
        actionsPayload.put("testName", "value");

        Bundle bundle = new Bundle();
        bundle.putString("com.urbanairship.actions", actionsPayload.toString());

        assertTrue("Should find the action in the bundle",
                ActionUtils.containsRegisteredActions(bundle, "testName"));
        assertTrue("Should find the action in the bundle",
                ActionUtils.containsRegisteredActions(bundle, "anotherTestName"));
        assertTrue("Should find the action in the bundle",
                ActionUtils.containsRegisteredActions(bundle, "anotherTestName", "blahblah"));

        assertFalse("Should not find the action in the bundle",
                ActionUtils.containsRegisteredActions(bundle, "blahblah"));
        assertFalse("Should not find the action in the bundle if no action name is given",
                ActionUtils.containsRegisteredActions(bundle, (String[]) null));

    }

    /**
     * Test checking if an empty push bundle contains registered actions
     */
    @Test
    public void testContainsRegisteredActionsEmptyPayload() {
        Bundle bundle = new Bundle();
        assertFalse("Should not find the action in the empty bundle",
                ActionUtils.containsRegisteredActions(bundle, "testName"));

    }
}
