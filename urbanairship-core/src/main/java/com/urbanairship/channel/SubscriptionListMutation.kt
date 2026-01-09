package com.urbanairship.channel

import androidx.annotation.RestrictTo
import androidx.core.util.ObjectsCompat
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import com.urbanairship.util.DateUtils

/**
 * Defines subscription list mutations.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SubscriptionListMutation(
    internal val action: String,
    internal val listId: String,
    internal val timestamp: String?
) : JsonSerializable {

    @Throws(JsonException::class)
    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_ACTION to action,
        KEY_LIST_ID to listId,
        KEY_TIMESTAMP to timestamp
    ).toJsonValue()

    internal fun apply(subscriptions: MutableSet<String>) {
        if (action == ACTION_SUBSCRIBE) {
            subscriptions.add(listId)
        } else {
            subscriptions.remove(listId)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as SubscriptionListMutation
        return action == that.action
                && listId == that.listId
                && ObjectsCompat.equals(timestamp, that.timestamp)
    }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(action, listId, timestamp)
    }

    override fun toString(): String {
        return "SubscriptionListMutation{action='$action', listId='$listId', timestamp='$timestamp'}"
    }

    internal companion object {

        private const val KEY_ACTION = "action"
        private const val KEY_LIST_ID = "list_id"
        private const val KEY_TIMESTAMP = "timestamp"

        /**
         * Subscribe action.
         * @hide
         */
        const val ACTION_SUBSCRIBE: String = "subscribe"

        /**
         * Unsubscribe action.
         * @hide
         */
        private const val ACTION_UNSUBSCRIBE: String = "unsubscribe"

        /**
         * Creates a mutation to subscribe to a list.
         *
         * @param listId The ID of the list to subscribe to.
         * @param timestamp The timestamp in milliseconds.
         * @return A new subscription list mutation.
         */
        fun newSubscribeMutation(listId: String, timestamp: Long): SubscriptionListMutation {
            return SubscriptionListMutation(
                action = ACTION_SUBSCRIBE,
                listId = listId,
                timestamp = DateUtils.createIso8601TimeStamp(timestamp)
            )
        }

        /**
         * Creates a mutation to unsubscribe from a list.
         *
         * @param listId The ID of the list to unsubscribe from.
         * @param timestamp The timestamp in milliseconds.
         * @return A new subscription list mutation.
         */
        fun newUnsubscribeMutation(listId: String, timestamp: Long): SubscriptionListMutation {
            return SubscriptionListMutation(
                action = ACTION_UNSUBSCRIBE,
                listId = listId,
                timestamp = DateUtils.createIso8601TimeStamp(timestamp)
            )
        }

        @Throws(JsonException::class)
        fun fromJsonValue(input: JsonValue): SubscriptionListMutation {
            val content = input.requireMap()

            return SubscriptionListMutation(
                action = content.requireField(KEY_ACTION),
                listId = content.requireField(KEY_LIST_ID),
                timestamp = content.optionalField(KEY_TIMESTAMP)
            )
        }

        fun fromJsonList(jsonList: JsonList): List<SubscriptionListMutation> {
            return jsonList.mapNotNull { item ->
                try {
                    fromJsonValue(item)
                } catch (e: JsonException) {
                    UALog.e(e, "Invalid subscription list mutation!")
                    null
                }
            }
        }

        /**
         * Collapses a collection of mutations into a single mutation payload.
         *
         * @param mutations a list of subscription list mutations to collapse.
         * @return A collapsed [SubscriptionListMutation] object.
         */
        fun collapseMutations(mutations: List<SubscriptionListMutation>): List<SubscriptionListMutation> {
            val result = mutableListOf<SubscriptionListMutation>()
            val addedMutationIds = mutableSetOf<String>()

            mutations.reversed().forEach { mutation ->
                if (addedMutationIds.contains(mutation.listId)) {
                    return@forEach
                }

                addedMutationIds.add(mutation.listId)
                result.add(mutation)
            }

            return result.reversed()
        }
    }
}
