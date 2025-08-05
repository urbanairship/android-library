/* Copyright Airship and Contributors */
package com.urbanairship.util

import androidx.annotation.ColorInt
import androidx.annotation.RestrictTo

/**
 * Color utility methods.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object ColorUtils {

    /**
     * Converts a color int to a #AARRGGBB color format string.
     *
     * @param color The color to covert.
     * @return The color string.
     */
    public fun convertToString(@ColorInt color: Int): String {
        val hex = StringBuilder("#")
        hex.append(Integer.toHexString(color))

        while (hex.length < 9) {
            hex.append("0")
        }

        return hex.toString()
    }
}
