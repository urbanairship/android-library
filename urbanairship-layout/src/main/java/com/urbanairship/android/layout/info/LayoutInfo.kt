package com.urbanairship.android.layout.info

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.BannerPresentation
import com.urbanairship.android.layout.BasePresentation
import com.urbanairship.android.layout.EmbeddedPresentation
import com.urbanairship.android.layout.info.ViewInfo.Companion.viewInfoFromJson
import com.urbanairship.android.layout.property.Conditions
import com.urbanairship.android.layout.property.EnvironmentConditionSelector
import com.urbanairship.android.layout.property.conditionsIn
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class LayoutInfo(
    public val version: Int,
    public val presentation: BasePresentation,
    public val view: ViewInfo,
    public val hash: Int,
    public val options: NativeControlOptions? = null,
    /** What this layout says about itself, read when something has to choose between layouts. */
    public val contentDescription: ThomasContentDescriptionInfo? = null,
    /** The scene's `environment_state.conditions`, chosen at `$environment.conditions`. */
    public val environmentConditions: Conditions<EnvironmentConditionSelector> = Conditions(emptyMap()),
    /** Whether a color or media selector in [view] names `environment_conditions`. */
    public val viewSelectsOnEnvironmentConditions: Boolean = false
) {
    public constructor(json: JsonMap) : this(
        version = json.requireField("version"),
        presentation = BasePresentation.fromJson(json.requireField("presentation")),
        view = viewInfoFromJson(json.requireField("view")),
        hash = json.hashCode(),
        options = json["options"]?.let(NativeControlOptions::fromJson),
        contentDescription = json.optionalMap("content_description")
            ?.let(ThomasContentDescriptionInfo::fromJson),
        environmentConditions = json.conditionsIn("environment_state", EnvironmentConditionSelector::fromJson),
        viewSelectsOnEnvironmentConditions = json.opt("view").namesKey("environment_conditions")
    )

    public val isEmbedded: Boolean
        get() = presentation is EmbeddedPresentation

    public val isBanner: Boolean
        get() = presentation is BannerPresentation

    public val embeddedViewId: String?
        get() = (presentation as? EmbeddedPresentation)?.embeddedId
}

/** Whether [key] is a key anywhere in this value, at any depth. */
private fun JsonValue.namesKey(key: String): Boolean = when {
    isJsonMap -> optMap().map.any { (name, value) -> name == key || value.namesKey(key) }
    isJsonList -> optList().list.any { it.namesKey(key) }
    else -> false
}
