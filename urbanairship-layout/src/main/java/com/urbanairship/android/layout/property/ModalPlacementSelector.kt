/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonValue

public class ModalPlacementSelector(
    @JvmField public val placement: ModalPlacement,
    /** What the environment has to match for [placement] to apply. */
    @JvmField public val conditions: SelectorConditions
) {

    public companion object {
        private const val KEY_PLACEMENT = "placement"

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): ModalPlacementSelector {
            val content = json.requireMap()

            return ModalPlacementSelector(
                placement = ModalPlacement.fromJson(content.require(KEY_PLACEMENT)),
                conditions = SelectorConditions.fromJson(content)
            )
        }

        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJsonList(json: JsonList): List<ModalPlacementSelector> {
            return json.map(::fromJson)
        }
    }
}
