/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

public class BannerPlacementSelector(
    @JvmField public val placement: BannerPlacement,
    /** What the environment has to match for [placement] to apply. */
    @JvmField public val conditions: SelectorConditions
) {

    public companion object {
        private const val KEY_PLACEMENT = "placement"

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): BannerPlacementSelector {
            val content = json.requireMap()

            return BannerPlacementSelector(
                placement = BannerPlacement.fromJson(content.require(KEY_PLACEMENT)),
                conditions = SelectorConditions.fromJson(content)
            )
        }

        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJsonList(json: JsonList): List<BannerPlacementSelector> {
            return json.map(BannerPlacementSelector::fromJson)
        }
    }
}
