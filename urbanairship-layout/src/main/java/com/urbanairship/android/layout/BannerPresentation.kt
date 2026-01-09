/* Copyright Airship and Contributors */
package com.urbanairship.android.layout

import android.content.Context
import com.urbanairship.android.layout.property.BannerPlacement
import com.urbanairship.android.layout.property.BannerPlacementSelector
import com.urbanairship.android.layout.property.PresentationType
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

public class BannerPresentation public constructor(
    public val defaultPlacement: BannerPlacement,
    public val durationMs: Int,
    public val placementSelectors: List<BannerPlacementSelector>?
) : BasePresentation(PresentationType.BANNER) {

    public fun getResolvedPlacement(context: Context): BannerPlacement {
        if (placementSelectors == null || placementSelectors.isEmpty()) {
            return defaultPlacement
        }

        val orientation = ResourceUtils.getOrientation(context)
        val windowSize = ResourceUtils.getWindowSize(context)

        // Try to find a matching placement selector.
        return placementSelectors.firstOrNull {
                (it.windowSize == null || it.windowSize == windowSize) &&
                (it.orientation == null || it.orientation == orientation)
            }
            ?.placement
        // Otherwise, return the default placement.
            ?: defaultPlacement
    }

    public companion object {
        private const val KEY_DURATION_MILLISECONDS = "duration_milliseconds"
        private const val KEY_PLACEMENT_SELECTORS = "placement_selectors"
        private const val KEY_DEFAULT_PLACEMENT = "default_placement"

        private const val DEFAULT_DURATION = 7000

        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): BannerPresentation {
            val content = json.requireMap()

            return BannerPresentation(
                defaultPlacement = BannerPlacement.fromJson(content.require(KEY_DEFAULT_PLACEMENT)),
                durationMs = content.opt(KEY_DURATION_MILLISECONDS).getInt(DEFAULT_DURATION),
                placementSelectors = content[KEY_PLACEMENT_SELECTORS]
                    ?.requireList()
                    ?.let(BannerPlacementSelector::fromJsonList))
        }
    }
}
