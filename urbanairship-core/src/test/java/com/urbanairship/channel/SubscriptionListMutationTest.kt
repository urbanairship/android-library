package com.urbanairship.channel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class SubscriptionListMutationTest {

    private val epoch = DateUtils.createIso8601TimeStamp(0L)

    @Test
    public fun testSubscribeMutation() {
        val mutation = SubscriptionListMutation.newSubscribeMutation("listId", 0L)

        val expected = """
            {
              "action": "subscribe",
              "list_id": "listId",
              "timestamp": "$epoch"
            }
        """.trimIndent()

        assertEquals(JsonValue.parseString(expected), mutation.toJsonValue())
    }

    @Test
    public fun testSubscribeMutationNullTimestamp() {
        val mutation = SubscriptionListMutation("subscribe", "listId", null)

        val expected = """
            {
              "action": "subscribe",
              "list_id": "listId"
            }
        """.trimIndent()

        assertEquals(JsonValue.parseString(expected), mutation.toJsonValue())
    }

    @Test
    public fun testUnsubscribeMutation() {
        val mutation = SubscriptionListMutation.newUnsubscribeMutation("listId", 0L)

        val expected = """
            {
              "action": "unsubscribe",
              "list_id": "listId",
              "timestamp": "$epoch"
            }
        """.trimIndent()

        assertEquals(JsonValue.parseString(expected), mutation.toJsonValue())
    }

    @Test
    public fun testUnsubscribeMutationNullTimestamp() {
        val mutation = SubscriptionListMutation("unsubscribe", "listId", null)

        val expected = """
            {
              "action": "unsubscribe",
              "list_id": "listId"
            }
        """.trimIndent()
        assertEquals(JsonValue.parseString(expected), mutation.toJsonValue())
    }

    @Test
    public fun testCollapseDuplicateMutations() {
        val mutations = listOf(
            SubscriptionListMutation.newSubscribeMutation("foo", 10L),
            SubscriptionListMutation.newSubscribeMutation("foo", 20L),
            SubscriptionListMutation.newSubscribeMutation("foo", 30L),
            SubscriptionListMutation.newSubscribeMutation("foo", 40L)
        )

        val collapsed = SubscriptionListMutation.collapseMutations(mutations)
        assertEquals(1, collapsed.size.toLong())

        val first = collapsed[0]
        val expected = jsonMapOf(
            "action" to "subscribe",
            "list_id" to "foo",
            "timestamp" to DateUtils.createIso8601TimeStamp(40L)

        )
        assertEquals(expected.toJsonValue(), first.toJsonValue())
    }

    @Test
    public fun testCollapseDifferentMutationsWithSameListId() {
        val mutations = listOf(
            SubscriptionListMutation.newSubscribeMutation("foo", 10L),
            SubscriptionListMutation.newUnsubscribeMutation("foo", 20L),
            SubscriptionListMutation.newSubscribeMutation("foo", 30L),
            SubscriptionListMutation.newUnsubscribeMutation("foo", 40L)
        )

        val collapsed = SubscriptionListMutation.collapseMutations(mutations)
        assertEquals(1, collapsed.size.toLong())

        val first = collapsed[0]
        val expected = jsonMapOf(
            "action" to "unsubscribe",
            "list_id" to "foo",
            "timestamp" to DateUtils.createIso8601TimeStamp(30L)

        )
        assertEquals(expected.toJsonValue(), first.toJsonValue())
    }

    @Test(expected = JsonException::class)
    public fun testToFromJsonValue() {
        val mutation = SubscriptionListMutation.newSubscribeMutation("bar", 0L)

        assertEquals(mutation, SubscriptionListMutation.fromJsonValue(mutation.toJsonValue()))

        SubscriptionListMutation.fromJsonValue(JsonMap.EMPTY_MAP.toJsonValue())
    }

    @Test
    public fun testEqualsAndHashCode() {
        val mutation = SubscriptionListMutation.newSubscribeMutation("same", 0L)
        val sameMutation = SubscriptionListMutation.newSubscribeMutation("same", 0L)
        val differentMutation = SubscriptionListMutation.newUnsubscribeMutation("same", 0L)
        val differentListId = SubscriptionListMutation.newSubscribeMutation("different", 0L)

        assertEquals(mutation, sameMutation)
        assertNotEquals(mutation, differentMutation)
        assertNotEquals(mutation, differentListId)

        assertEquals(mutation.hashCode().toLong(), sameMutation.hashCode().toLong())
        assertNotEquals(mutation.hashCode().toLong(), differentMutation.hashCode().toLong())
        assertNotEquals(mutation.hashCode().toLong(), differentListId.hashCode().toLong())
    }
}
