/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions.tags;

import com.urbanairship.BaseTestCase;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionTestUtils;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class BaseTagsActionTest extends BaseTestCase {

    private TestBaseTagsAction action;
    private @Action.Situation int[] acceptedSituations;


    @Before
    public void setup() {
        action = new TestBaseTagsAction();

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

        args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "{group: [tag1, tag2, tag3]}");
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
    public void testGetTags() throws Exception {
        ActionArguments singleTagArg = ActionTestUtils.createArgs(Action.SITUATION_PUSH_OPENED, "tag1");
        action.perform(singleTagArg);

        ActionArguments collectionArgs = ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED,
                Arrays.asList("tag1", "tag2", "tag3"));
        action.perform(collectionArgs);

        ActionArguments badArgs = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, 1);
        action.perform(badArgs);

        ActionArguments nullArgs = ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, null);
        action.perform(nullArgs);

        assertEquals(2, action.applyTags.size());
        assertEquals(1, action.applyTags.get(0).size());
        assertTrue(action.applyTags.get(0).contains("tag1"));

        assertEquals(3, action.applyTags.get(1).size());
        assertTrue(action.applyTags.get(1).contains("tag1"));
        assertTrue(action.applyTags.get(1).contains("tag2"));
        assertTrue(action.applyTags.get(1).contains("tag3"));

        assertTrue(action.applyChannelTags.isEmpty());
        assertTrue(action.applyNamedUserTags.isEmpty());
    }

    @Test
    public void testApplyTagGroups() {
        Set<String> tags = new HashSet<>(Arrays.asList("tag1", "tag2", "tag3"));
        HashMap<String, JsonValue> tagGroup = new HashMap<>();
        tagGroup.put("channel", JsonMap.newBuilder().put("group1", JsonValue.wrapOpt(tags)).build().toJsonValue());
        tagGroup.put("named_user", JsonMap.newBuilder().put("group2", JsonValue.wrapOpt(tags)).build().toJsonValue());
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED, tagGroup);
        action.perform(args);

        assertEquals(1, action.applyChannelTags.size());
        assertEquals(tags, action.applyChannelTags.get("group1"));

        assertEquals(1, action.applyNamedUserTags.size());
        assertEquals(tags, action.applyNamedUserTags.get("group2"));

        assertTrue(action.applyTags.isEmpty());
    }

    private static class TestBaseTagsAction extends BaseTagsAction {

        List<Set<String>> applyTags = new ArrayList<>();
        Map<String, Set<String>> applyChannelTags = new HashMap<>();
        Map<String, Set<String>> applyNamedUserTags = new HashMap<>();

        @Override
        void applyChannelTags(Set<String> tags) {
            applyTags.add(tags);
        }

        @Override
        void applyChannelTagGroups(Map<String, Set<String>> tags) {
            applyChannelTags.putAll(tags);
        }

        @Override
        void applyNamedUserTagGroups(Map<String, Set<String>> tags) {
            applyNamedUserTags.putAll(tags);
        }
    }

}
