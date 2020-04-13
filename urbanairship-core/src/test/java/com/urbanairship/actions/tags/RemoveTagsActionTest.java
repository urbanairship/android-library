/* Copyright Airship and Contributors */

package com.urbanairship.actions.tags;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionTestUtils;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.NamedUser;
import com.urbanairship.channel.TagEditor;
import com.urbanairship.channel.TagGroupsEditor;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RemoveTagsActionTest extends BaseTestCase {

    RemoveTagsAction action;
    AirshipChannel channel;

    @Before
    public void setup() {
        channel = mock(AirshipChannel.class);
        TestApplication.getApplication().setChannel(channel);

        action = new RemoveTagsAction();
    }

    /**
     * Test perform, should remove tags
     */
    @Test
    public void testPerform() {
        final Set<String> removedTags = new HashSet<>();
        TagEditor tagEditor = new TagEditor() {
            @Override
            protected void onApply(boolean clear, @NonNull Set<String> tagsToAdd, @NonNull Set<String> tagsToRemove) {
                removedTags.addAll(tagsToRemove);
                assertTrue(tagsToAdd.isEmpty());
                assertFalse(clear);
            }
        };

        when(channel.editTags()).thenReturn(tagEditor);

        // Remove foo and bar
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED, Arrays.asList("foo", "bar"));
        ActionResult result = action.perform(args);

        assertTrue(result.getValue().isNull());

        Set<String> expectedTags = new HashSet<>(Arrays.asList("foo", "bar"));
        assertEquals(expectedTags, removedTags);
    }

    @Test
    public void testPerformTagGroups() throws Exception {
        final Map<String, Set<String>> removed = new HashMap<>();
        NamedUser namedUser = mock(NamedUser.class);

        TagGroupsEditor tagGroupsEditor = new TagGroupsEditor() {
            @NonNull
            @Override
            public TagGroupsEditor removeTags(@NonNull String group, @NonNull Set<String> tags) {
                removed.put(group, tags);
                return this;
            }
        };

        when(channel.editTagGroups()).thenReturn(tagGroupsEditor);
        when(namedUser.editTagGroups()).thenReturn(tagGroupsEditor);

        TestApplication.getApplication().setNamedUser(namedUser);

        JsonValue tags = JsonValue.wrapOpt(Arrays.asList("tag1", "tag2", "tag3"));
        Map<String, JsonValue> tagGroup = new HashMap<>();
        tagGroup.put("channel", JsonMap.newBuilder().put("group1", tags).build().toJsonValue());
        tagGroup.put("named_user", JsonMap.newBuilder().put("group2", tags).build().toJsonValue());
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED, tagGroup);
        action.perform(args);

        assertEquals(2, removed.size());
        assertTrue(removed.keySet().contains("group1"));
        assertTrue(removed.get("group1").contains("tag1"));
        assertTrue(removed.get("group1").contains("tag2"));
        assertTrue(removed.get("group1").contains("tag3"));

        assertTrue(removed.keySet().contains("group2"));
        assertTrue(removed.get("group2").contains("tag1"));
        assertTrue(removed.get("group2").contains("tag2"));
        assertTrue(removed.get("group2").contains("tag3"));
    }

}
