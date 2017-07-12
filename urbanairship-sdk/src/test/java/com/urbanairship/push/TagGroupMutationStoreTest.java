/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;


public class TagGroupMutationStoreTest extends BaseTestCase {

    TagGroupMutationStore store;

    @Before
    public void setup() {
        store = new TagGroupMutationStore(TestApplication.getApplication().preferenceDataStore, "test");
    }

    @Test
    public void testAdd() {
        List<TagGroupsMutation> mutations = new ArrayList<>();
        mutations.add(TagGroupsMutation.newAddTagsMutation("group-one", createTagSet("cool")));
        mutations.add(TagGroupsMutation.newRemoveTagsMutation("group-two", createTagSet("story")));
        mutations.add(TagGroupsMutation.newSetTagsMutation("group-one", createTagSet("whatever")));

        store.add(mutations);

        // Verify the collapsed mutations are available
        assertEquals(TagGroupsMutation.collapseMutations(mutations), store.getMutations());
    }

    @Test
    public void testPop() {
        List<TagGroupsMutation> mutations = new ArrayList<>();

        mutations.add(TagGroupsMutation.newAddTagsMutation("group-one", createTagSet("cool")));
        mutations.add(TagGroupsMutation.newSetTagsMutation("group-two", createTagSet("whatever")));
        mutations = TagGroupsMutation.collapseMutations(mutations);

        store.add(mutations);

        assertEquals(mutations.get(0), store.pop());
        assertEquals(mutations.get(1), store.pop());
        assertNull(store.pop());
    }

    @Test
    public void testPush() {
        List<TagGroupsMutation> mutations = new ArrayList<>();
        mutations.add(TagGroupsMutation.newAddTagsMutation("group-one", createTagSet("cool")));
        mutations.add(TagGroupsMutation.newSetTagsMutation("group-two", createTagSet("whatever")));
        mutations = TagGroupsMutation.collapseMutations(mutations);

        store.add(mutations);

        TagGroupsMutation mutation = store.pop();

        assertEquals(mutations.get(0), mutation);

        store.push(mutation);

        assertEquals(mutation, store.pop());
    }

    @Test
    public void testClear() {
        List<TagGroupsMutation> mutations = new ArrayList<>();
        mutations.add(TagGroupsMutation.newAddTagsMutation("group-one", createTagSet("cool")));
        mutations.add(TagGroupsMutation.newSetTagsMutation("group-two", createTagSet("whatever")));
        store.add(mutations);

        store.clear();

        assertTrue(store.getMutations().isEmpty());
    }

    private Set<String> createTagSet(String... tags) {
        return new HashSet<>(Arrays.asList(tags));
    }
}