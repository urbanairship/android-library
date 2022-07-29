package com.urbanairship.preferencecenter.data

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.preferencecenter.util.jsonMapOf
import com.urbanairship.preferencecenter.util.requireField
import com.urbanairship.preferencecenter.util.toJsonList

/**
 * Preference Center Configuration.
 */
data class PreferenceCenterConfig(
    val id: String,
    val sections: List<Section>,
    val display: CommonDisplay,
    val options: Options? = null
) {

    constructor(id: String, sections: List<Section>, display: CommonDisplay) : this(id, sections, display, null)

    companion object {
        internal const val KEY_ID = "id"
        private const val KEY_DISPLAY = "display"
        private const val KEY_SECTIONS = "sections"
        private const val KEY_OPTIONS = "options"

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
                display = json.get(KEY_DISPLAY)?.map?.let { CommonDisplay.parse(it) } ?: CommonDisplay.EMPTY,
                options = json.get(KEY_OPTIONS)?.map?.let { Options.parse(it) }
            )
    }

    /** Flag indicating if this preference center configuration contains any channel subscription items. */
    val hasChannelSubscriptions: Boolean = sections.any { it.hasChannelSubscriptions }
    /** Flag indicating if this preference center configuration contains any contact subscription items. */
    val hasContactSubscriptions: Boolean = sections.any { it.hasContactSubscriptions }

    internal fun toJson(): JsonMap = jsonMapOf(
        KEY_ID to id,
        KEY_SECTIONS to sections.map(Section::toJson).toJsonList(),
        KEY_DISPLAY to display.toJson(),
        KEY_OPTIONS to options?.toJson()
    )
}
