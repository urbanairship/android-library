/* Copyright Airship and Contributors */

package com.urbanairship.actions.tags;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionTestUtils;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.TagEditor;
import com.urbanairship.channel.TagGroupsEditor;
import com.urbanairship.contacts.Contact;
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

public class AddTagsActionTest extends BaseTestCase {

    AddTagsAction action;
    AirshipChannel channel;

    @Before
    public void setup() {
        channel = mock(AirshipChannel.class);
        TestApplication.getApplication().setChannel(channel);
        action = new AddTagsAction();
    }

    /**
     * Test perform, should add tags
     */
    @Test
    public void testPerform() {
        final Set<String> addedTags = new HashSet<>();
        TagEditor tagEditor = new TagEditor() {
            @Override
            protected void onApply(boolean clear, @NonNull Set<String> tagsToAdd, @NonNull Set<String> tagsToRemove) {
                addedTags.addAll(tagsToAdd);
                assertTrue(tagsToRemove.isEmpty());
                assertFalse(clear);
            }
        };

        when(channel.editTags()).thenReturn(tagEditor);

        // Add foo and bar
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED, Arrays.asList("foo", "bar"));
        ActionResult result = action.perform(args);

        assertTrue(result.getValue().isNull());

        Set<String> expectedTags = new HashSet<>(Arrays.asList("foo", "bar"));
        assertEquals(expectedTags, addedTags);
    }

    @Test
    public void testPerformTagGroups() {
        final Map<String, Set<String>> added = new HashMap<>();

        Contact contact = mock(Contact.class);
        TagGroupsEditor tagGroupsEditor = new TagGroupsEditor() {
            @NonNull
            @Override
            public TagGroupsEditor addTags(@NonNull String group, @NonNull Set<String> tags) {
                added.put(group, tags);
                return this;
            }
        };

        when(channel.editTagGroups()).thenReturn(tagGroupsEditor);
        when(contact.editTagGroups()).thenReturn(tagGroupsEditor);

        TestApplication.getApplication().setContact(contact);

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

        assertTrue(added.keySet().contains("group2"));
        assertTrue(added.get("group2").contains("tag1"));
        assertTrue(added.get("group2").contains("tag2"));
        assertTrue(added.get("group2").contains("tag3"));
    }

}
