/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.shape

import android.graphics.drawable.GradientDrawable
import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

/**
 * Shape types.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public enum class ShapeType(
    private val value: String,
    public val drawableShape: Int
) {

    RECTANGLE("rectangle", GradientDrawable.RECTANGLE),
    ELLIPSE("ellipse", GradientDrawable.OVAL);

    override fun toString(): String {
        return name.lowercase()
    }

    public companion object {

        @Throws(JsonException::class)
        public fun from(value: JsonValue): ShapeType {
            val content = value.requireString().lowercase()

            return entries.firstOrNull { it.value == content }
                ?: throw JsonException("Unknown ShapeType value: $value")
        }
    }
}
