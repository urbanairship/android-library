/* Copyright Airship and Contributors */

package com.urbanairship.iam.info

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import java.util.Objects

/**
 * Media display info.
 */
public class InAppMessageMediaInfo(
    public val url: String,
    public val type: MediaType,
    public val description: String?
) : JsonSerializable {
    public enum class MediaType(internal val json: String) {
        /**
         * Youtube media type.
         */
        YOUTUBE("youtube"),

        /**
         * Vimeo media type.
         */
        VIMEO("vimeo"),

        /**
         * Video media type.
         */
        VIDEO("video"),

        /**
         * Image media type.
         */
        IMAGE("image");

        internal companion object {

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): MediaType {
                val string = value.requireString()
                return entries.firstOrNull { it.json == string }
                    ?: throw JsonException("Invalid media type $value")
            }
        }
    }

    public companion object {
        private const val URL_KEY = "url"
        private const val DESCRIPTION_KEY = "description"
        private const val TYPE_KEY = "type"

        /**
         * Parses a [InAppMessageMediaInfo] from a [JsonValue].
         *
         * @param value The json value.
         * @return The parsed media info.
         * @throws JsonException If the media info was unable to be parsed.
         */
        @Throws(JsonException::class)
        public fun fromJson(source: JsonValue): InAppMessageMediaInfo {
            val content = source.optMap()
            return InAppMessageMediaInfo(
                url = content.requireField(URL_KEY),
                type = content.require(TYPE_KEY).let(MediaType.Companion::fromJson),
                description = content.optionalField(DESCRIPTION_KEY)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        URL_KEY to url,
        TYPE_KEY to type.json,
        DESCRIPTION_KEY to description
    ).toJsonValue()

    override fun toString(): String = toJsonValue().toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InAppMessageMediaInfo

        if (url != other.url) return false
        if (type != other.type) return false
        return description == other.description
    }

    override fun hashCode(): Int {
        return Objects.hash(url, type, description)
    }
}
