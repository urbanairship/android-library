package com.urbanairship.push;

import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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

        assertEquals("Expect size to be 1", 1, editor.tagsToAdd.size());
        assertEquals("Expect size to be 3", 3, editor.tagsToAdd.get(tagGroup).size());
        assertEquals("Expect tags to match", expectedTags, editor.tagsToAdd.get(tagGroup));
        assertEquals("Expect size to be 0", 0, editor.tagsToRemove.size());

        editor.removeTags(tagGroup, "tag1");

        assertEquals("Expect size to be 2", 2, editor.tagsToAdd.get(tagGroup).size());
        assertEquals("Expect size to be 1", 1, editor.tagsToRemove.size());
        assertEquals("Expect size to be 1", 1, editor.tagsToRemove.get(tagGroup).size());

        editor.addTags(tagGroup, "tag1");

        assertEquals("Expect size to be 3", 3, editor.tagsToAdd.get(tagGroup).size());
        assertEquals("Expect size to be 0", 0, editor.tagsToRemove.size());

        editor.removeTags(tagGroup, "tag1", "tag2", "tag3");

        assertEquals("Expect size to be 0", 0, editor.tagsToAdd.size());
        assertEquals("Expect size to be 3", 3, editor.tagsToRemove.get(tagGroup).size());
    }

    /**
     * Test add a set of tags to the tag group.
     */
    @Test
    public void testAddTagsSet() {
        Set<String> expectedTags = new HashSet<>();
        expectedTags.add("tag1");
        expectedTags.add("tag2");
        expectedTags.add("tag3");

        editor.addTags(tagGroup, expectedTags);

        assertEquals("Expect size to be 1", 1, editor.tagsToAdd.size());
        assertEquals("Expect size to be 3", 3, editor.tagsToAdd.get(tagGroup).size());
        assertEquals("Expect tags to match", expectedTags, editor.tagsToAdd.get(tagGroup));
    }

    /**
     * Test add a set of tags to the tag group.
     */
    @Test
    public void testAddTagsSetNormalized() {
        Set<String> expectedTags = new HashSet<>();
        expectedTags.add("tag1");
        expectedTags.add("tag2");
        expectedTags.add("tag3");

        editor.addTags(tagGroup, " tag1 ", " tag2 ", " tag3 ");
        assertEquals("Expect tags to match", expectedTags, editor.tagsToAdd.get(tagGroup));

        editor.addTags(tagGroup, " tag3 ", " tag 4 ", " tag 5 ");
        expectedTags.add("tag 4");
        expectedTags.add("tag 5");

        expectedTags.removeAll(editor.tagsToAdd.get(tagGroup));
        assertEquals("Expect expectedTags size to be 0", 0, expectedTags.size());
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

        assertEquals("Expect size to be 1", 1, editor.tagsToRemove.size());
        assertEquals("Expect size to be 3", 3, editor.tagsToRemove.get(tagGroup).size());
        assertEquals("Expect tags to match", expectedTags, editor.tagsToRemove.get(tagGroup));

        editor.removeTags(tagGroup, "tag4");

        assertEquals("Expect size to be 4", 4, editor.tagsToRemove.get(tagGroup).size());

    }

    /**
     * Test remove tags from the tag group.
     */
    @Test
    public void testRemoveTagsNormalized() {
        Set<String> expectedTags = new HashSet<>();
        expectedTags.add("tag1");
        expectedTags.add("tag2");
        expectedTags.add("tag3");

        editor.removeTags(tagGroup, " tag1 ", " tag2 ", " tag3 ");
        assertEquals("Expect tags to match", expectedTags, editor.tagsToRemove.get(tagGroup));

        editor.removeTags(tagGroup, " tag3 ", " tag 4 ", " tag 5 ");
        expectedTags.add("tag 4");
        expectedTags.add("tag 5");

        expectedTags.removeAll(editor.tagsToRemove.get(tagGroup));
        assertEquals("Expect expectedTags size to be 0", 0, expectedTags.size());
    }

    /**
     * Test remove a set of tags from the tag group.
     */
    @Test
    public void testRemoveTagsSet() {
        Set<String> expectedTags = new HashSet<>();
        expectedTags.add("tag1");
        expectedTags.add("tag2");
        expectedTags.add("tag3");

        editor.removeTags(tagGroup, expectedTags);

        assertEquals("Expect size to be 1", 1, editor.tagsToRemove.size());
        assertEquals("Expect size to be 3", 3, editor.tagsToRemove.get(tagGroup).size());
        assertEquals("Expect tags to match", expectedTags, editor.tagsToRemove.get(tagGroup));
    }

    /**
     * Test add a tag will override prior remove tag edit.
     */
    @Test
    public void testAddTagsLast() {
        editor.removeTags(tagGroup, "tag1", "tag2", "tag4");
        editor.addTags(tagGroup, "tag1", "tag2", "tag3");

        // addTags: tag1, tag2, tag3
        assertEquals("Expect size to be 1", 1, editor.tagsToAdd.size());
        assertEquals("Expect size to be 3", 3, editor.tagsToAdd.get(tagGroup).size());
        // removeTags: tag4
        assertEquals("Expect size to be 1", 1, editor.tagsToRemove.size());
        assertEquals("Expect size to be 1", 1, editor.tagsToRemove.get(tagGroup).size());
    }

    /**
     * Test remove a tag will override prior add tag edit.
     */
    @Test
    public void testRemoveTagsLast() {
        editor.addTags(tagGroup, "tag1", "tag2", "tag3");
        editor.removeTags(tagGroup, "tag1", "tag2", "tag4");

        // addTags: tag3
        assertEquals("Expect size to be 1", 1, editor.tagsToAdd.size());
        assertEquals("Expect size to be 1", 1, editor.tagsToAdd.get(tagGroup).size());
        // removeTags: tag1, tag2, tag4
        assertEquals("Expect size to be 1", 1, editor.tagsToRemove.size());
        assertEquals("Expect size to be 3", 3, editor.tagsToRemove.get(tagGroup).size());
    }

    /**
     * Test adding empty tags does not add tags.
     */
    @Test
    public void testAddingEmptyTags() {
        Set<String> emptyTags = new HashSet<>();
        editor.addTags(tagGroup, emptyTags);
        assertEquals("Expect size to be 0", 0, editor.tagsToAdd.size());

        editor.removeTags(tagGroup, emptyTags);
        assertEquals("Expect size to be 0", 0, editor.tagsToRemove.size());
    }

    /**
     * Test adding null tag group does not add tags.
     */
    @Test
    public void testAddingNullTagGroup() {
        editor.addTags(null, "tag1");
        assertEquals("Expect size to be 0", 0, editor.tagsToAdd.size());

        editor.removeTags(null, "tag2");
        assertEquals("Expect size to be 0", 0, editor.tagsToRemove.size());
    }

    /**
     * Test when addTags and removeTags intersect.
     */
    @Test
    public void testTagsIntersect() {
        Set<String> expectedTags = new HashSet<>();
        expectedTags.add("tag1");
        expectedTags.add("tag2");
        expectedTags.add("tag3");

        editor.addTags(tagGroup, expectedTags);
        assertEquals("Expect size to be 1", 1, editor.tagsToAdd.size());
        assertEquals("Expect size to be 3", 3, editor.tagsToAdd.get(tagGroup).size());
        assertEquals("Expect tags to match", expectedTags, editor.tagsToAdd.get(tagGroup));

        editor.removeTags(tagGroup, expectedTags);
        assertEquals("Expect size to be 0", 0, editor.tagsToAdd.size());
        assertNull("Expect to be null", editor.tagsToAdd.get(tagGroup));
        assertEquals("Expect size to be 1", 1, editor.tagsToRemove.size());
        assertEquals("Expect size to be 3", 3, editor.tagsToRemove.get(tagGroup).size());
        assertEquals("Expect tags to match", expectedTags, editor.tagsToRemove.get(tagGroup));

        editor.addTags(tagGroup, expectedTags);
        assertEquals("Expect size to be 0", 0, editor.tagsToRemove.size());
        assertNull("Expect to be null", editor.tagsToRemove.get(tagGroup));
        assertEquals("Expect size to be 1", 1, editor.tagsToAdd.size());
        assertEquals("Expect size to be 3", 3, editor.tagsToAdd.get(tagGroup).size());
        assertEquals("Expect tags to match", expectedTags, editor.tagsToAdd.get(tagGroup));
    }
}
