/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonValue

public class ModalPlacementSelector public constructor(
    @JvmField public val placement: ModalPlacement,
    @JvmField public val windowSize: WindowSize?,
    @JvmField public val orientation: Orientation?
) {

    public companion object {
        private const val KEY_PLACEMENT = "placement"
        private const val KEY_WINDOW_SIZE = "window_size"
        private const val KEY_ORIENTATION = "orientation"

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): ModalPlacementSelector {
            val content = json.requireMap()

            return ModalPlacementSelector(
                placement = ModalPlacement.fromJson(content.require(KEY_PLACEMENT)),
                windowSize = content[KEY_WINDOW_SIZE]?.let(WindowSize::from),
                orientation = content[KEY_ORIENTATION]?.let(Orientation::from)
            )
        }

        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJsonList(json: JsonList): List<ModalPlacementSelector> {
            return json.map(::fromJson)
        }
    }
}
