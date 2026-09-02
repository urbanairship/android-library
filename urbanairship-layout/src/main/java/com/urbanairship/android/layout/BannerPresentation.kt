/* Copyright Airship and Contributors */
package com.urbanairship.android.layout

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.android.layout.property.BannerPlacement
import com.urbanairship.android.layout.property.BannerPlacementSelector
import com.urbanairship.android.layout.property.PresentationType
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Banner presentation info.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class BannerPresentation public constructor(
    public val defaultPlacement: BannerPlacement,
    /** Auto-dismiss duration, or `null` if the banner should not auto-dismiss. */
    public val duration: Duration?,
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

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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
            val duration = content.optionalDuration(KEY_DURATION_SECONDS, DurationUnit.SECONDS)
                ?: content.optionalDuration(KEY_DURATION_MILLISECONDS, DurationUnit.MILLISECONDS)

            return BannerPresentation(
                defaultPlacement = BannerPlacement.fromJson(content.require(KEY_DEFAULT_PLACEMENT)),
                duration = duration,
                placementSelectors = content[KEY_PLACEMENT_SELECTORS]
                    ?.requireList()
                    ?.let(BannerPlacementSelector::fromJsonList))
        }

        /**
         * Returns the duration for [key], interpreting the stored number in [unit], or `null`
         * if the field is absent or unusable (non-numeric, or under a millisecond).
         *
         * Deliberately lenient: a bad duration means "do not auto-dismiss" rather than a failure
         * to parse the whole layout.
         */
        private fun JsonMap.optionalDuration(key: String, unit: DurationUnit): Duration? {
            val value = this[key] ?: return null
            // takeIf { isFinite() }: toDuration throws on NaN, and an infinite duration is not a
            // meaningful auto-dismiss. The floor is a millisecond rather than zero because the
            // value used to be truncated to whole milliseconds before being checked, so a
            // sub-millisecond duration read as unusable and fell through to the other key.
            val duration = value.getDouble(0.0).takeIf { it.isFinite() }?.toDuration(unit)
            if (duration == null || duration < 1.milliseconds) {
                UALog.w { "Ignoring banner '$key'! Expected a positive number, got: $value" }
                return null
            }
            return duration
        }
    }
}
