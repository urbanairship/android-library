/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalMap

public class EmbeddedPlacement(
    public val size: ConstrainedSize,
    public val margin: Margin?,
    public val border: Border?,
    public val backgroundColor: Color?
) {
    public companion object {

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): EmbeddedPlacement {
            val content = json.requireMap()
            return EmbeddedPlacement(
                size = ConstrainedSize.fromJson(content.require("size")),
                margin = content["margin"]?.let(Margin::fromJson),
                border = content["border"]?.let(Border::fromJson),
                backgroundColor = content["background_color"]?.let(Color::fromJson)
            )
        }
    }
}
