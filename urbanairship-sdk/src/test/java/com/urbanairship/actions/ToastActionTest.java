/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.annotation.SuppressLint;
import android.widget.Toast;

import com.urbanairship.BaseTestCase;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.shadows.ShadowToast;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class ToastActionTest extends BaseTestCase {

    private ToastAction action;

    private
    @Action.Situation
    int[] acceptedSituations;
    private
    @Action.Situation
    int[] rejectedSituations;

    @Before
    public void setup() {
        action = new ToastAction();

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
        Map<String, Object> toastMap = new HashMap<>();
        toastMap.put("length", Toast.LENGTH_LONG);
        toastMap.put("text", "toast text");

        for (@Action.Situation int situation : acceptedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, toastMap);
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }

        for (@Action.Situation int situation : rejectedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, toastMap);
            assertFalse("Should reject arguments in situation " + situation,
                    action.acceptsArguments(args));
        }
    }

    /**
     * Test accepts arguments with string action argument value.
     */
    @Test
    public void testAcceptsArgumentWithString() {
        String toastText = "oh hi";

        for (@Action.Situation int situation : acceptedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, toastText);
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }

        for (@Action.Situation int situation : rejectedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, toastText);
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
        Map<String, Object> toastMap = new HashMap<>();
        toastMap.put("length", Toast.LENGTH_LONG);
        toastMap.put("text", "totes");

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_OPENED, toastMap);
        ActionResult result = action.perform(args);

        assertEquals(args.getValue(), result.getValue());

        assertEquals("totes", ShadowToast.getTextOfLatestToast());
        assertEquals(Toast.LENGTH_LONG, ShadowToast.getLatestToast().getDuration());
    }

    /**
     * Test perform with string action argument value.
     */
    @Test
    @SuppressLint("NewApi")
    public void testPerformWithString() {
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_OPENED, "totes");
        ActionResult result = action.perform(args);

        assertEquals(args.getValue(), result.getValue());

        assertEquals("totes", ShadowToast.getTextOfLatestToast());
        assertEquals(Toast.LENGTH_SHORT, ShadowToast.getLatestToast().getDuration());
    }
}
