/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
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
    val orientation: Orientation? = null
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
            (orientation == null || orientation == environment.orientation)

    internal companion object {
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): SelectorConditions = SelectorConditions(
            platform = json["platform"]?.let(Platform::from),
            darkMode = json.optionalField("dark_mode"),
            windowSize = json["window_size"]?.let(WindowSize::from),
            orientation = json["orientation"]?.let(Orientation::from)
        )
    }
}

/**
 * The values selectors are matched against.
 *
 * @param windowSize the window's size class, or null where the configuration doesn't give one, so
 * a selector naming one doesn't match.
 * @param orientation the device's orientation, under the same rule as [windowSize].
 */
internal data class SelectorEnvironment(
    val darkMode: Boolean,
    val windowSize: WindowSize?,
    val orientation: Orientation?,
    val platform: Platform = Platform.ANDROID
) {
    companion object {
        /** Reads the environment from [context]'s configuration. */
        fun from(context: Context): SelectorEnvironment = SelectorEnvironment(
            darkMode = ResourceUtils.isUiModeNight(context),
            windowSize = ResourceUtils.getWindowSize(context),
            orientation = ResourceUtils.getOrientation(context)
        )
    }
}
