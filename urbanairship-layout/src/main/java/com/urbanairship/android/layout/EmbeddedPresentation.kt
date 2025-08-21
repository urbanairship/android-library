/* Copyright Airship and Contributors */
package com.urbanairship.android.layout

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.property.EmbeddedPlacement
import com.urbanairship.android.layout.property.EmbeddedPlacementSelector
import com.urbanairship.android.layout.property.PresentationType
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class EmbeddedPresentation(
    private val defaultPlacement: EmbeddedPlacement,
    private val placementSelectors: List<EmbeddedPlacementSelector>?,
    public val embeddedId: String
) : BasePresentation(PresentationType.EMBEDDED) {

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun getResolvedPlacement(context: Context): EmbeddedPlacement {
        if (placementSelectors.isNullOrEmpty()) {
            return defaultPlacement
        }
        val orientation = ResourceUtils.getOrientation(context)
        val windowSize = ResourceUtils.getWindowSize(context)

        // Try to find a matching placement selector.
        for (selector in placementSelectors) {
            if (selector.windowSize != null && selector.windowSize != windowSize) {
                continue
            }
            if (selector.orientation != null && selector.orientation != orientation) {
                continue
            }
            return selector.placement
        }

        // Otherwise, return the default placement.
        return defaultPlacement
    }

    internal companion object {
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): EmbeddedPresentation {
            val embeddedId = json.opt("embedded_id").string ?: throw JsonException(
                "Failed to parse EmbeddedPresentation! Field 'embedded_id' is required."
            )
            val defaultPlacementJson = json.opt("default_placement").map ?: throw JsonException(
                "Failed to parse EmbeddedPresentation! Field 'default_placement' is required."
            )
            val placementSelectorsJson = json.opt("placement_selectors").list

            val defaultPlacement = EmbeddedPlacement.fromJson(defaultPlacementJson)
            val placementSelectors = placementSelectorsJson?.let {
                EmbeddedPlacementSelector.fromJsonList(it)
            }

            return EmbeddedPresentation(defaultPlacement, placementSelectors, embeddedId)
        }
    }
}
