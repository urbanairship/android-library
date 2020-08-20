/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

public class PendingAttributeMutationStoreTest extends BaseTestCase {

    private PendingAttributeMutationStore store;

    @Before
    public void setup() {
        store = new PendingAttributeMutationStore(TestApplication.getApplication().preferenceDataStore, "test");
    }

    @Test
    public void testAdd() {
        List<AttributeMutation> attributeMutations = new ArrayList<>();
        attributeMutations.add(AttributeMutation.newSetAttributeMutation("expected_key", JsonValue.wrapOpt("expected_value"), 0));
        store.add(attributeMutations);

        assertEquals(attributeMutations, store.getList().get(0));
    }

    @Test
    public void testPop() {
        List<AttributeMutation> mutations = new ArrayList<>();
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", JsonValue.wrapOpt("expected_value"), 100));
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key2", JsonValue.wrapOpt("expected_value2"), 200));

        store.add(mutations);

        assertEquals(JsonValue.wrapOpt(mutations), JsonValue.wrapOpt(store.pop()));
        assertNull(store.pop());
    }

    @Test
    public void testPeek() {
        List<AttributeMutation> mutations = new ArrayList<>();
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", JsonValue.wrapOpt("expected_value"), 100));
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key2", JsonValue.wrapOpt("expected_value2"), 200));

        store.add(mutations);

        assertEquals(mutations, store.peek());
        assertEquals(mutations, store.peek());
    }

    @Test
    public void testClear() {
        List<AttributeMutation> mutations = new ArrayList<>();
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", JsonValue.wrapOpt("expected_value"), 100));
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key2", JsonValue.wrapOpt("expected_value2"), 200));

        store.add(mutations);

        store.removeAll();

        assertTrue(store.getList().isEmpty());
    }

    @Test
    public void testRemoveTwoCollapseAndSave() {
        List<AttributeMutation> mutations = new ArrayList<>();
        mutations.add(AttributeMutation.newRemoveAttributeMutation("expected_key", 0));
        mutations.add(AttributeMutation.newRemoveAttributeMutation("expected_key2", 0));

        store.add(mutations);
        store.collapseAndSaveMutations();

        String expectedResult = "[{\"action\":\"remove\",\"key\":\"expected_key\",\"timestamp\":\"1970-01-01T00:00:00\"},"+
                "{\"action\":\"remove\",\"key\":\"expected_key2\",\"timestamp\":\"1970-01-01T00:00:00\"}]";

        assertEquals(expectedResult, JsonValue.wrapOpt(store.peek()).toString());
    }

    @Test
    public void testAddTwoCollapseAndSave() {
        List<AttributeMutation> mutations = new ArrayList<>();
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", JsonValue.wrapOpt("expected_value"), 0));
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key2", JsonValue.wrapOpt("expected_value2"), 0));
        mutations.add(AttributeMutation.newRemoveAttributeMutation("expected_key2", 0));

        String expectedResult = "[{\"action\":\"set\",\"value\":\"expected_value\",\"key\":\"expected_key\",\"timestamp\":\"1970-01-01T00:00:00\"},"+
                "{\"action\":\"remove\",\"key\":\"expected_key2\",\"timestamp\":\"1970-01-01T00:00:00\"}]";

        store.add(mutations);

        store.collapseAndSaveMutations();

        assertEquals(expectedResult, JsonValue.wrapOpt(store.peek()).toString());
    }

    @Test
    public void testAddTwoRemoveOneCollapseAndSave() {
        List<AttributeMutation> mutations = new ArrayList<>();
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", JsonValue.wrapOpt("expected_value"), 999999999));
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key2", JsonValue.wrapOpt("expected_value2"), 999999999));
        mutations.add(AttributeMutation.newRemoveAttributeMutation("expected_key2", 999999999));

        String expectedResult = "[{\"action\":\"set\",\"value\":\"expected_value\",\"key\":\"expected_key\",\"timestamp\":\"1970-01-12T13:46:39\"},"+
                "{\"action\":\"remove\",\"key\":\"expected_key2\",\"timestamp\":\"1970-01-12T13:46:39\"}]";

        store.add(mutations);

        store.collapseAndSaveMutations();

        assertEquals(expectedResult, JsonValue.wrapOpt(store.peek()).toString());
    }
}
