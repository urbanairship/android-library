/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import java.util.regex.Pattern

public object PercentUtils {

    private val PATTERN_NON_NUMERIC: Pattern = Pattern.compile("[^\\d.]")
    /** Any non-negative number followed by `%`, decimals included, as iOS and web read one. */
    private val PATTERN_PERCENT_W_SYMBOL: Pattern = Pattern.compile("^(\\d+(\\.\\d*)?|\\.\\d+)%$")

    /**
     * Returns whether [string] is a percentage, such as `50%` or `12.5%`.
     *
     * A string that isn't one is read as a length in points, so one this misses fails to parse.
     */
    public fun isPercent(string: String): Boolean {
        return PATTERN_PERCENT_W_SYMBOL.matcher(string).matches()
    }

    public fun parse(percentString: String): Float {
        val number = digits(percentString)
        return number.toFloat() / 100f
    }

    public fun digits(number: String): String {
        return PATTERN_NON_NUMERIC.matcher(number).replaceAll("")
    }
}
