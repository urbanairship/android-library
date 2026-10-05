/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.environment.ThomasEnvironment
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalMap
import com.urbanairship.json.optionalField

/**
 * What every selector can match: placement, color and media URL selectors alike.
 *
 * Every field a selector names must hold, a field it leaves out matches anything, and the first
 * selector in a list that matches wins. All of them are values that change rarely; no selector
 * measures a size.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class SelectorConditions(
    val platform: Platform? = null,
    val darkMode: Boolean? = null,
    val windowSize: WindowSize? = null,
    val orientation: Orientation? = null,
    /**
     * Environment conditions' chosen values, each of which must equal the one given:
     * `{size: compact}` matches while the scene's `size` condition has chosen `compact`.
     */
    val environmentConditions: Map<String, JsonValue>? = null
) {

    /**
     * Returns whether every field these conditions name holds in [environment].
     *
     * @param environment what the scene is shown in.
     * @return true where every named field holds.
     */
    internal fun matches(environment: SelectorEnvironment): Boolean =
        (platform == null || platform == environment.platform) &&
            (darkMode == null || darkMode == environment.darkMode) &&
            (windowSize == null || windowSize == environment.windowSize) &&
            (orientation == null || orientation == environment.orientation) &&
            environmentConditions.orEmpty().all { (name, value) ->
                environment.conditions.containsKey(name) && environment.conditions.opt(name) == value
            }

    internal companion object {
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): SelectorConditions = SelectorConditions(
            platform = json["platform"]?.let(Platform::from),
            darkMode = json.optionalField("dark_mode"),
            windowSize = json["window_size"]?.let(WindowSize::from),
            orientation = json["orientation"]?.let(Orientation::from),
            environmentConditions = json.optionalMap("environment_conditions")?.map
        )
    }
}

/**
 * The values selectors are matched against.
 *
 * @param windowSize the window's size class, or null where the configuration doesn't give one, so
 * a selector naming one doesn't match.
 * @param orientation the device's orientation, under the same rule as [windowSize].
 * @param conditions the scene's environment conditions' chosen values; one with none chosen is
 * left out, so a selector naming it doesn't match.
 */
internal data class SelectorEnvironment(
    val darkMode: Boolean,
    val windowSize: WindowSize?,
    val orientation: Orientation?,
    val conditions: JsonMap = JsonMap.EMPTY_MAP,
    val platform: Platform = Platform.ANDROID
) {
    companion object {
        /**
         * Reads the environment from [context]'s configuration, and the environment conditions of
         * the layout [context] belongs to; see [EnvironmentConditionsContext].
         */
        fun from(context: Context): SelectorEnvironment = SelectorEnvironment(
            darkMode = ResourceUtils.isUiModeNight(context),
            windowSize = ResourceUtils.getWindowSize(context),
            orientation = ResourceUtils.getOrientation(context),
            conditions = EnvironmentConditionsContext.conditionsOf(context)
        )

        /** Reads what environment conditions match on from the measured [environment]. */
        fun from(environment: ThomasEnvironment): SelectorEnvironment = SelectorEnvironment(
            darkMode = environment.darkMode,
            windowSize = environment.windowSize,
            orientation = environment.orientation
        )
    }
}
