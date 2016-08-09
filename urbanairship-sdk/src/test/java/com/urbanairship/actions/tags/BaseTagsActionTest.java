/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.actions.tags;

import com.urbanairship.BaseTestCase;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionTestUtils;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

public class BaseTagsActionTest extends BaseTestCase {

    BaseTagsAction action;
    private @Action.Situation int[] acceptedSituations;

    @Before
    public void setup() {
        action = new BaseTagsAction() {
            @Override
            public ActionResult perform(ActionArguments arguments) {
                return ActionResult.newEmptyResult();
            }
        };

        acceptedSituations = new int[] {
                Action.SITUATION_PUSH_OPENED,
                Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION,
                Action.SITUATION_PUSH_RECEIVED,
                Action.SITUATION_AUTOMATION,
                Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON,
                Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON
        };
    }

    /**
     * Test accepts arguments accepts any arguments that it can pass
     * a set of tags from
     */
    @Test
    public void testAcceptsArguments() {
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "tag1");
        assertTrue("Single tag is an acceptable argument", action.acceptsArguments(args));

        args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "[tag1,tag2,tag3]");
        assertTrue("JSON string of tags is an acceptable argument", action.acceptsArguments(args));

        args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, 1);
        assertFalse("Integer object is invalid arguments", action.acceptsArguments(args));

        args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, null);
        assertFalse(action.acceptsArguments(args));

        // Check every accepted situation
        for (@Action.Situation int situation : acceptedSituations) {
            args = ActionTestUtils.createArgs(situation, "tag1");
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }
    }

    /**
     * Test get tags parses the arguments correctly
     */
    @Test
    public void testGetTags() {
        ActionArguments singleTagArg = ActionTestUtils.createArgs(Action.SITUATION_PUSH_OPENED, "tag1");
        Set<String> tags = action.getTags(singleTagArg);
        assertEquals(1, tags.size());
        assertTrue(tags.contains("tag1"));


        ActionArguments collectionArgs = ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED,
                Arrays.asList("tag1", "tag2", "tag3"));

        tags = action.getTags(collectionArgs);
        assertEquals(3, tags.size());
        assertTrue(tags.contains("tag1"));
        assertTrue(tags.contains("tag2"));
        assertTrue(tags.contains("tag3"));

        ActionArguments badArgs = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, 1);
        assertNull(action.getTags(badArgs));

        ActionArguments nullArgs = ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, null);
        assertNull(action.getTags(nullArgs));
    }

}
