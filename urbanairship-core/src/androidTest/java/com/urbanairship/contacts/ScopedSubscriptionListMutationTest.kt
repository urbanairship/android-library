/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue.Companion.parseString
import com.urbanairship.util.DateUtils
import java.util.Arrays
import junit.framework.TestCase
import org.junit.Test

class ScopedSubscriptionListMutationTest : TestCase() {

    @Test
    fun testFromJson() {
        val jsonString =
            "{ \"scope\": \"app\", \"action\": \"subscribe\", \"list_id\": \"listId\", \"timestamp\": \"$EPOCH\" }"
        val mutation = ScopedSubscriptionListMutation.fromJsonValue(parseString(jsonString))

        assertEquals(Scope.APP, mutation.scope)
        assertEquals(ScopedSubscriptionListMutation.ACTION_SUBSCRIBE, mutation.action)
        assertEquals(EPOCH, mutation.timestamp)
        assertEquals("listId", mutation.listId)
    }

    @Test
    fun testToJsonSubscribe() {
        val mutation = ScopedSubscriptionListMutation.newSubscribeMutation("someList", Scope.SMS, 0)

        val expected =
            "{ \"scope\": \"sms\", \"action\": \"subscribe\", \"list_id\": \"someList\", \"timestamp\": \"" + EPOCH + "\" }"
        assertEquals(parseString(expected), mutation.toJsonValue())
    }

    @Test
    @Throws(JsonException::class)
    fun testToJsonUnsubscribe() {
        val mutation =
            ScopedSubscriptionListMutation.newUnsubscribeMutation("someList", Scope.SMS, 0)

        val expected =
            "{ \"scope\": \"sms\", \"action\": \"unsubscribe\", \"list_id\": \"someList\", \"timestamp\": \"" + EPOCH + "\" }"
        assertEquals(parseString(expected), mutation.toJsonValue())
    }

    @Test
    fun testCollapse() {
        val mutations = Arrays.asList(
            ScopedSubscriptionListMutation.newSubscribeMutation("foo", Scope.SMS, 10L),
            ScopedSubscriptionListMutation.newSubscribeMutation("foo", Scope.APP, 20L),
            ScopedSubscriptionListMutation.newSubscribeMutation("bar", Scope.WEB, 30L),
            ScopedSubscriptionListMutation.newSubscribeMutation("foo", Scope.SMS, 30L),
            ScopedSubscriptionListMutation.newSubscribeMutation("foo", Scope.APP, 40L),
            ScopedSubscriptionListMutation.newSubscribeMutation("bar", Scope.APP, 30L),
            ScopedSubscriptionListMutation.newUnsubscribeMutation("bar", Scope.APP, 30L)
        )

        val expected = Arrays.asList(
            ScopedSubscriptionListMutation.newSubscribeMutation("bar", Scope.WEB, 30L),
            ScopedSubscriptionListMutation.newSubscribeMutation("foo", Scope.SMS, 30L),
            ScopedSubscriptionListMutation.newSubscribeMutation("foo", Scope.APP, 40L),
            ScopedSubscriptionListMutation.newUnsubscribeMutation("bar", Scope.APP, 30L)
        )

        val collapsed = ScopedSubscriptionListMutation.collapseMutations(mutations)
        assertEquals(expected, collapsed)
    }

    companion object {

        private val EPOCH = DateUtils.createIso8601TimeStamp(0L)
    }
}
