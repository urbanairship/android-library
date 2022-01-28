package com.urbanairship.preferencecenter.data

import com.urbanairship.contacts.Scope
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.preferencecenter.util.jsonMapOf
import com.urbanairship.preferencecenter.util.optionalField
import com.urbanairship.preferencecenter.util.requireField
import com.urbanairship.preferencecenter.util.toJsonList

/**
 * Preference Center Payload from Remote Data.
 */
internal data class PreferenceCenterPayload(
    val config: PreferenceCenterConfig
) {
    companion object {
        private const val KEY_FORM = "form"

        /**
         * Parses a `JsonMap` into a `PreferenceCenterPayload` object.
         *
         * @hide
         * @throws JsonException
         */
        internal fun parse(json: JsonMap): PreferenceCenterPayload =
            PreferenceCenterPayload(PreferenceCenterConfig.parse(json.opt(KEY_FORM).optMap()))
    }

    internal fun toJson(): JsonMap = jsonMapOf(
        KEY_FORM to config.toJson()
    )
}

/**
 * Preference Center Configuration.
 */
data class PreferenceCenterConfig(
    val id: String,
    val sections: List<Section>,
    val display: CommonDisplay
) {
    companion object {
        private const val KEY_ID = "id"
        private const val KEY_DISPLAY = "display"
        private const val KEY_SECTIONS = "sections"

        /**
         * Parses a `JsonMap` into a `PreferenceCenterConfig` object.
         *
         * @hide
         * @throws JsonException
         */
        internal fun parse(json: JsonMap): PreferenceCenterConfig =
            PreferenceCenterConfig(
                id = json.requireField(KEY_ID),
                sections = json.opt(KEY_SECTIONS).optList().map { Section.parse(it.optMap()) },
                display = json.get(KEY_DISPLAY)?.map?.let { CommonDisplay.parse(it) } ?: CommonDisplay.EMPTY
            )
    }

    /** Flag indicating if this preference center configuration contains any channel subscription items. */
    val hasChannelSubscriptions: Boolean = sections.any { it.hasChannelSubscriptions }
    /** Flag indicating if this preference center configuration contains any contact subscription items. */
    val hasContactSubscriptions: Boolean = sections.any { it.hasContactSubscriptions }

    internal fun toJson(): JsonMap = jsonMapOf(
        KEY_ID to id,
        KEY_SECTIONS to sections.map(Section::toJson).toJsonList(),
        KEY_DISPLAY to display.toJson()
    )
}

/**
 * Common display attributes.
 */
data class CommonDisplay(
    val name: String? = null,
    val description: String? = null
) {
    companion object {
        @JvmStatic val EMPTY = CommonDisplay(null, null)

        private const val KEY_NAME = "name"
        private const val KEY_DESCRIPTION = "description"

        /**
         * Parses a `JsonMap` into a `CommonDisplay` object.
         *
         * @hide
         * @throws JsonException
         */
        internal fun parse(json: JsonMap): CommonDisplay =
            CommonDisplay(
                name = json.optionalField(KEY_NAME),
                description = json.optionalField(KEY_DESCRIPTION)
            )

        /**
         * Parses a `JsonValue` into a `CommonDisplay` object.
         *
         * @hide
         * @throws JsonException
         */
        internal fun parse(json: JsonValue?): CommonDisplay =
            json?.map?.let { parse(it) } ?: EMPTY
    }

    internal fun toJson(): JsonMap = jsonMapOf(
        KEY_NAME to name,
        KEY_DESCRIPTION to description
    )
}

/**
 * Preference items.
 */
sealed class Item(private val type: String) {
    abstract val id: String
    abstract val display: CommonDisplay

    internal abstract val hasChannelSubscriptions: Boolean
    internal abstract val hasContactSubscriptions: Boolean

    /**
     * Channel subscription preference item.
     */
    data class ChannelSubscription(
        override val id: String,
        val subscriptionId: String,
        override val display: CommonDisplay
    ) : Item(TYPE_CHANNEL_SUBSCRIPTION) {
        override val hasChannelSubscriptions = true
        override val hasContactSubscriptions = false

        override fun toJson(): JsonMap =
            jsonMapBuilder()
                .put(KEY_SUBSCRIPTION_ID, subscriptionId)
                .build()
    }

    /**
     * Channel subscription preference item.
     */
    data class ContactSubscription(
        override val id: String,
        val subscriptionId: String,
        val scopes: Set<Scope>,
        override val display: CommonDisplay
    ) : Item(TYPE_CONTACT_SUBSCRIPTION) {
        override val hasChannelSubscriptions = false
        override val hasContactSubscriptions = true

        override fun toJson(): JsonMap =
            jsonMapBuilder()
                .put(KEY_SUBSCRIPTION_ID, subscriptionId)
                .build()
    }

