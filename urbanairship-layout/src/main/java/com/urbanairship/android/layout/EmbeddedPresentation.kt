/* Copyright Airship and Contributors */
package com.urbanairship.android.layout

import android.content.Context
import com.urbanairship.android.layout.property.ModalPlacement
import com.urbanairship.android.layout.property.ModalPlacementSelector
import com.urbanairship.android.layout.property.PresentationType
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap

internal class EmbeddedPresentation(
    // TODO: embedded placement and embedded placement selector
    private val defaultPlacement: ModalPlacement,
    private val placementSelectors: List<ModalPlacementSelector>?,
    internal val embeddedId: String
) : BasePresentation(PresentationType.EMBEDDED) {

    fun getResolvedPlacement(context: Context): ModalPlacement {
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

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): EmbeddedPresentation {
            val embeddedId = json.opt("embedded_id").optString()

            val defaultPlacementJson = json.opt("default_placement").optMap()
            if (defaultPlacementJson.isEmpty) {
                throw JsonException("Failed to parse EmbeddedPresentation! Field 'default_placement' is required.")
            }
            val placementSelectorsJson = json.opt("placement_selectors").optList()
            // TODO(embedded): embedded placement!
            val defaultPlacement = ModalPlacement.fromJson(defaultPlacementJson)
            val placementSelectors =
                // TODO(embedded): embedded placement selectors!
                if (placementSelectorsJson.isEmpty) null else ModalPlacementSelector.fromJsonList(
                    placementSelectorsJson
                )

            return EmbeddedPresentation(
                defaultPlacement,
                placementSelectors,
                embeddedId
            )
        }
    }
}
