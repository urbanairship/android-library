/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import com.urbanairship.BaseTestCase;

import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class TagEditorTest extends BaseTestCase {

    private TagEditor editor;

    // Set by the tag editor onApply
    private Boolean clear;
    private Set<String> tagsToAdd;
    private Set<String> tagsToRemove;

    @Before
    public void setup() {
        clear = null;
        tagsToAdd = null;
        tagsToRemove = null;

        editor = new TagEditor() {
            @Override
            void onApply(boolean clear, Set<String> tagsToAdd, Set<String> tagsToRemove) {
                TagEditorTest.this.clear = clear;
                TagEditorTest.this.tagsToAdd = tagsToAdd;
                TagEditorTest.this.tagsToRemove = tagsToRemove;
            }
        };
    }

    /**
     * Test add tag
     */
    @Test
    public void testAddTag() {
        editor.addTag("some_tag")
              .apply();

        assertEquals(1, tagsToAdd.size());
        assertTrue(tagsToAdd.contains("some_tag"));

        assertEquals(0, tagsToRemove.size());
        assertFalse(clear);
    }

    /**
     * Test add tags
     */
    @Test
    public void testAddTags() {
        Set<String> tagSet = new HashSet<>();
        tagSet.add("set_tag");
        tagSet.add("another_set_tag");

        editor.addTag("some_tag")
              .addTags(tagSet)
              .addTag("some_other_tags")
              .apply();

        assertEquals(4, tagsToAdd.size());
        assertTrue(tagsToAdd.contains("some_tag"));
        assertTrue(tagsToAdd.contains("some_other_tags"));
        assertTrue(tagsToAdd.contains("set_tag"));
        assertTrue(tagsToAdd.contains("another_set_tag"));

        assertEquals(0, tagsToRemove.size());
        assertFalse(clear);
    }

    /**
     * Test remove tag
     */
    @Test
    public void testRemoveTag() {
        editor.removeTag("some_tag")
              .apply();

        assertEquals(1, tagsToRemove.size());
        assertTrue(tagsToRemove.contains("some_tag"));

        assertEquals(0, tagsToAdd.size());
        assertFalse(clear);
    }

    /**
     * Test remove tags
     */
    @Test
    public void testRemoveTags() {
        Set<String> tagSet = new HashSet<>();
        tagSet.add("set_tag");
        tagSet.add("another_set_tag");

        editor.removeTag("some_tag")
              .removeTags(tagSet)
              .removeTag("some_other_tags")
              .apply();

        assertEquals(4, tagsToRemove.size());
        assertTrue(tagsToRemove.contains("some_tag"));
        assertTrue(tagsToRemove.contains("some_other_tags"));
        assertTrue(tagsToRemove.contains("set_tag"));
        assertTrue(tagsToRemove.contains("another_set_tag"));

        assertEquals(0, tagsToAdd.size());
        assertFalse(clear);
    }

    /**
     * Test adding and removing the same tag results in the last call winning.
     */
    @Test
    public void testAddRemoveSameTag() {
        editor.removeTag("some_tag")
              .addTag("some_tag")
              .addTag("some_other_tag")
              .removeTag("some_other_tag")
              .apply();


        assertEquals(1, tagsToRemove.size());
        assertTrue(tagsToRemove.contains("some_other_tag"));


        assertEquals(1, tagsToAdd.size());
        assertTrue(tagsToAdd.contains("some_tag"));

        assertFalse(clear);
    }

    /**
     * Test clear
     */
    @Test
    public void testClear() {
        editor.clear()
              .apply();

        assertEquals(0, tagsToAdd.size());
        assertEquals(0, tagsToRemove.size());

        assertTrue(clear);
    }
}