package com.urbanairship.push;

import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class TagGroupsEditorTest {

    private final String tagGroup = "someTagGroup";
    private TagGroupsEditor editor;

    @Before
    public void setUp() {
        editor = new TagGroupsEditor() {
            @Override
            public void apply() {

            }
        };
    }

    /**
     * Test add tags to the tag group.
     */
    @Test
    public void testAddTags() {
        Set<String> expectedTags = new HashSet<>();
        expectedTags.add("tag1");
        expectedTags.add("tag2");
        expectedTags.add("tag3");

        editor.addTags(tagGroup, "tag1", "tag2", "tag3");

        assertEquals(1, editor.tagsToAdd.size());
        assertEquals(3, editor.tagsToAdd.get(tagGroup).size());
        assertEquals(expectedTags, editor.tagsToAdd.get(tagGroup));
    }

    /**
     * Test add a collection of tags to the tag group.
     */
    @Test
    public void testAddTagsCollection() {
        Set<String> expectedTags = new HashSet<>();
        expectedTags.add("tag1");
        expectedTags.add("tag2");
        expectedTags.add("tag3");

        editor.addTags(tagGroup, expectedTags);

        assertEquals(1, editor.tagsToAdd.size());
        assertEquals(3, editor.tagsToAdd.get(tagGroup).size());
        assertEquals(expectedTags, editor.tagsToAdd.get(tagGroup));
    }

    /**
     * Test remove tags from the tag group.
     */
    @Test
    public void testRemoveTags() {
        Set<String> expectedTags = new HashSet<>();
        expectedTags.add("tag1");
        expectedTags.add("tag2");
        expectedTags.add("tag3");

        editor.removeTags(tagGroup, "tag1", "tag2", "tag3");

        assertEquals(1, editor.tagsToRemove.size());
        assertEquals(3, editor.tagsToRemove.get(tagGroup).size());
        assertEquals(expectedTags, editor.tagsToRemove.get(tagGroup));
    }

    /**
     * Test remove collection of tags from the tag group.
     */
    @Test
    public void testRemoveTagsCollection() {
        Set<String> expectedTags = new HashSet<>();
        expectedTags.add("tag1");
        expectedTags.add("tag2");
        expectedTags.add("tag3");

        editor.removeTags(tagGroup, expectedTags);

        assertEquals(1, editor.tagsToRemove.size());
        assertEquals(3, editor.tagsToRemove.get(tagGroup).size());
        assertEquals(expectedTags, editor.tagsToRemove.get(tagGroup));
    }

    /**
     * Test add a tag will override prior remove tag edit.
     */
    @Test
    public void testAddTagsLast() {
        editor.removeTags(tagGroup, "tag1", "tag2", "tag4");
        editor.addTags(tagGroup, "tag1", "tag2", "tag3");

        // addTags: tag1, tag2, tag3
        assertEquals(1, editor.tagsToAdd.size());
        assertEquals(3, editor.tagsToAdd.get(tagGroup).size());
        // removeTags: tag4
        assertEquals(1, editor.tagsToRemove.size());
        assertEquals(1, editor.tagsToRemove.get(tagGroup).size());
    }

    /**
     * Test remove a tag will override prior add tag edit.
     */
    @Test
    public void testRemoveTagsLast() {
        editor.addTags(tagGroup, "tag1", "tag2", "tag3");
        editor.removeTags(tagGroup, "tag1", "tag2", "tag4");

        // addTags: tag3
        assertEquals(1, editor.tagsToAdd.size());
        assertEquals(1, editor.tagsToAdd.get(tagGroup).size());
        // removeTags: tag1, tag2, tag4
        assertEquals(1, editor.tagsToRemove.size());
        assertEquals(3, editor.tagsToRemove.get(tagGroup).size());
    }
}
