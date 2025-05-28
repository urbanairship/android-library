/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap

public class EmbeddedPlacementSelector(
    public val placement: EmbeddedPlacement,
    public val windowSize: WindowSize?,
    public val orientation: Orientation?
) {
    public companion object {
        @Throws(JsonException::class)
        public fun fromJson(json: JsonMap): EmbeddedPlacementSelector {
            val placementJson = json.opt("placement").optMap()
            val windowSizeString = json.opt("window_size").optString()
            val orientationString = json.opt("orientation").optString()

            val placement = EmbeddedPlacement.fromJson(placementJson)
            val windowSize = if (windowSizeString.isEmpty()) null else WindowSize.from(windowSizeString)
            val orientation = if (orientationString.isEmpty()) null else Orientation.from(orientationString)

            return EmbeddedPlacementSelector(placement, windowSize, orientation)
        }

        @Throws(JsonException::class)
        public fun fromJsonList(json: JsonList): List<EmbeddedPlacementSelector> =
            json.mapNotNull { fromJson(it.optMap()) }
    }
}
