/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static junit.framework.Assert.assertEquals;


public class TagGroupsMutationTest extends BaseTestCase {


    @Test
    public void testAddTagsMutation() throws JsonException {
        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("group", tagSet("tag2", "tag2", "tag1"));

        String expected = "{ \"add\": { \"group\": [\"tag1\", \"tag2\"] } }";
        assertEquals(JsonValue.parseString(expected), mutation.toJsonValue());
    }

    @Test
    public void testSetTagsMutation() throws JsonException {
        TagGroupsMutation mutation = TagGroupsMutation.newSetTagsMutation("group", tagSet("tag2", "tag2", "tag1"));

        String expected = "{ \"set\": { \"group\": [\"tag1\", \"tag2\"] } }";
        assertEquals(JsonValue.parseString(expected), mutation.toJsonValue());
    }

    @Test
    public void testRemoveTagsMutation() throws JsonException {
        TagGroupsMutation mutation = TagGroupsMutation.newRemoveTagsMutation("group", tagSet("tag2", "tag2", "tag1"));

        String expected = "{ \"remove\": { \"group\": [\"tag1\", \"tag2\"] } }";
        assertEquals(JsonValue.parseString(expected), mutation.toJsonValue());
    }

    @Test
    public void testCollapseMutationSameGroup() throws JsonException {
        final TagGroupsMutation add = TagGroupsMutation.newAddTagsMutation("group", tagSet("tag1", "tag2"));
        final TagGroupsMutation remove = TagGroupsMutation.newRemoveTagsMutation("group", tagSet("tag1"));
        final TagGroupsMutation set = TagGroupsMutation.newSetTagsMutation("group", tagSet("tag3"));

        // Collapse [add, remove] should result in a single mutation to add [tag2] remove [tag1]
        List<TagGroupsMutation> mutations = new ArrayList<>();
        mutations.add(add);
        mutations.add(remove);

        List<TagGroupsMutation> collapsed = TagGroupsMutation.collapseMutations(mutations);
        assertEquals(1, collapsed.size());

        String expected = "{ \"add\": { \"group\": [\"tag2\"] }, \"remove\": { \"group\": [\"tag1\"] } }";
        assertEquals(JsonValue.parseString(expected), collapsed.get(0).toJsonValue());

        // Collapse [remove, add] order and it should result in add [tag1, tag2]
        mutations.clear();
        mutations.add(remove);
        mutations.add(add);

        collapsed = TagGroupsMutation.collapseMutations(mutations);
        assertEquals(1, collapsed.size());

        expected  = "{ \"add\": { \"group\": [\"tag1\", \"tag2\"] } }";
        assertEquals(JsonValue.parseString(expected), collapsed.get(0).toJsonValue());

        // Collapse [set, add, remove] should result in a single mutation to set [tag2, tag3]
        mutations.clear();
        mutations.add(set);
        mutations.add(add);
        mutations.add(remove);

        collapsed = TagGroupsMutation.collapseMutations(mutations);
        assertEquals(1, collapsed.size());

        expected  = "{ \"set\": { \"group\": [\"tag2\", \"tag3\"] } }";
        assertEquals(JsonValue.parseString(expected), collapsed.get(0).toJsonValue());

        // Collapse [add, set, remove] should result in single mutation to set [tag3]
        mutations.clear();
        mutations.add(add);
        mutations.add(set);
        mutations.add(remove);

        collapsed = TagGroupsMutation.collapseMutations(mutations);
        assertEquals(1, collapsed.size());

        expected  = "{ \"set\": { \"group\": [\"tag3\"] } }";
        assertEquals(JsonValue.parseString(expected), collapsed.get(0).toJsonValue());

        // Collapse [set, remove, add] should result in single mutation to set [tag3, tag1, tag2]
        mutations.clear();
        mutations.add(set);
        mutations.add(remove);
        mutations.add(add);

        collapsed = TagGroupsMutation.collapseMutations(mutations);
        assertEquals(1, collapsed.size());

        expected  = "{ \"set\": { \"group\": [\"tag1\", \"tag2\", \"tag3\"] } }";
        assertEquals(JsonValue.parseString(expected), collapsed.get(0).toJsonValue());

        // Collapse multiple adds should result in a single add [tag1, tag2]
        mutations.clear();
        mutations.add(add);
        mutations.add(add);
        mutations.add(add);
        mutations.add(add);

        collapsed = TagGroupsMutation.collapseMutations(mutations);
        assertEquals(1, collapsed.size());

        expected  = "{ \"add\": { \"group\": [\"tag1\", \"tag2\"] } }";
        assertEquals(JsonValue.parseString(expected), collapsed.get(0).toJsonValue());
    }