    /**
     * Contact subscription group.
     */
    data class ContactSubscriptionGroup(
        override val id: String,
        val subscriptionId: String,
        val components: List<Component>,
        override val display: CommonDisplay
    ) : Item(TYPE_CONTACT_SUBSCRIPTION_GROUP) {
        override val hasChannelSubscriptions = false
        override val hasContactSubscriptions = true

        override fun toJson(): JsonMap =
            jsonMapBuilder()
                .put(KEY_SUBSCRIPTION_ID, subscriptionId)
                .put(KEY_COMPONENTS, components.toJson())
                .build()

        data class Component(
            val scopes: Set<Scope>,
            val display: CommonDisplay
        ) {

            fun toJson(): JsonMap =
                jsonMapOf(
                    KEY_SCOPES to JsonValue.wrap(scopes.map(Scope::toJsonValue)),
                    KEY_DISPLAY to display.toJson()
                )

            companion object {
                internal fun parse(json: JsonMap): Component {
                    return Component(
                        scopes = json.opt(KEY_SCOPES).optList().map(Scope::fromJson).toSet(),
                        display = CommonDisplay.parse(json.get(KEY_DISPLAY))
                    )
                }
            }
        }

        private fun List<Component>.toJson(): JsonValue =
            JsonValue.wrap(this.map(Component::toJson))
    }

    companion object {
        private const val TYPE_CHANNEL_SUBSCRIPTION = "channel_subscription"
        private const val TYPE_CONTACT_SUBSCRIPTION = "contact_subscription"
        private const val TYPE_CONTACT_SUBSCRIPTION_GROUP = "contact_subscription_group"

        private const val KEY_TYPE = "type"
        private const val KEY_ID = "id"
        private const val KEY_DISPLAY = "display"

        private const val KEY_SUBSCRIPTION_ID = "subscription_id"
        private const val KEY_COMPONENTS = "components"
        private const val KEY_SCOPES = "scopes"

        /**
         * Parses a `JsonMap` into an `Item` subclass, based on the value of the `type` field.
         *
         * @hide
         * @throws JsonException
         */
        internal fun parse(json: JsonMap): Item {
            val id = json.requireField<String>(KEY_ID)
            val display = CommonDisplay.parse(json.get(KEY_DISPLAY))

            return when (val type = json.get(KEY_TYPE)?.string) {
                TYPE_CHANNEL_SUBSCRIPTION -> ChannelSubscription(
                    id = id,
                    subscriptionId = json.requireField(KEY_SUBSCRIPTION_ID),
                    display = display
                )
                TYPE_CONTACT_SUBSCRIPTION_GROUP -> {
                    val components = json.opt(KEY_COMPONENTS).list?.map {
                        ContactSubscriptionGroup.Component.parse(it.optMap())
                    } ?: emptyList()
                    ContactSubscriptionGroup(
                        id = id,
                        subscriptionId = json.requireField(KEY_SUBSCRIPTION_ID),
                        display = display,
                        components = components
                    )
                }
                else -> throw JsonException("Unknown Preference Center Item type: '$type'")
            }
        }
    }

    internal abstract fun toJson(): JsonMap

    protected fun jsonMapBuilder(): JsonMap.Builder =
        JsonMap.newBuilder()
            .put(KEY_ID, id)
            .put(KEY_TYPE, type)
            .put(KEY_DISPLAY, display.toJson())
}

/**
 * Preference sections.
 */
sealed class Section(private val type: String) {
    abstract val id: String
    abstract val items: List<Item>
    abstract val display: CommonDisplay

    /** Returns `true` if this section contains channel subscription items. */
    internal val hasChannelSubscriptions: Boolean by lazy {
        items.any { it.hasChannelSubscriptions }
    }
    /** Returns `true` if this section contains contact subscription items. */
    internal val hasContactSubscriptions: Boolean by lazy {
        items.any { it.hasContactSubscriptions }
    }

    /**
     * Common preference section.
     */
    data class Common(
        override val id: String,
        override val items: List<Item>,
        override val display: CommonDisplay
    ) : Section(TYPE_SECTION) {
        override fun toJson(): JsonMap = jsonMapBuilder().build()
    }

    /**
     * Labeled section break.
     */
    data class SectionBreak(
        override val id: String,
        override val display: CommonDisplay
    ) : Section(TYPE_SECTION_BREAK) {
        override val items: List<Item> = emptyList()

        override fun toJson(): JsonMap = jsonMapBuilder().build()
    }

    companion object {
        private const val TYPE_SECTION = "section"
        private const val TYPE_SECTION_BREAK = "labeled_section_break"

        private const val KEY_TYPE = "type"
        private const val KEY_ID = "id"
        private const val KEY_ITEMS = "items"
        private const val KEY_DISPLAY = "display"

        /**
         * Parses a `JsonMap` into an `Section` subclass, based on the value of the `type` field.
         *
         * @hide
         * @throws JsonException
         */
        internal fun parse(json: JsonMap): Section {
            val id = json.requireField<String>(KEY_ID)
            val display = CommonDisplay.parse(json.get(KEY_DISPLAY))

            return when (val type = json.get(KEY_TYPE)?.string) {
                TYPE_SECTION -> {
                    val items = json.opt(KEY_ITEMS).optList().map { Item.parse(it.optMap()) }
                    return Common(
                        id = id,
                        display = display,
                        items = items
                    )
                }
                TYPE_SECTION_BREAK -> SectionBreak(
                    id = id,
                    display = display
                )
                else -> throw JsonException("Unknown Preference Center Section type: '$type'")
            }
        }
    }

    internal abstract fun toJson(): JsonMap

    protected fun jsonMapBuilder() =
        JsonMap.newBuilder()
            .put(KEY_ID, id)
            .put(KEY_TYPE, type)
            .put(KEY_DISPLAY, display.toJson())
            .put(KEY_ITEMS, items.map(Item::toJson).toJsonList())
}
