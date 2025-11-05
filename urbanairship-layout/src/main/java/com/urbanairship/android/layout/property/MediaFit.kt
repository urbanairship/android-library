/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import android.widget.ImageView.ScaleType
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import java.util.Locale

/**
 * Property that determines how an image should be scaled in an `ImageView`.
 */
public enum class MediaFit(
    private val value: String,
    public val scaleType: ScaleType
) {

    @Deprecated("")
    CENTER("center", ScaleType.CENTER),
    @Deprecated("")
    CENTER_CROP("center_crop", ScaleType.CENTER_CROP),
    CENTER_INSIDE("center_inside", ScaleType.FIT_CENTER),
    FIT_CROP("fit_crop", ScaleType.MATRIX);

    override fun toString(): String {
        return name.lowercase()
    }

    public companion object {

        @Throws(JsonException::class)
        public fun from(value: JsonValue): MediaFit {
            val content = value.requireString().lowercase()

            return entries.firstOrNull { it.value == content }
                ?: throw JsonException("Unknown MediaFit value: $value")
        }
    }
}
