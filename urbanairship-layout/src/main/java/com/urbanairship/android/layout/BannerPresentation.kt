/* Copyright Airship and Contributors */
package com.urbanairship.android.layout

import android.content.Context
import com.urbanairship.UALog
import com.urbanairship.android.layout.property.BannerPlacement
import com.urbanairship.android.layout.property.BannerPlacementSelector
import com.urbanairship.android.layout.property.PresentationType
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

public class BannerPresentation public constructor(
    public val defaultPlacement: BannerPlacement,
    /** Auto-dismiss duration, in milliseconds, or `null` if the banner should not auto-dismiss. */
    public val durationMs: Long?,
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
        private const val KEY_DURATION_SECONDS = "duration_seconds"
        private const val KEY_DURATION_MILLISECONDS = "duration_milliseconds"
        private const val KEY_PLACEMENT_SELECTORS = "placement_selectors"
        private const val KEY_DEFAULT_PLACEMENT = "default_placement"

        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): BannerPresentation {
            val content = json.requireMap()

            // Prefer duration_seconds, falling back to the legacy duration_milliseconds.
            // If neither is present (or the value is unusable), the banner will not auto-dismiss.
            val durationMs = content.optionalDurationMs(KEY_DURATION_SECONDS, msPerUnit = 1000.0)
                ?: content.optionalDurationMs(KEY_DURATION_MILLISECONDS, msPerUnit = 1.0)

            return BannerPresentation(
                defaultPlacement = BannerPlacement.fromJson(content.require(KEY_DEFAULT_PLACEMENT)),
                durationMs = durationMs,
                placementSelectors = content[KEY_PLACEMENT_SELECTORS]
                    ?.requireList()
                    ?.let(BannerPlacementSelector::fromJsonList))
        }

        /**
         * Returns the duration for [key], converted to milliseconds via [msPerUnit], or `null`
         * if the field is absent or unusable (non-numeric or not positive).
         */
        private fun JsonMap.optionalDurationMs(key: String, msPerUnit: Double): Long? {
            val value = this[key] ?: return null
            val durationMs = (value.getDouble(0.0) * msPerUnit).toLong()
            if (durationMs <= 0) {
                UALog.w { "Ignoring banner '$key'! Expected a positive number, got: $value" }
                return null
            }
            return durationMs
        }
    }
}
