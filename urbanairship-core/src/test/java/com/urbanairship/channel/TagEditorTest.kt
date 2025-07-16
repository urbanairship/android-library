/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class TagEditorTest {

    // Set by the tag editor onApply
    private var clear: Boolean? = null
    private var tagsToAdd: Set<String>? = null
    private var tagsToRemove: Set<String>? = null

    private var editor = object : TagEditor() {
        override fun onApply(
            clear: Boolean, tagsToAdd: Set<String>, tagsToRemove: Set<String>
        ) {
            this@TagEditorTest.clear = clear
            this@TagEditorTest.tagsToAdd = tagsToAdd
            this@TagEditorTest.tagsToRemove = tagsToRemove
        }
    }

    /**
     * Test add tag
     */
    @Test
    public fun testAddTag() {
        editor.addTag("some_tag").apply()

        assertEquals(1, tagsToAdd?.size)
        assertTrue(tagsToAdd?.contains("some_tag") == true)

        assertEquals(0, tagsToRemove?.size)
        assertFalse(clear == true)
    }

    /**
     * Test add tags
     */
    @Test
    public fun testAddTags() {
        val tagSet = setOf("set_tag", "another_set_tag")

        editor
            .addTag("some_tag")
            .addTags(tagSet)
            .addTag("some_other_tags")
            .apply()

        assertEquals(4, tagsToAdd?.size)
        assertTrue(tagsToAdd?.contains("some_tag") == true)
        assertTrue(tagsToAdd?.contains("some_other_tags") == true)
        assertTrue(tagsToAdd?.contains("set_tag") == true)
        assertTrue(tagsToAdd?.contains("another_set_tag") == true)

        assertEquals(0, tagsToRemove?.size)
        assertFalse(clear == true)
    }

    /**
     * Test remove tag
     */
    @Test
    public fun testRemoveTag() {
        editor.removeTag("some_tag").apply()

        assertEquals(1, tagsToRemove?.size)
        assertTrue(tagsToRemove?.contains("some_tag") == true)

        assertEquals(0, tagsToAdd?.size)
        assertFalse(clear == true)
    }

    /**
     * Test remove tags
     */
    @Test
    public fun testRemoveTags() {
        val tagSet = setOf("set_tag", "another_set_tag")

        editor
            .removeTag("some_tag")
            .removeTags(tagSet)
            .removeTag("some_other_tags")
            .apply()

        assertEquals(4, tagsToRemove?.size)
        assertTrue(tagsToRemove?.contains("some_tag") == true)
        assertTrue(tagsToRemove?.contains("some_other_tags") == true)
        assertTrue(tagsToRemove?.contains("set_tag") == true)
        assertTrue(tagsToRemove?.contains("another_set_tag") == true)

        assertEquals(0, tagsToAdd?.size)
        assertFalse(clear == true)
    }

    /**
     * Test adding and removing the same tag results in the last call winning.
     */
    @Test
    public fun testAddRemoveSameTag() {
        editor
            .removeTag("some_tag")
            .addTag("some_tag")
            .addTag("some_other_tag")
            .removeTag("some_other_tag")
            .apply()

        assertEquals(1, tagsToRemove?.size)
        assertTrue(tagsToRemove?.contains("some_other_tag") == true)

        assertEquals(1, tagsToAdd?.size)
        assertTrue(tagsToAdd?.contains("some_tag") == true)

        assertFalse(clear == true)
    }

    /**
     * Test clear
     */
    @Test
    public fun testClear() {
        editor.clear().apply()

        assertEquals(0, tagsToAdd?.size)
        assertEquals(0, tagsToRemove?.size)

        assertTrue(clear == true)
    }
}
