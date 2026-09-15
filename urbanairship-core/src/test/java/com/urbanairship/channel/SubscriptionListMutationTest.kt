package com.urbanairship.channel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.DateUtils
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class SubscriptionListMutationTest {

    private val epoch = DateUtils.createIso8601TimeStamp(Instant.ofEpochMilli(0))

    @Test
    public fun testSubscribeMutation() {
        val mutation = SubscriptionListMutation.newSubscribeMutation("listId", Instant.ofEpochMilli(0))

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
        val mutation = SubscriptionListMutation.newUnsubscribeMutation("listId", Instant.ofEpochMilli(0))

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
            SubscriptionListMutation.newSubscribeMutation("foo", Instant.ofEpochMilli(10)),
            SubscriptionListMutation.newSubscribeMutation("foo", Instant.ofEpochMilli(20)),
            SubscriptionListMutation.newSubscribeMutation("foo", Instant.ofEpochMilli(30)),
            SubscriptionListMutation.newSubscribeMutation("foo", Instant.ofEpochMilli(40))
        )

        val collapsed = SubscriptionListMutation.collapseMutations(mutations)
        assertEquals(1, collapsed.size.toLong())

        val first = collapsed[0]
        val expected = jsonMapOf(
            "action" to "subscribe",
            "list_id" to "foo",
            "timestamp" to DateUtils.createIso8601TimeStamp(Instant.ofEpochMilli(40))

        )
        assertEquals(expected.toJsonValue(), first.toJsonValue())
    }

    @Test
    public fun testCollapseDifferentMutationsWithSameListId() {
        val mutations = listOf(
            SubscriptionListMutation.newSubscribeMutation("foo", Instant.ofEpochMilli(10)),
            SubscriptionListMutation.newUnsubscribeMutation("foo", Instant.ofEpochMilli(20)),
            SubscriptionListMutation.newSubscribeMutation("foo", Instant.ofEpochMilli(30)),
            SubscriptionListMutation.newUnsubscribeMutation("foo", Instant.ofEpochMilli(40))
        )

        val collapsed = SubscriptionListMutation.collapseMutations(mutations)
        assertEquals(1, collapsed.size.toLong())

        val first = collapsed[0]
        val expected = jsonMapOf(
            "action" to "unsubscribe",
            "list_id" to "foo",
            "timestamp" to DateUtils.createIso8601TimeStamp(Instant.ofEpochMilli(30))

        )
        assertEquals(expected.toJsonValue(), first.toJsonValue())
    }

    @Test(expected = JsonException::class)
    public fun testToFromJsonValue() {
        val mutation = SubscriptionListMutation.newSubscribeMutation("bar", Instant.ofEpochMilli(0))

        assertEquals(mutation, SubscriptionListMutation.fromJsonValue(mutation.toJsonValue()))

        SubscriptionListMutation.fromJsonValue(JsonMap.EMPTY_MAP.toJsonValue())
    }

    @Test
    public fun testEqualsAndHashCode() {
        val mutation = SubscriptionListMutation.newSubscribeMutation("same", Instant.ofEpochMilli(0))
        val sameMutation = SubscriptionListMutation.newSubscribeMutation("same", Instant.ofEpochMilli(0))
        val differentMutation = SubscriptionListMutation.newUnsubscribeMutation("same", Instant.ofEpochMilli(0))
        val differentListId = SubscriptionListMutation.newSubscribeMutation("different", Instant.ofEpochMilli(0))

        assertEquals(mutation, sameMutation)
        assertNotEquals(mutation, differentMutation)
        assertNotEquals(mutation, differentListId)

        assertEquals(mutation.hashCode().toLong(), sameMutation.hashCode().toLong())
        assertNotEquals(mutation.hashCode().toLong(), differentMutation.hashCode().toLong())
        assertNotEquals(mutation.hashCode().toLong(), differentListId.hashCode().toLong())
    }
}
