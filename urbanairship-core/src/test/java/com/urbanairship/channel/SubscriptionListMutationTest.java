package com.urbanairship.channel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class SubscriptionListMutationTest extends BaseTestCase {

    private static final String EPOCH = DateUtils.createIso8601TimeStamp(0L);

    @Test
    public void testSubscribeMutation() throws JsonException {
        SubscriptionListMutation mutation = SubscriptionListMutation.newSubscribeMutation("listId", 0L);

        String expected = "{ \"action\": \"subscribe\", \"list_id\": \"listId\", \"timestamp\": \"" + EPOCH  + "\" }";
        assertEquals(JsonValue.parseString(expected), mutation.toJsonValue());
    }

    @Test
    public void testSubscribeMutationNullTimestamp() throws JsonException {
        SubscriptionListMutation mutation = new SubscriptionListMutation("subscribe", "listId", null);

        String expected = "{ \"action\": \"subscribe\", \"list_id\": \"listId\" }";
        assertEquals(JsonValue.parseString(expected), mutation.toJsonValue());
    }

    @Test
    public void testUnsubscribeMutation() throws JsonException {
        SubscriptionListMutation mutation = SubscriptionListMutation.newUnsubscribeMutation("listId", 0L);

        String expected = "{ \"action\": \"unsubscribe\", \"list_id\": \"listId\", \"timestamp\": \"" + EPOCH  + "\" }";
        assertEquals(JsonValue.parseString(expected), mutation.toJsonValue());
    }

    @Test
    public void testUnsubscribeMutationNullTimestamp() throws JsonException {
        SubscriptionListMutation mutation = new SubscriptionListMutation("unsubscribe", "listId", null);

        String expected = "{ \"action\": \"unsubscribe\", \"list_id\": \"listId\" }";
        assertEquals(JsonValue.parseString(expected), mutation.toJsonValue());
    }

    @Test
    public void testCollapseDuplicateMutations() {
        List<SubscriptionListMutation> mutations = Arrays.asList(
                SubscriptionListMutation.newSubscribeMutation("foo", 10L),
                SubscriptionListMutation.newSubscribeMutation("foo", 20L),
                SubscriptionListMutation.newSubscribeMutation("foo", 30L),
                SubscriptionListMutation.newSubscribeMutation("foo", 40L)
        );

        List<SubscriptionListMutation> collapsed = SubscriptionListMutation.collapseMutations(mutations);
        assertEquals(1, collapsed.size());

        SubscriptionListMutation first = collapsed.get(0);
        JsonValue expected = JsonMap.newBuilder()
                .put("action", "subscribe")
                .put("list_id", "foo")
                .put("timestamp", DateUtils.createIso8601TimeStamp(40L))
                .build()
                .toJsonValue();
        assertEquals(expected, first.toJsonValue());
    }

    @Test
    public void testCollapseDifferentMutationsWithSameListId() {
        List<SubscriptionListMutation> mutations = Arrays.asList(
                SubscriptionListMutation.newSubscribeMutation("foo", 10L),
                SubscriptionListMutation.newUnsubscribeMutation("foo", 20L),
                SubscriptionListMutation.newSubscribeMutation("foo", 30L),
                SubscriptionListMutation.newUnsubscribeMutation("foo", 40L)
        );

        List<SubscriptionListMutation> collapsed = SubscriptionListMutation.collapseMutations(mutations);
        assertEquals(1, collapsed.size());

        SubscriptionListMutation first = collapsed.get(0);
        JsonValue expected = JsonMap.newBuilder()
                                    .put("action", "unsubscribe")
                                    .put("list_id", "foo")
                                    .put("timestamp", DateUtils.createIso8601TimeStamp(40L))
                                    .build()
                                    .toJsonValue();
        assertEquals(expected, first.toJsonValue());
    }

    @Test
    public void testToFromJsonValue() throws JsonException {
        SubscriptionListMutation mutation = SubscriptionListMutation.newSubscribeMutation("bar", 0L);

        assertEquals(mutation, SubscriptionListMutation.fromJsonValue(mutation.toJsonValue()));

        assertThrows(JsonException.class, new ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                SubscriptionListMutation.fromJsonValue(JsonMap.EMPTY_MAP.toJsonValue());
            }
        });
    }

    @Test
    public void testEqualsAndHashCode() {
        SubscriptionListMutation mutation = SubscriptionListMutation.newSubscribeMutation("same", 0L);
        SubscriptionListMutation sameMutation = SubscriptionListMutation.newSubscribeMutation("same", 0L);
        SubscriptionListMutation differentMutation = SubscriptionListMutation.newUnsubscribeMutation("same", 0L);
        SubscriptionListMutation differentListId = SubscriptionListMutation.newSubscribeMutation("different", 0L);

        assertEquals(mutation, sameMutation);
        assertNotEquals(mutation, differentMutation);
        assertNotEquals(mutation, differentListId);

        assertEquals(mutation.hashCode(), sameMutation.hashCode());
        assertNotEquals(mutation.hashCode(), differentMutation.hashCode());
        assertNotEquals(mutation.hashCode(), differentListId.hashCode());
    }
}
