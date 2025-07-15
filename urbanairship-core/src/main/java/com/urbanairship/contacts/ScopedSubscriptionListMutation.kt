/* Copyright Airship and Contributors */
package com.urbanairship.contacts

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
 * Defines a scoped subscription list mutation.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ScopedSubscriptionListMutation internal constructor(
    public val action: String,
    public val listId: String,
    public val scope: Scope,
    public val timestamp: String?
) : JsonSerializable {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_ACTION to action,
        KEY_LIST_ID to listId,
        KEY_SCOPE to scope,
        KEY_TIMESTAMP to timestamp
    ).toJsonValue()

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as ScopedSubscriptionListMutation
        return ObjectsCompat.equals(action, that.action)
                && ObjectsCompat.equals(listId, that.listId)
                && ObjectsCompat.equals(scope, that.scope)
                && ObjectsCompat.equals(timestamp, that.timestamp)
    }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(action, listId, timestamp, scope)
    }

    override fun toString(): String {
        return "ScopedSubscriptionListMutation{action='$action', listId='$listId', " +
                "scope=$scope, timestamp='$timestamp'}"
    }

    public fun apply(subscriptionLists: MutableMap<String, MutableSet<Scope>>) {
        val scopes = subscriptionLists.getOrPut(listId) { mutableSetOf() }

        when (action) {
            ACTION_SUBSCRIBE -> scopes.add(scope)
            ACTION_UNSUBSCRIBE -> scopes.remove(scope)
        }

        if (scopes.isEmpty()) {
            subscriptionLists.remove(listId)
        }
    }

    public companion object {

        private const val KEY_ACTION = "action"
        private const val KEY_LIST_ID = "list_id"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_SCOPE = "scope"

        /**
         * Subscribe action.
         */
        public const val ACTION_SUBSCRIBE: String = "subscribe"

        /**
         * Unsubscribe action.
         */
        public const val ACTION_UNSUBSCRIBE: String = "unsubscribe"

        public fun newSubscribeMutation(
            listId: String,
            scope: Scope,
            timestamp: Long
        ): ScopedSubscriptionListMutation {
            return ScopedSubscriptionListMutation(
                action = ACTION_SUBSCRIBE,
                listId = listId,
                scope = scope,
                timestamp = DateUtils.createIso8601TimeStamp(timestamp)
            )
        }

        public fun newUnsubscribeMutation(
            listId: String,
            scope: Scope,
            timestamp: Long
        ): ScopedSubscriptionListMutation {
            return ScopedSubscriptionListMutation(
                action = ACTION_UNSUBSCRIBE,
                listId = listId,
                scope = scope,
                timestamp = DateUtils.createIso8601TimeStamp(timestamp)
            )
        }

        @Throws(JsonException::class)
        public fun fromJsonValue(input: JsonValue): ScopedSubscriptionListMutation {
            val json = input.requireMap()

            return ScopedSubscriptionListMutation(
                action = json.requireField(KEY_ACTION),
                listId = json.requireField(KEY_LIST_ID),
                scope = json.requireField(KEY_SCOPE),
                timestamp = json.optionalField(KEY_TIMESTAMP)
            )
        }

        public fun fromJsonList(jsonList: JsonList): List<ScopedSubscriptionListMutation> {
            return jsonList.mapNotNull {
                try {
                    fromJsonValue(it)
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
         * @return A collapsed [ScopedSubscriptionListMutation] object.
         */
        public fun collapseMutations(
            mutations: List<ScopedSubscriptionListMutation>
        ): List<ScopedSubscriptionListMutation> {

            val result = mutableListOf<ScopedSubscriptionListMutation>()
            val scopedListIds = mutableSetOf<String>()

            mutations.reversed().forEach {
                val key = "${it.scope}:${it.listId}"
                if (scopedListIds.contains(key)) {
                    return@forEach
                }

                scopedListIds.add(key)
                result.add(it)
            }

            return result.reversed()
        }
    }
}
