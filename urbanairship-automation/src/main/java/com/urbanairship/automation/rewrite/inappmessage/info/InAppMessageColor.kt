package com.urbanairship.automation.rewrite.inappmessage.info

import android.graphics.Color
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.util.ColorUtils
import kotlin.jvm.Throws

public class InAppMessageColor internal constructor(
    private val color: Int
) : JsonSerializable {

    internal companion object {

        @Throws(JsonException::class, RuntimeException::class)
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
