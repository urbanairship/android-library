/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

public class EmbeddedPlacementSelector(
    public val placement: EmbeddedPlacement,
    /** What the environment has to match for [placement] to apply. */
    public val conditions: SelectorConditions
) {
    public companion object {
        private const val KEY_PLACEMENT = "placement"

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): EmbeddedPlacementSelector {
            val content = json.requireMap()
            return EmbeddedPlacementSelector(
                placement = EmbeddedPlacement.fromJson(content.require(KEY_PLACEMENT)),
                conditions = SelectorConditions.fromJson(content)
            )
        }

        @Throws(JsonException::class)
        public fun fromJsonList(json: JsonList): List<EmbeddedPlacementSelector> = json.map(::fromJson)
    }
}
