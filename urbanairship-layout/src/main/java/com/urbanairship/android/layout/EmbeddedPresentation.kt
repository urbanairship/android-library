/* Copyright Airship and Contributors */
package com.urbanairship.android.layout

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.property.EmbeddedPlacement
import com.urbanairship.android.layout.property.EmbeddedPlacementSelector
import com.urbanairship.android.layout.property.PresentationType
import com.urbanairship.android.layout.property.SelectorEnvironment
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalList
import com.urbanairship.json.requireField

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class EmbeddedPresentation(
    private val defaultPlacement: EmbeddedPlacement,
    private val placementSelectors: List<EmbeddedPlacementSelector>?,
    public val embeddedId: String
) : BasePresentation(PresentationType.EMBEDDED) {

    /** Returns the first placement whose selector [context]'s environment matches, else the default. */
    public fun getResolvedPlacement(context: Context): EmbeddedPlacement {
        val environment = SelectorEnvironment.from(context)
        return placementSelectors?.firstOrNull { it.conditions.matches(environment) }?.placement ?: defaultPlacement
    }

    internal companion object {
        private const val KEY_EMBEDDED_ID = "embedded_id"
        private const val KEY_DEFAULT_PLACEMENT = "default_placement"
        private const val KEY_PLACEMENT_SELECTORS = "placement_selectors"

        @Throws(JsonException::class)
        fun fromJson(json: JsonValue): EmbeddedPresentation {
            val content = json.requireMap()

            return EmbeddedPresentation(
                defaultPlacement = EmbeddedPlacement.fromJson(content.require(KEY_DEFAULT_PLACEMENT)),
                placementSelectors = content
                    .optionalList(KEY_PLACEMENT_SELECTORS)
                    ?.let(EmbeddedPlacementSelector::fromJsonList),
                embeddedId = content.requireField(KEY_EMBEDDED_ID)
            )
        }
    }
}
