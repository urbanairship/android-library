/* Copyright Airship and Contributors */

package com.urbanairship.channel;

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

public class PendingTagGroupMutationStoreTest extends BaseTestCase {

    PendingTagGroupMutationStore store;

    @Before
    public void setup() {
        store = new PendingTagGroupMutationStore(TestApplication.getApplication().preferenceDataStore, "test");
    }

    @Test
    public void testAdd() {
        List<TagGroupsMutation> mutations = new ArrayList<>();
        mutations.add(TagGroupsMutation.newAddTagsMutation("group-one", createTagSet("cool")));
        mutations.add(TagGroupsMutation.newRemoveTagsMutation("group-two", createTagSet("story")));
        mutations.add(TagGroupsMutation.newSetTagsMutation("group-one", createTagSet("whatever")));

        store.addAll(mutations);

        assertEquals(mutations, store.getList());
    }

    @Test
    public void testPop() {
        List<TagGroupsMutation> mutations = new ArrayList<>();
        mutations.add(TagGroupsMutation.newAddTagsMutation("group-one", createTagSet("cool")));
        mutations.add(TagGroupsMutation.newSetTagsMutation("group-two", createTagSet("whatever")));

        store.addAll(mutations);

        assertEquals(mutations.get(0), store.pop());
        assertEquals(mutations.get(1), store.pop());
        assertNull(store.pop());
    }

    @Test
    public void testPeek() {
        List<TagGroupsMutation> mutations = new ArrayList<>();
        mutations.add(TagGroupsMutation.newAddTagsMutation("group-one", createTagSet("cool")));
        mutations.add(TagGroupsMutation.newSetTagsMutation("group-two", createTagSet("whatever")));

        store.addAll(mutations);

        assertEquals(mutations.get(0), store.peek());
        assertEquals(mutations.get(0), store.peek());
    }

    @Test
    public void testClear() {
        List<TagGroupsMutation> mutations = new ArrayList<>();
        mutations.add(TagGroupsMutation.newAddTagsMutation("group-one", createTagSet("cool")));
        mutations.add(TagGroupsMutation.newSetTagsMutation("group-two", createTagSet("whatever")));
        store.addAll(mutations);

        store.removeAll();

        assertTrue(store.getList().isEmpty());
    }

    @Test
    public void testCollapse() {
        List<TagGroupsMutation> mutations = new ArrayList<>();
        mutations.add(TagGroupsMutation.newAddTagsMutation("group-one", createTagSet("cool")));
        mutations.add(TagGroupsMutation.newAddTagsMutation("group-two", createTagSet("whatever")));
        store.addAll(mutations);

        store.collapseAndSaveMutations();

        List<TagGroupsMutation> collapseMutations = TagGroupsMutation.collapseMutations(mutations);
        assertEquals(collapseMutations.get(0), store.peek());
    }

    private Set<String> createTagSet(String... tags) {
        return new HashSet<>(Arrays.asList(tags));
    }

}
