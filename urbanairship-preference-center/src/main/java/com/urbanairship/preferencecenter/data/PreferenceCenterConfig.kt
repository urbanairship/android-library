package com.urbanairship.preferencecenter.data

import android.os.Parcel
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import com.urbanairship.json.toJsonList
import kotlinx.parcelize.Parceler

/**
 * Preference Center Configuration.
 */
public data class PreferenceCenterConfig(
    val id: String,
    val sections: List<Section>,
    val display: CommonDisplay,
    val options: Options? = null
) {

    public constructor(id: String, sections: List<Section>, display: CommonDisplay) : this(id, sections, display, null)

    internal companion object {
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
        @Throws(JsonException::class)
        internal fun parse(json: JsonMap): PreferenceCenterConfig = PreferenceCenterConfig(
            id = json.requireField(KEY_ID),
            sections = json.opt(KEY_SECTIONS).optList().map { Section.parse(it.optMap()) },
            display = json.get(KEY_DISPLAY)?.map?.let { CommonDisplay.parse(it) } ?: CommonDisplay.EMPTY,
            options = json.get(KEY_OPTIONS)?.map?.let { Options.parse(it) }
        )
    }

    /** Flag indicating if this preference center configuration contains any channel subscription items. */
    val hasChannelSubscriptions: Boolean

    /** Flag indicating if this preference center configuration contains any contact subscription items. */
    val hasContactSubscriptions: Boolean

    /** Flag indicating if this preference center configuration contains any contact management items. */
    val hasContactManagement: Boolean

    init {
        var channelSubscriptions = false
        var contactSubscriptions = false
        var contactManagement = false

        for (section in sections) {
            channelSubscriptions = section.hasChannelSubscriptions || channelSubscriptions
            contactSubscriptions = section.hasContactSubscriptions || contactSubscriptions
            contactManagement = section.hasContactManagement || contactManagement
        }

        hasContactManagement = contactManagement
        hasChannelSubscriptions = channelSubscriptions
        hasContactSubscriptions = contactSubscriptions
    }

    @Throws(JsonException::class)
    internal fun toJson(): JsonMap = jsonMapOf(
        KEY_ID to id,
        KEY_SECTIONS to sections.map(Section::toJson).toJsonList(),
        KEY_DISPLAY to display.toJson(),
        KEY_OPTIONS to options?.toJson()
    )
}

internal object PreferenceCenterConfigParceler : Parceler<PreferenceCenterConfig> {
    @Throws(JsonException::class)
    override fun create(parcel: Parcel): PreferenceCenterConfig {
        val config = JsonValue.parseString(parcel.readString()).requireMap()
        return PreferenceCenterConfig.parse(config)
    }

    @Throws(JsonException::class)
    override fun PreferenceCenterConfig.write(parcel: Parcel, flags: Int) {
        parcel.writeString(toJson().toString())
    }
}
