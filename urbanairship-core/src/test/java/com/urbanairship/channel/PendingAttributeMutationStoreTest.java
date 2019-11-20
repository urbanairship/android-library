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
        attributeMutations.add(AttributeMutation.newSetAttributeMutation("expected_key", "expected_value"));

        List<PendingAttributeMutation> expectedMutations = PendingAttributeMutation.fromAttributeMutations(attributeMutations, 0);

        store.add(expectedMutations);

        assertEquals(expectedMutations, store.getMutations().get(0));
    }

    @Test
    public void testPop() {
        List<AttributeMutation> mutations = new ArrayList<>();
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", "expected_value"));
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key2", "expected_value2"));

        List<PendingAttributeMutation> expectedMutations = PendingAttributeMutation.fromAttributeMutations(mutations, 0);

        store.add(expectedMutations);

        assertEquals(JsonValue.wrapOpt(expectedMutations), JsonValue.wrapOpt(store.pop()));
        assertNull(store.pop());
    }

    @Test
    public void testPeek() {
        List<AttributeMutation> mutations = new ArrayList<>();
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", "expected_value"));
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key2", "expected_value2"));

        List<PendingAttributeMutation> expectedMutations = PendingAttributeMutation.fromAttributeMutations(mutations, 0);

        store.add(expectedMutations);

        assertEquals(expectedMutations, store.peek());
        assertEquals(expectedMutations, store.peek());
    }

    @Test
    public void testClear() {
        List<AttributeMutation> mutations = new ArrayList<>();
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", "expected_value"));
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key2", "expected_value2"));

        List<PendingAttributeMutation> expectedMutations = PendingAttributeMutation.fromAttributeMutations(mutations, 0);
        store.add(expectedMutations);

        store.clear();

        assertTrue(store.getMutations().isEmpty());
    }

    @Test
    public void testRemoveTwoCollapseAndSave() {
        List<AttributeMutation> mutations = new ArrayList<>();
        mutations.add(AttributeMutation.newRemoveAttributeMutation("expected_key"));
        mutations.add(AttributeMutation.newRemoveAttributeMutation("expected_key2"));
        List<PendingAttributeMutation> expectedMutations = PendingAttributeMutation.fromAttributeMutations(mutations, 0);

        store.add(expectedMutations);
        store.collapseAndSaveMutations();

        String expectedResult = "[{\"action\":\"remove\",\"key\":\"expected_key\",\"timestamp\":\"1970-01-01T00:00:00\"},"+
                "{\"action\":\"remove\",\"key\":\"expected_key2\",\"timestamp\":\"1970-01-01T00:00:00\"}]";

        assertEquals(expectedResult, JsonValue.wrapOpt(store.peek()).toString());
    }

    @Test
    public void testAddTwoCollapseAndSave() {
        List<AttributeMutation> mutations = new ArrayList<>();
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", "expected_value"));
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key2", "expected_value2"));
        mutations.add(AttributeMutation.newRemoveAttributeMutation("expected_key2"));

        List<PendingAttributeMutation> expectedMutations = PendingAttributeMutation.fromAttributeMutations(mutations, 0);

        String expectedResult = "[{\"action\":\"set\",\"value\":\"expected_value\",\"key\":\"expected_key\",\"timestamp\":\"1970-01-01T00:00:00\"},"+
                "{\"action\":\"remove\",\"key\":\"expected_key2\",\"timestamp\":\"1970-01-01T00:00:00\"}]";

        store.add(expectedMutations);

        store.collapseAndSaveMutations();

        assertEquals(expectedResult, JsonValue.wrapOpt(store.peek()).toString());
    }

    @Test
    public void testAddTwoRemoveOneCollapseAndSave() {
        List<AttributeMutation> mutations = new ArrayList<>();
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", "expected_value"));
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key2", "expected_value2"));
        mutations.add(AttributeMutation.newRemoveAttributeMutation("expected_key2"));

        List<PendingAttributeMutation> expectedMutations = PendingAttributeMutation.fromAttributeMutations(mutations, 999999999);

        String expectedResult = "[{\"action\":\"set\",\"value\":\"expected_value\",\"key\":\"expected_key\",\"timestamp\":\"1970-01-12T13:46:39\"},"+
                "{\"action\":\"remove\",\"key\":\"expected_key2\",\"timestamp\":\"1970-01-12T13:46:39\"}]";

        store.add(expectedMutations);

        store.collapseAndSaveMutations();

        assertEquals(expectedResult, JsonValue.wrapOpt(store.peek()).toString());
    }
}