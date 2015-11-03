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

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import com.urbanairship.BaseTestCase;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

public class ClipboardActionTest extends BaseTestCase {

    private ClipboardAction action;

    private @Action.Situation int[] acceptedSituations;
    private @Action.Situation int[] rejectedSituations;
    private ClipboardManager clipboardManager;

    @Before
    public void setup() {
        action = new ClipboardAction();
        clipboardManager = (ClipboardManager) RuntimeEnvironment.application.getSystemService(Context.CLIPBOARD_SERVICE);

        // Accepted situations (All - PUSH_RECEIVED)
        acceptedSituations = new int[] {
                Action.SITUATION_PUSH_OPENED,
                Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION,
                Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON,
                Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON
        };

        // Rejected situations (All - accepted)
        rejectedSituations = new int[] {
                Action.SITUATION_PUSH_RECEIVED
        };
    }

    /**
     * Test accepts arguments with map action argument value.
     */
    @Test
    public void testAcceptsArgumentWithMap() {
        Map<String, String> clipboardMap = new HashMap<>();
        clipboardMap.put("label", "clipboard label");
        clipboardMap.put("text", "clipboard text");

        for (@Action.Situation int situation : acceptedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, clipboardMap);
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }

        for (@Action.Situation int situation : rejectedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, clipboardMap);
            assertFalse("Should reject arguments in situation " + situation,
                    action.acceptsArguments(args));
        }
    }

    /**
     * Test accepts arguments with string action argument value.
     */
    @Test
    public void testAcceptsArgumentWithString() {
        String clipboardString = "oh hi";

        for (@Action.Situation int situation : acceptedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, clipboardString);
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }

        for (@Action.Situation int situation : rejectedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, clipboardString);
            assertFalse("Should reject arguments in situation " + situation,
                    action.acceptsArguments(args));
        }
    }

    /**
     * Test perform with map action argument value.
     */
    @Test
    @SuppressLint("NewApi")
    public void testPerformWithMap() {
        Map<String, String> clipboardMap = new HashMap<>();
        clipboardMap.put("label", "clipboard label");
        clipboardMap.put("text", "clipboard text");

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_OPENED, clipboardMap);
        ActionResult result = action.perform(args);

        assertEquals(args.getValue(), result.getValue());

        ClipData clipData = clipboardManager.getPrimaryClip();
        assertEquals(clipData.getDescription().getLabel(), "clipboard label");
        assertEquals(clipData.getItemAt(0).getText(), "clipboard text");
    }

    /**
     * Test perform with string action argument value.
     */
    @Test
    @SuppressLint("NewApi")
    public void testPerformWithString() {
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_OPENED, "clipboard text");
        ActionResult result = action.perform(args);

        assertEquals(args.getValue(), result.getValue());

        ClipData clipData = clipboardManager.getPrimaryClip();
        assertNull(clipData.getDescription().getLabel());
        assertEquals(clipData.getItemAt(0).getText(), "clipboard text");
    }

}