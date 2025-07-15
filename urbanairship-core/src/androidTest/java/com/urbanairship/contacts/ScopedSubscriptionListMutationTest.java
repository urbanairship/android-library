/* Copyright Airship and Contributors */

package com.urbanairship.contacts;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import junit.framework.TestCase;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ScopedSubscriptionListMutationTest extends TestCase {

    private static final String EPOCH = DateUtils.createIso8601TimeStamp(0L);

    @Test
    public void testFromJson() throws JsonException {
        String jsonString = "{ \"scope\": \"app\", \"action\": \"subscribe\", \"list_id\": \"listId\", \"timestamp\": \"" + EPOCH  + "\" }";
        ScopedSubscriptionListMutation mutation = ScopedSubscriptionListMutation.fromJsonValue(JsonValue.parseString(jsonString));

        assertEquals(Scope.APP, mutation.scope);
        assertEquals(ScopedSubscriptionListMutation.ACTION_SUBSCRIBE, mutation.action);
        assertEquals(EPOCH, mutation.timestamp);
        assertEquals("listId", mutation.listId);
    }

    @Test
    public void testToJsonSubscribe() throws JsonException {
        ScopedSubscriptionListMutation mutation = ScopedSubscriptionListMutation.newSubscribeMutation("someList", Scope.SMS, 0);

        String expected = "{ \"scope\": \"sms\", \"action\": \"subscribe\", \"list_id\": \"someList\", \"timestamp\": \"" + EPOCH  + "\" }";
        assertEquals(JsonValue.parseString(expected), mutation.toJsonValue());
    }

    @Test
    public void testToJsonUnsubscribe() throws JsonException {
        ScopedSubscriptionListMutation mutation = ScopedSubscriptionListMutation.newUnsubscribeMutation("someList", Scope.SMS, 0);

        String expected = "{ \"scope\": \"sms\", \"action\": \"unsubscribe\", \"list_id\": \"someList\", \"timestamp\": \"" + EPOCH  + "\" }";
        assertEquals(JsonValue.parseString(expected), mutation.toJsonValue());
    }

    @Test
    public void testCollapse() {
        List<ScopedSubscriptionListMutation> mutations = Arrays.asList(
                ScopedSubscriptionListMutation.newSubscribeMutation("foo", Scope.SMS, 10L),
                ScopedSubscriptionListMutation.newSubscribeMutation("foo", Scope.APP, 20L),
                ScopedSubscriptionListMutation.newSubscribeMutation("bar", Scope.WEB, 30L),
                ScopedSubscriptionListMutation.newSubscribeMutation("foo", Scope.SMS, 30L),
                ScopedSubscriptionListMutation.newSubscribeMutation("foo", Scope.APP, 40L),
                ScopedSubscriptionListMutation.newSubscribeMutation("bar", Scope.APP, 30L),
                ScopedSubscriptionListMutation.newUnsubscribeMutation("bar", Scope.APP, 30L)
        );

        List<ScopedSubscriptionListMutation> expected = Arrays.asList(
                ScopedSubscriptionListMutation.newSubscribeMutation("bar", Scope.WEB, 30L),
                ScopedSubscriptionListMutation.newSubscribeMutation("foo", Scope.SMS, 30L),
                ScopedSubscriptionListMutation.newSubscribeMutation("foo", Scope.APP, 40L),
                ScopedSubscriptionListMutation.newUnsubscribeMutation("bar", Scope.APP, 30L)
        );

        List<ScopedSubscriptionListMutation> collapsed = ScopedSubscriptionListMutation.collapseMutations(mutations);
        assertEquals(expected, collapsed);
    }
}
