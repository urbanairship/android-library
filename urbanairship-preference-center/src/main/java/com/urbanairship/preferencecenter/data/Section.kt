package com.urbanairship.preferencecenter.data

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.preferencecenter.util.requireField
import com.urbanairship.preferencecenter.util.toJsonList

/**
 * Preference sections.
 */
sealed class Section(private val type: String) {
    abstract val id: String
    abstract val items: List<Item>
    abstract val display: CommonDisplay
    abstract val conditions: Conditions

    fun filterItems(predicate: (Item) -> Boolean): Section =
        when (this) {
            is Common -> copy(items = items.filter(predicate))
            is SectionBreak -> this
        }

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
        override val display: CommonDisplay,
        override val conditions: Conditions
    ) : Section(TYPE_SECTION) {
        override fun toJson(): JsonMap = jsonMapBuilder().build()
    }

    /**
     * Labeled section break.
     */
    data class SectionBreak(
        override val id: String,
        override val display: CommonDisplay,
        override val conditions: Conditions
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
        private const val KEY_CONDITIONS = "conditions"

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
                        items = items,
                        conditions = Condition.parse(json.get(KEY_CONDITIONS))
                    )
                }
                TYPE_SECTION_BREAK -> SectionBreak(
                    id = id,
                    display = display,
                    conditions = Condition.parse(json.get(KEY_CONDITIONS))
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
            .put(KEY_CONDITIONS, conditions.map(Condition::toJson).toJsonList())
}
