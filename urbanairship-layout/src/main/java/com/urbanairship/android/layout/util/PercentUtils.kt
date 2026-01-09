/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import java.util.regex.Pattern

public object PercentUtils {

    private val PATTERN_NON_NUMERIC: Pattern = Pattern.compile("[^\\d.]")
    private val PATTERN_PERCENT_W_SYMBOL: Pattern = Pattern.compile("^\\d{1,3}%$")

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
