/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.android.layout.model.SafeAreaAware
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap

public class EmbeddedPlacement(
    public val size: ConstrainedSize,
    public val margin: Margin?,
    public val border: Border?,
    public val backgroundColor: Color?
) {
    public companion object {

        @Throws(JsonException::class)
        public fun fromJson(json: JsonMap): EmbeddedPlacement {
            val sizeJson = json.opt("size").map ?: throw JsonException(
                "Failed to parse Modal Placement! Field 'size' is required."
            )

            val marginJson = json.opt("margin").map
            val borderJson = json.opt("border").map
            val backgroundJson = json.opt("background_color").map

            val size = ConstrainedSize.fromJson(sizeJson)
            val margin = marginJson?.let { Margin.fromJson(it) }
            val border = borderJson?.let { Border.fromJson(it) }
            val backgroundColor = backgroundJson?.let { Color.fromJson(it) }

            return EmbeddedPlacement(size, margin, border, backgroundColor)
        }
    }
}
