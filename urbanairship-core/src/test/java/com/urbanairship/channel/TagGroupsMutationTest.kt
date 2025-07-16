/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import java.util.Arrays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class TagGroupsMutationTest {

    @Test
    public fun testAddTagsMutation() {
        val mutation = TagGroupsMutation.newAddTagsMutation("group", setOf("tag1", "tag2", "tag2"))

        val expected = """
            {
              "add": {
                "group": [
                  "tag1",
                  "tag2"
                ]
              }
            }
        """.trimIndent()
        assertEquals(JsonValue.parseString(expected), mutation.toJsonValue())
    }

    @Test
    public fun testSetTagsMutation() {
        val mutation = TagGroupsMutation.newSetTagsMutation("group", setOf("tag2", "tag2", "tag1"))

        val expected = """
            {
              "set": {
                "group": [
                  "tag2",
                  "tag1"
                ]
              }
            }
        """.trimIndent()
        assertEquals(JsonValue.parseString(expected), mutation.toJsonValue())
    }

    @Test
    public fun testRemoveTagsMutation() {
        val mutation =
            TagGroupsMutation.newRemoveTagsMutation("group", setOf("tag2", "tag2", "tag1"))

        val expected = """
            {
              "remove": {
                "group": [
                  "tag2",
                  "tag1"
                ]
              }
            }
        """.trimIndent()
        assertEquals(JsonValue.parseString(expected), mutation.toJsonValue())
    }

    @Test
    public fun testCollapseMutationSameGroup() {
        val add = TagGroupsMutation.newAddTagsMutation("group", setOf("tag1", "tag2"))
        val remove = TagGroupsMutation.newRemoveTagsMutation("group", setOf("tag1"))
        val set = TagGroupsMutation.newSetTagsMutation("group", setOf("tag3"))

        // Collapse [add, remove] should result in a single mutation to add [tag2] remove [tag1]
        var mutations = listOf(add, remove)

        var collapsed = TagGroupsMutation.collapseMutations(mutations)
        assertEquals(1, collapsed.size)

        var expected = "{ \"add\": { \"group\": [\"tag2\"] }, \"remove\": { \"group\": [\"tag1\"] } }"
        assertEquals(JsonValue.parseString(expected), collapsed[0].toJsonValue())

        // Collapse [remove, add] order and it should result in add [tag1, tag2]
        mutations = listOf(remove, add)

        collapsed = TagGroupsMutation.collapseMutations(mutations)
        assertEquals(1, collapsed.size)

        expected = "{ \"add\": { \"group\": [\"tag1\", \"tag2\"] } }"
        assertEquals(JsonValue.parseString(expected), collapsed.first().toJsonValue())

        // Collapse [set, add, remove] should result in a single mutation to set [tag2, tag3]
        mutations = listOf(set, add, remove)

        collapsed = TagGroupsMutation.collapseMutations(mutations)
        assertEquals(1, collapsed.size)

        expected = "{ \"set\": { \"group\": [\"tag3\", \"tag2\"] } }"
        assertEquals(JsonValue.parseString(expected), collapsed.first().toJsonValue())

        // Collapse [add, set, remove] should result in single mutation to set [tag3]
        mutations = listOf(add, set, remove)

        collapsed = TagGroupsMutation.collapseMutations(mutations)
        assertEquals(1, collapsed.size)

        expected = "{ \"set\": { \"group\": [\"tag3\"] } }"
        assertEquals(JsonValue.parseString(expected), collapsed.first().toJsonValue())

        // Collapse [set, remove, add] should result in single mutation to set [tag3, tag1, tag2]
        mutations = listOf(set, remove, add)

        collapsed = TagGroupsMutation.collapseMutations(mutations)
        assertEquals(1, collapsed.size)

        expected = "{ \"set\": { \"group\": [\"tag3\", \"tag1\", \"tag2\"] } }"
        assertEquals(JsonValue.parseString(expected), collapsed.first().toJsonValue())

        // Collapse multiple adds should result in a single add [tag1, tag2]
        mutations = listOf(add, add, add, add)

        collapsed = TagGroupsMutation.collapseMutations(mutations)
        assertEquals(1, collapsed.size)

        expected = "{ \"add\": { \"group\": [\"tag1\", \"tag2\"] } }"
        assertEquals(JsonValue.parseString(expected), collapsed.first().toJsonValue())
    }

    @Test
    public fun testCollapseMultipleGroups() {
        val addGroup1 = TagGroupsMutation.newAddTagsMutation("group1", setOf("tag1", "tag2"))
        val removeGroup2 = TagGroupsMutation.newRemoveTagsMutation("group2", setOf("tag1"))
        val setGroup3 = TagGroupsMutation.newSetTagsMutation("group3", setOf("tag3"))
        val setGroup1 = TagGroupsMutation.newSetTagsMutation("group1", setOf("tag4"))

        // Collapse [addGroup1, removeGroup2, setGroup3] should result in 2 mutations
        val mutations = mutableListOf(addGroup1, removeGroup2, setGroup3)

        var collapsed = TagGroupsMutation.collapseMutations(mutations)
        assertEquals(2, collapsed.size)

        var expected = "{ \"set\": { \"group3\": [\"tag3\"] } }"
        assertEquals(JsonValue.parseString(expected), collapsed[0].toJsonValue())

        // Collapse result with setGroup1 should result in 2 mutations
        mutations.add(setGroup1)

        collapsed = TagGroupsMutation.collapseMutations(mutations)
        assertEquals(2, collapsed.size)

        expected = "{ \"set\": { \"group3\": [\"tag3\"], \"group1\": [\"tag4\"] } }"
        assertEquals(JsonValue.parseString(expected), collapsed[0].toJsonValue())

        expected = "{ \"remove\": { \"group2\": [\"tag1\"] } }"
        assertEquals(JsonValue.parseString(expected), collapsed[1].toJsonValue())
    }

    @Test
    public fun testCollapseEmptyMutations() {
        val collapsed = TagGroupsMutation.collapseMutations(listOf())
        assertEquals(0, collapsed.size)
    }

    @Test
    public fun testCollapseResultsNoMutations() {
        val set = TagGroupsMutation.newSetTagsMutation("group", setOf("tag1"))
        val remove = TagGroupsMutation.newRemoveTagsMutation("group", setOf("tag1"))

        // Collapse [set, remove] should result in no mutations
        val mutations = listOf(set, remove)

        val collapsed = TagGroupsMutation.collapseMutations(mutations)
        assertEquals(1, collapsed.size)

        val expected = "{ \"set\": { \"group\": [] } }"
        assertEquals(JsonValue.parseString(expected), collapsed[0].toJsonValue())
    }

    @Test
    public fun testCollapseSetTags() {
        val set1 = TagGroupsMutation.newSetTagsMutation("group", setOf("tag1"))
        val set2 = TagGroupsMutation.newSetTagsMutation("group", setOf("tag2"))

        // Collapse [set1, set2] should result in only set2
        val mutations = listOf(set1, set2)

        val collapsed = TagGroupsMutation.collapseMutations(mutations)
        assertEquals(1, collapsed.size)

        val expected = "{ \"set\": { \"group\": [\"tag2\"] } }"
        assertEquals(JsonValue.parseString(expected), collapsed[0].toJsonValue())
    }

    @Test
    public fun testEmptyMutation() {
        val mutation = TagGroupsMutation.newAddTagsMutation("empty", emptySet())
        val fromJson = TagGroupsMutation.fromJsonValue(mutation.toJsonValue())
        assertEquals(mutation, fromJson)
    }

    @Test
    public fun testCollapseEmptyMutation() {
        val mutation = TagGroupsMutation.newAddTagsMutation("empty", emptySet())
        val collapsed = TagGroupsMutation.collapseMutations(Arrays.asList(mutation))
        assertTrue(collapsed.isEmpty())
    }

    @Test
    public fun testEmptySetMutation() {
        val mutation = TagGroupsMutation.newSetTagsMutation("empty", emptySet())
        val fromJson = TagGroupsMutation.fromJsonValue(mutation.toJsonValue())
        assertEquals(mutation.toJsonValue(), fromJson.toJsonValue())
    }

    @Test
    public fun testCollapseEmptySetMutation() {
        val mutation = TagGroupsMutation.newSetTagsMutation("empty", emptySet())
        val collapsed = TagGroupsMutation.collapseMutations(Arrays.asList(mutation))
        assertFalse(collapsed.isEmpty())
    }

    @Test
    public fun testEquals() {
        val mutation = TagGroupsMutation.newAddTagsMutation(
            "group", setOf("tag1", "tag2")
        )
        val sameMutation = TagGroupsMutation.newAddTagsMutation(
            "group", setOf("tag1", "tag2")
        )
        val differentMutation = TagGroupsMutation.newSetTagsMutation("group", setOf("tag3"))

        assertEquals(mutation, sameMutation)
        assertNotSame(mutation, differentMutation)
        assertEquals(mutation, TagGroupsMutation.fromJsonValue(mutation.toJsonValue()))
    }
}
