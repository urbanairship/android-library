/* Copyright Airship and Contributors */

package com.urbanairship.util

import androidx.annotation.RestrictTo
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * Formatting utils.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object FormatterUtils {

    /**
     * Manages thread-safe instances of `DecimalFormat` for formatting to two decimal places.
     */
    private val secondsStringDecimalFormatter = object : ThreadLocal<DecimalFormat>() {
        override fun initialValue(): DecimalFormat {
            // This format ensures two decimal places, e.g., 5.1 becomes "5.10"
            return DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.ROOT))
        }
    }

    /**
     * Converts a `Duration` into a string representing the total number of seconds,
     * formatted to two decimal places (hundredths of a second).
     *
     * ### Example Usage:
     * ```
     * val duration1 = 10.5.seconds
     * println(duration1.toSecondsString()) // Prints "10.50"
     *
     * val duration2 = 75.129.seconds
     * println(duration2.toSecondsString()) // Prints "75.13" (rounds up)
     *
     * val duration3 = 2.minutes
     * println(duration3.toSecondsString()) // Prints "120.00"
     * ```
     *
     * @receiver The `Duration` instance to format.
     * @return A `String` representation of the total seconds, formatted to "0.00".
     */
    public fun Duration.toSecondsString(): String {
        // ThreadLocal is not annotated so even though it can never be null
        // it returns an optional. Using a checkNotNull to have a better error
        // instead of the !! operator.
        val formatter = checkNotNull(secondsStringDecimalFormatter.get()) {
            "DecimalFormat not available on this thread. This is unexpected."
        }

        return formatter.format(this.toDouble(DurationUnit.SECONDS))
    }
}
