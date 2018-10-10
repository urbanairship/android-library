/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.util;

import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;

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
    @NonNull
    public static String convertToString(@ColorInt int color) {
        StringBuilder hex = new StringBuilder("#");
        hex.append(Integer.toHexString(color));

        while (hex.length() < 9) {
            hex.append("0");
        }

        return hex.toString();
    }
}
