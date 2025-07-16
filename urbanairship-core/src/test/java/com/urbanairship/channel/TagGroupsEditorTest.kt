/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class TagGroupsEditorTest {

    private val tagGroup = "someTagGroup"
    private var editor = TestTagGroupsEditor()

    /**
     * Test empty tag group are ignored.
     */
    @Test
    public fun testEmptyTagGroup() {
        editor
            .addTag("", "tag1")
            .setTag("", "tag2")
            .removeTag("", "tag3")
            .apply()

        assertTrue(editor.collapsedMutations?.isEmpty() == true)
    }

    /**
     * Test add and removes of tags that normalize to an empty set are ignored.
     */
    @Test
    public fun testAddRemoveEmptyTags() {
        val testTags = setOf("", "  ", "  ")

        editor
            .addTags(tagGroup, testTags)
            .addTags(tagGroup, emptySet())
            .removeTags(tagGroup, testTags)
            .removeTags(tagGroup, emptySet())
            .apply()

        assertTrue(editor.collapsedMutations?.isEmpty() == true)
    }

    /**
     * Test setting null or empty set is allowed for set operations.
     */
    @Test
    public fun testSetEmptyTags() {
        editor.setTags(tagGroup, null).apply()

        assertEquals(1, editor.collapsedMutations?.size)

        val expected = TagGroupsMutation.newSetTagsMutation(tagGroup, emptySet())
        assertEquals(expected, editor.collapsedMutations?.first())
    }

    @Test
    public fun testAllowTagGroupChanges() {
        editor = object : TestTagGroupsEditor() {
            override fun allowTagGroupChange(tagGroup: String): Boolean {
                return tagGroup != "ignore"
            }
        }

        editor
            .addTag("ignore", "hi")
            .removeTag(tagGroup, "cool")
            .apply()

        assertEquals(1, editor.collapsedMutations?.size)

        val expected = TagGroupsMutation.newRemoveTagsMutation(tagGroup, setOf("cool"))
        assertEquals(expected, editor.collapsedMutations?.first())
    }

    private open class TestTagGroupsEditor : TagGroupsEditor() {

        var collapsedMutations: List<TagGroupsMutation>? = null
            private set

        override fun onApply(collapsedMutations: List<TagGroupsMutation>) {
            this.collapsedMutations = collapsedMutations
        }
    }
}
