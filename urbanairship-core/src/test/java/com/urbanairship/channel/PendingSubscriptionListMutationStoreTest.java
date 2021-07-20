package com.urbanairship.channel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PendingSubscriptionListMutationStoreTest extends BaseTestCase {

    private PendingSubscriptionListMutationStore store;

    @Before
    public void setup() {
        store = new PendingSubscriptionListMutationStore(TestApplication.getApplication().preferenceDataStore, "test");
    }

    @Test
    public void testAdd() {
        List<SubscriptionListMutation> mutations = new ArrayList<>();
        mutations.add(SubscriptionListMutation.newSubscribeMutation("foo", 0L));

        store.add(mutations);

        assertEquals(mutations, store.getList().get(0));
    }

    @Test
    public void testPop() {
        List<SubscriptionListMutation> mutations = new ArrayList<>();
        mutations.add(SubscriptionListMutation.newSubscribeMutation("foo", 0L));
        mutations.add(SubscriptionListMutation.newUnsubscribeMutation("bar", 0L));

        store.add(mutations);

        assertEquals(mutations, store.pop());
        assertNull(store.pop());
    }

    @Test
    public void testPeek() {
        List<SubscriptionListMutation> mutations = new ArrayList<>();
        mutations.add(SubscriptionListMutation.newSubscribeMutation("foo", 0L));
        mutations.add(SubscriptionListMutation.newUnsubscribeMutation("bar", 0L));

        store.add(mutations);

        assertEquals(mutations, store.peek());
        assertEquals(mutations, store.peek());
    }

    @Test
    public void testClear() {
        List<SubscriptionListMutation> mutations = new ArrayList<>();
        mutations.add(SubscriptionListMutation.newSubscribeMutation("foo", 0L));
        mutations.add(SubscriptionListMutation.newUnsubscribeMutation("bar", 0L));

        store.add(mutations);
        store.removeAll();

        assertTrue(store.getList().isEmpty());
    }

    @Test
    public void testSubscribeUnsubscribeSameListIdCollapseAndSave() {
        List<SubscriptionListMutation> mutations = new ArrayList<>();
        mutations.add(SubscriptionListMutation.newSubscribeMutation("foo", 0L));
        mutations.add(SubscriptionListMutation.newSubscribeMutation("bar", 0L));
        mutations.add(SubscriptionListMutation.newUnsubscribeMutation("bar", 0L));

        store.add(mutations);
        store.collapseAndSaveMutations();

        List<SubscriptionListMutation> expected = new ArrayList<>();
        expected.add(SubscriptionListMutation.newSubscribeMutation("foo", 0L));
        expected.add(SubscriptionListMutation.newUnsubscribeMutation("bar", 0L));

        assertEquals(expected, store.peek());
    }

    @Test
    public void testDuplicateSubscribeSameListIdCollapseAndSave() {
        List<SubscriptionListMutation> mutations = new ArrayList<>();
        mutations.add(SubscriptionListMutation.newSubscribeMutation("foo", 0L));
        mutations.add(SubscriptionListMutation.newSubscribeMutation("foo", 10L));
        mutations.add(SubscriptionListMutation.newSubscribeMutation("foo", 20L));

        store.add(mutations);
        store.collapseAndSaveMutations();

        List<SubscriptionListMutation> expected = new ArrayList<>();
        expected.add(SubscriptionListMutation.newSubscribeMutation("foo", 20L));

        assertEquals(expected, store.peek());
    }
}
