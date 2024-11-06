/* Copyright Airship and Contributors */

package com.urbanairship.iam.content

import com.urbanairship.android.layout.Thomas
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

/**
 * Display content for Scenes and Surveys.
 */
public class AirshipLayout private constructor(
    public val layoutInfo: LayoutInfo,
    private val jsonValue: JsonValue //TODO: make LayoutInfo serializable and remove that field
) : JsonSerializable {

    public companion object {
        private const val LAYOUT_KEY = "layout"

        /**
         * Parses layout display JSON.
         *
         * @param value The json payload.
         * @return The parsed display content.
         * @throws JsonException If the json was unable to be parsed.
         */
        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): AirshipLayout {
            val json = value.requireMap()
            return AirshipLayout(
                layoutInfo = LayoutInfo(json.require(LAYOUT_KEY).requireMap()),
                jsonValue = value
            )
        }
    }

    internal fun validate(): Boolean = Thomas.isValid(layoutInfo)
    internal fun isEmbedded(): Boolean = layoutInfo.isEmbedded

    override fun toJsonValue(): JsonValue = jsonValue
    override fun toString(): String = toJsonValue().toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AirshipLayout

        return jsonValue == other.jsonValue
    }

    override fun hashCode(): Int {
        return layoutInfo.hashCode()
    }
}
