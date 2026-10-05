/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import java.util.regex.Pattern

public object PercentUtils {

    private val PATTERN_NON_NUMERIC: Pattern = Pattern.compile("[^\\d.]")

    /** Returns whether [string] is a percentage: anything ending in `%`, as iOS reads one. */
    public fun isPercent(string: String): Boolean = string.endsWith("%")

    /**
     * Returns [percentString] as a fraction, such as `0.125` for `12.5%`.
     *
     * The `%` is optional. A number that doesn't parse, such as in `%`, reads as `0`, as on iOS.
     *
     * @param percentString the percentage, with or without its `%`.
     * @return the percentage divided by 100.
     */
    public fun parse(percentString: String): Float =
        (percentString.removeSuffix("%").toFloatOrNull() ?: 0f) / 100f

    public fun digits(number: String): String {
        return PATTERN_NON_NUMERIC.matcher(number).replaceAll("")
    }
}
