/* Copyright Airship and Contributors */

package com.urbanairship.iam.info

import android.graphics.Color
import androidx.annotation.ColorInt
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.util.ColorUtils

/**
 * A color used by in-app message content.
 */
public class InAppMessageColor(
    /** The packed ARGB color value. */
    @get:ColorInt public val color: Int
) : JsonSerializable {

    internal companion object {

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): InAppMessageColor {
            val content = value.requireString()
            return InAppMessageColor(Color.parseColor(content))
        }
    }

    override fun toJsonValue(): JsonValue = JsonValue.wrap(ColorUtils.convertToString(color))

    override fun toString(): String = toJsonValue().toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InAppMessageColor

        return color == other.color
    }

    override fun hashCode(): Int {
        return color
    }

}
