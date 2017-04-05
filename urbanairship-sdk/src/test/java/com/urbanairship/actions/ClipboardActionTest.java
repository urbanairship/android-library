/* Copyright 2017 Urban Airship and Contributors */

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
                Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON,
                Action.SITUATION_AUTOMATION
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
