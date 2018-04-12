/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.util;

import android.support.annotation.ColorInt;

/**
 * Color utility methods.
 */
public class ColorUtils {

    /**
     * Converts a color int to a #AARRGGBB color format string.
     *
     * @param color The color to covert.
     * @return The color string.
     */
    public static String convertToString(@ColorInt int color) {
        String hex = Integer.toHexString(color);
        while (hex.length() < 8) {
            hex = hex + "0";
        }

        return "#" + hex;
    }
}
