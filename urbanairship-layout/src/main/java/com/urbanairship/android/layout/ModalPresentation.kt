/* Copyright Airship and Contributors */
package com.urbanairship.android.layout

import android.content.Context
import com.urbanairship.android.layout.property.ModalPlacement
import com.urbanairship.android.layout.property.ModalPlacementSelector
import com.urbanairship.android.layout.property.PresentationType
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

public class ModalPresentation public constructor(
    public val defaultPlacement: ModalPlacement,
    public val placementSelectors: List<ModalPlacementSelector>?,
    public val dismissOnTouchOutside: Boolean,
    public val disableBackButton: Boolean
) : BasePresentation(PresentationType.MODAL) {

    public fun getResolvedPlacement(context: Context): ModalPlacement {
        if (placementSelectors == null || placementSelectors.isEmpty()) {
            return defaultPlacement
        }

        val orientation = ResourceUtils.getOrientation(context)
        val windowSize = ResourceUtils.getWindowSize(context)

        // Try to find a matching placement selector.
        return placementSelectors.firstOrNull {
                (it.windowSize == null || it.windowSize == windowSize) &&
                (it.orientation == null || it.orientation == orientation)
            }?.placement
            // Otherwise, return the default placement.
            ?: defaultPlacement
    }

    public companion object {
        private const val KEY_PLACEMENT_SELECTORS = "placement_selectors"
        private const val KEY_DEFAULT_PLACEMENT = "default_placement"
        private const val KEY_DISMISS_ON_TOUCH_OUTSIDE = "dismiss_on_touch_outside"
        private const val KEY_DISABLE_BACK_BUTTON = "disable_back_button"

        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): ModalPresentation {
            val content = json.requireMap()

            return ModalPresentation(
                defaultPlacement = ModalPlacement.fromJson(content.require(KEY_DEFAULT_PLACEMENT)),
                placementSelectors = content[KEY_PLACEMENT_SELECTORS]
                    ?.requireList()
                    ?.let(ModalPlacementSelector::fromJsonList),
                dismissOnTouchOutside = content.opt(KEY_DISMISS_ON_TOUCH_OUTSIDE).getBoolean(false),
                disableBackButton = content.opt(KEY_DISABLE_BACK_BUTTON).getBoolean(false)
            )
        }
    }
}