    @Test
    public void testCollapseMultipleGroups() throws JsonException {
        final TagGroupsMutation addGroup1 = TagGroupsMutation.newAddTagsMutation("group1", tagSet("tag1", "tag2"));
        final TagGroupsMutation removeGroup2 = TagGroupsMutation.newRemoveTagsMutation("group2", tagSet("tag1"));
        final TagGroupsMutation setGroup3 = TagGroupsMutation.newSetTagsMutation("group3", tagSet("tag3"));
        final TagGroupsMutation setGroup1 = TagGroupsMutation.newSetTagsMutation("group1", tagSet("tag4"));

        // Collapse [addGroup1, removeGroup2, setGroup3] should result in 2 mutations
        List<TagGroupsMutation> mutations = new ArrayList<>();
        mutations.add(addGroup1);
        mutations.add(removeGroup2);
        mutations.add(setGroup3);

        List<TagGroupsMutation> collapsed = TagGroupsMutation.collapseMutations(mutations);
        assertEquals(2, collapsed.size());

        String expected  = "{ \"set\": { \"group3\": [\"tag3\"] } }";
        assertEquals(JsonValue.parseString(expected), collapsed.get(0).toJsonValue());

        // Collapse result with setGroup1 should result in 2 mutations
        mutations.add(setGroup1);

        collapsed = TagGroupsMutation.collapseMutations(mutations);
        assertEquals(2, collapsed.size());

        expected  = "{ \"set\": { \"group3\": [\"tag3\"], \"group1\": [\"tag4\"] } }";
        assertEquals(JsonValue.parseString(expected), collapsed.get(0).toJsonValue());

        expected  = "{ \"remove\": { \"group2\": [\"tag1\"] } }";
        assertEquals(JsonValue.parseString(expected), collapsed.get(1).toJsonValue());
    }

    @Test
    public void testCollapseEmptyMutations() throws JsonException {
        List<TagGroupsMutation> mutations = new ArrayList<>();
        List<TagGroupsMutation> collapsed = TagGroupsMutation.collapseMutations(mutations);
        assertEquals(0, collapsed.size());
    }

    @Test
    public void testCollapseResultsNoMutations() throws JsonException {
        TagGroupsMutation set = TagGroupsMutation.newSetTagsMutation("group", tagSet("tag1"));
        TagGroupsMutation remove = TagGroupsMutation.newRemoveTagsMutation("group", tagSet("tag1"));

        // Collapse [set, remove] should result in no mutations
        List<TagGroupsMutation> mutations = new ArrayList<>();
        mutations.add(set);
        mutations.add(remove);

        List<TagGroupsMutation> collapsed = TagGroupsMutation.collapseMutations(mutations);
        assertEquals(1, collapsed.size());

        String expected  = "{ \"set\": { \"group\": [] } }";
        assertEquals(JsonValue.parseString(expected), collapsed.get(0).toJsonValue());
    }

    @Test
    public void testCollapseSetTags() throws JsonException {
        TagGroupsMutation set1 = TagGroupsMutation.newSetTagsMutation("group", tagSet("tag1"));
        TagGroupsMutation set2 = TagGroupsMutation.newSetTagsMutation("group", tagSet("tag2"));

        // Collapse [set1, set2] should result in only set2
        List<TagGroupsMutation> mutations = new ArrayList<>();
        mutations.add(set1);
        mutations.add(set2);

        List<TagGroupsMutation> collapsed = TagGroupsMutation.collapseMutations(mutations);
        assertEquals(1, collapsed.size());

        String expected  = "{ \"set\": { \"group\": [\"tag2\"] } }";
        assertEquals(JsonValue.parseString(expected), collapsed.get(0).toJsonValue());
    }

    /**
     * Helper method to create a set.
     * @param tags The tags.
     * @return The set of tags.
     */
    private static Set<String> tagSet(String... tags) {
        return new HashSet<>(Arrays.asList(tags));
    }

}
