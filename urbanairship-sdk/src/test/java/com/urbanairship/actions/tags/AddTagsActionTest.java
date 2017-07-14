/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions.tags;

import android.support.annotation.NonNull;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionTestUtils;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.NamedUser;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.TagGroupsEditor;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AddTagsActionTest extends BaseTestCase {

    AddTagsAction action;
    PushManager pushManager;

    @Before
    public void setup() {
        pushManager = mock(PushManager.class);
        TestApplication.getApplication().setPushManager(pushManager);
        action = new AddTagsAction();
    }

    /**
     * Test perform, should add tags
     */
    @Test
    public void testPerform() {
        Set<String> existingTags = new HashSet<>();
        existingTags.add("tagOne");
        existingTags.add("tagTwo");

        when(pushManager.getTags()).thenReturn(existingTags);

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, "tagThree");
        ActionResult result = action.perform(args);

        assertTrue("Add tags action should return 'null' result", result.getValue().isNull());

        // Verify we have original tags plus the added tagThree
        Set<String> expectedTags = new HashSet<>(existingTags);
        expectedTags.add("tagThree");
        verify(pushManager).setTags(expectedTags);
    }

    @Test
    public void testPerformTagGroups() throws Exception {
        final Map<String, Set<String>> added = new HashMap<>();
        PushManager pushManager = mock(PushManager.class);
        NamedUser namedUser = mock(NamedUser.class);
        TagGroupsEditor tagGroupsEditor = new TagGroupsEditor() {
            @Override
            public TagGroupsEditor addTags(@NonNull String group, @NonNull Set<String> tags) {
                added.put(group, tags);
                return this;
            }
        };

        when(pushManager.editTagGroups()).thenReturn(tagGroupsEditor);
        when(namedUser.editTagGroups()).thenReturn(tagGroupsEditor);

        TestApplication.getApplication().setPushManager(pushManager);
        TestApplication.getApplication().setNamedUser(namedUser);

        JsonValue tags = JsonValue.wrapOpt(Arrays.asList("tag1", "tag2", "tag3"));
        Map<String, JsonValue> tagGroup = new HashMap<>();
        tagGroup.put("channel", JsonMap.newBuilder().put("group1", tags).build().toJsonValue());
        tagGroup.put("named_user", JsonMap.newBuilder().put("group2", tags).build().toJsonValue());
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED, tagGroup);
        action.perform(args);

        assertEquals(2, added.size());
        assertTrue(added.keySet().contains("group1"));
        assertTrue(added.get("group1").contains("tag1"));
        assertTrue(added.get("group1").contains("tag2"));
        assertTrue(added.get("group1").contains("tag3"));

        assertTrue( added.keySet().contains("group2"));
        assertTrue(added.get("group2").contains("tag1"));
        assertTrue(added.get("group2").contains("tag2"));
        assertTrue(added.get("group2").contains("tag3"));
    }
}
