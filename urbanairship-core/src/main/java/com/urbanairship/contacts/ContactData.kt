/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal data class ContactData internal constructor(
    val tagGroups: Map<String, Set<String>> = emptyMap(),
    val attributes: Map<String, JsonValue> = emptyMap(),
    val subscriptionLists: Map<String, Set<Scope>> = emptyMap(),
    val associatedChannels: List<AssociatedChannel> = emptyList()
) : JsonSerializable {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        TAG_GROUPS_KEY to tagGroups,
        ATTRIBUTES_KEY to attributes,
        SUBSCRIPTION_LISTS to subscriptionLists,
        ASSOCIATED_CHANNELS_KEY to associatedChannels
    ).toJsonValue()

    internal val isEmpty: Boolean
        get() = attributes.isEmpty() && tagGroups.isEmpty() && associatedChannels.isEmpty() && subscriptionLists.isEmpty()

    internal constructor(jsonValue: JsonValue) : this(
        tagGroups = jsonValue.optMap().opt(TAG_GROUPS_KEY).optMap().map.mapValues { entry ->
            entry.value.optList().mapNotNull { it.string }.toSet()
        },
        attributes = jsonValue.optMap().opt(ATTRIBUTES_KEY).optMap().map,
        subscriptionLists = jsonValue.optMap().opt(SUBSCRIPTION_LISTS).optMap().map.mapValues { entry ->
            entry.value.optList().mapNotNull { Scope.fromJson(it) }.toSet()
        },
        associatedChannels = jsonValue.optMap().opt(ASSOCIATED_CHANNELS_KEY).optList().mapNotNull {
            AssociatedChannel(it)
        }
    )

    internal companion object {
        private const val TAG_GROUPS_KEY = "tag_groups"
        private const val ATTRIBUTES_KEY = "attributes"
        private const val ASSOCIATED_CHANNELS_KEY = "associated_channels"
        private const val SUBSCRIPTION_LISTS = "subscription_lists"
    }
}
