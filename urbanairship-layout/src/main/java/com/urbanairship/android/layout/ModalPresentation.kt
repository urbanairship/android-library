/* Copyright Airship and Contributors */
package com.urbanairship.android.layout

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.property.ModalPlacement
import com.urbanairship.android.layout.property.ModalPlacementSelector
import com.urbanairship.android.layout.property.PresentationType
import com.urbanairship.android.layout.property.SelectorEnvironment
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

/**
 * Modal presentation info.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ModalPresentation public constructor(
    public val defaultPlacement: ModalPlacement,
    public val placementSelectors: List<ModalPlacementSelector>?,
    public val dismissOnTouchOutside: Boolean,
    public val disableBackButton: Boolean
) : BasePresentation(PresentationType.MODAL) {

    /** Returns the first placement whose selector [context]'s environment matches, else the default. */
    public fun getResolvedPlacement(context: Context): ModalPlacement =
        getResolvedPlacement(SelectorEnvironment.from(context))

    /**
     * Returns the first placement whose selector matches [context]'s environment and the layout's
     * environment [conditions], else the default. A root view's own context sits above the one its
     * views carry conditions in, so it passes them.
     */
    internal fun getResolvedPlacement(context: Context, conditions: JsonMap): ModalPlacement =
        getResolvedPlacement(SelectorEnvironment.from(context).copy(conditions = conditions))

    private fun getResolvedPlacement(environment: SelectorEnvironment): ModalPlacement =
        placementSelectors?.firstOrNull { it.conditions.matches(environment) }?.placement ?: defaultPlacement

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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
