/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

public enum class MediaType(
    private val value: String,
    public val isPlayable: Boolean
) {
    IMAGE("image", isPlayable = false),
    VIDEO("video", isPlayable = true),
    YOUTUBE("youtube", isPlayable = true),
    VIMEO("vimeo", isPlayable = true);

    override fun toString(): String {
        return name.lowercase()
    }

    public companion object {

        @Throws(JsonException::class)
        public fun from(value: JsonValue): MediaType {
            val content = value.requireString().lowercase()
            return entries.firstOrNull { it.value == content }
                ?: throw JsonException("Unknown MediaType value: $content")
        }
    }
}
