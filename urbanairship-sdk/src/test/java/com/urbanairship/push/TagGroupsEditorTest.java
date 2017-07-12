/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import com.urbanairship.BaseTestCase;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

public class TagGroupsEditorTest extends BaseTestCase {

    private final String tagGroup = "someTagGroup";
    private TestTagGroupsEditor editor;

    @Before
    public void setUp() {
        editor = new TestTagGroupsEditor();
    }

    /**
     * Test empty tag group are ignored.
     */
    @Test
    public void testEmptyTagGroup() {
        editor.addTag("", "tag1")
              .setTag("", "tag2")
              .removeTag("", "tag3")
              .apply();

        assertTrue(editor.collapsedMutations.isEmpty());
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

        editor.addTags(tagGroup, testTags)
              .addTags(tagGroup, new HashSet<String>())
              .removeTags(tagGroup, testTags)
              .removeTags(tagGroup, new HashSet<String>())
              .apply();

        assertTrue(editor.collapsedMutations.isEmpty());
    }

    /**
     * Test setting null or empty set is allowed for set operations.
     */
    @Test
    public void testSetEmptyTags() {
        editor.setTags(tagGroup, null)
              .apply();


        assertEquals(1, editor.collapsedMutations.size());

        TagGroupsMutation expected = TagGroupsMutation.newSetTagsMutation(tagGroup, new HashSet<String>());
        assertEquals(expected, editor.collapsedMutations.get(0));
    }

    @Test
    public void testAllowTagGroupChanges() {
        editor = new TestTagGroupsEditor() {
            @Override
            protected boolean allowTagGroupChange(String tagGroup) {
                if (tagGroup.equals("ignore")) {
                    return false;
                }
                return true;
            }
        };

        editor.addTag("ignore", "hi")
              .removeTag(tagGroup, "cool")
              .apply();

        assertEquals(1, editor.collapsedMutations.size());

        TagGroupsMutation expected = TagGroupsMutation.newRemoveTagsMutation(tagGroup, new HashSet<>(Arrays.asList("cool")));
        assertEquals(expected, editor.collapsedMutations.get(0));
    }

    private static class TestTagGroupsEditor extends TagGroupsEditor {

        List<TagGroupsMutation> collapsedMutations;

        @Override
        protected void onApply(List<TagGroupsMutation> collapsedMutations) {
            this.collapsedMutations = collapsedMutations;
        }
    }
}
