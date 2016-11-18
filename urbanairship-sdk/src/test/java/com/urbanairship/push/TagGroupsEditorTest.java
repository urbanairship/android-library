/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import com.urbanairship.BaseTestCase;
import com.urbanairship.job.Job;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class TagGroupsEditorTest extends BaseTestCase {

    private final String tagGroup = "someTagGroup";
    private TagGroupsEditor editor;
    private JobDispatcher mockDispatcher;

    @Before
    public void setUp() {
        mockDispatcher = Mockito.mock(JobDispatcher.class);
        editor = new TagGroupsEditor("my action", PushManager.class, mockDispatcher);
    }

    /**
     * Test empty tag group are ignored.
     */
    @Test
    public void testEmptyTagGroup() {
        editor.addTag("", "tag1");
        editor.setTag("", "tag2");
        editor.removeTag("", "tag3");
        editor.apply();

        verifyZeroInteractions(mockDispatcher);
    }

    /**
     * Test add and removes of tags that normalize to an empty set are ignored.
     */
    @Test
    public void testAddRemoveEmptyTags() {
        Set<String> testTags = new HashSet<>();
        testTags.add("");
        testTags.add("  ");
        testTags.add("  ");

        editor.addTags(tagGroup, testTags);
        editor.addTags(tagGroup, new HashSet<String>());

        editor.removeTags(tagGroup, testTags);
        editor.removeTags(tagGroup, new HashSet<String>());

        editor.apply();

        verifyZeroInteractions(mockDispatcher);
    }

    /**
     * Test setting null or empty set is allowed for set operations.
     */
    @Test
    public void testSetEmptyTags() throws JsonException {
        editor.setTags(tagGroup, null);
        editor.apply();

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<Job>() {
            @Override
            public boolean matches(Object argument) {
                Job job = (Job) argument;

                if (!job.getAction().equals("my action")) {
                    return false;
                }

                TagGroupMutation expected = TagGroupMutation.newSetTagsMutation(tagGroup, new HashSet<String>());

                JsonValue actual;
                try {
                    actual = JsonValue.parseString(job.getExtras().getString(TagGroupsEditor.EXTRA_TAG_GROUP_MUTATIONS));
                } catch (JsonException e) {
                    return false;
                }

                return actual.optList().size() == 1 && actual.optList().get(0).equals(expected.toJsonValue());
            }
        }));
    }
}
