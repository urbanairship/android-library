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
    public fun getResolvedPlacement(context: Context): EmbeddedPlacement =
        getResolvedPlacement(SelectorEnvironment.from(context))

    /**
     * Returns the first placement whose selector matches [context]'s environment and the layout's
     * environment [conditions], else the default. A root view's own context sits above the one its
     * views carry conditions in, so it passes them.
     */
    internal fun getResolvedPlacement(context: Context, conditions: JsonMap): EmbeddedPlacement =
        getResolvedPlacement(SelectorEnvironment.from(context).copy(conditions = conditions))

    private fun getResolvedPlacement(environment: SelectorEnvironment): EmbeddedPlacement =
        placementSelectors?.firstOrNull { it.conditions.matches(environment) }?.placement ?: defaultPlacement

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
