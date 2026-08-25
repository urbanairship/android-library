/* Copyright Airship and Contributors */

package com.urbanairship.util

import androidx.annotation.RestrictTo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import java.time.Duration as JavaDuration

/**
 * Interop between [Instant] and [kotlin.time.Duration], date formatting, and related helpers.
 *
 * [Instant] only speaks `java.time.Duration`, so without these every bit of time arithmetic
 * has to round-trip through epoch millis. These keep call sites in the same units the rest
 * of the SDK uses.
 *
 * @hide
 */

/**
 * The [Duration] elapsed from [other] to this instant. Negative if [other] is later.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public operator fun Instant.minus(other: Instant): Duration =
    JavaDuration.between(other, this).toKotlinDuration()

/**
 * This instant advanced by [duration].
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public operator fun Instant.plus(duration: Duration): Instant =
    plus(duration.toJavaDuration())

/**
 * This instant moved back by [duration].
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public operator fun Instant.minus(duration: Duration): Instant =
    minus(duration.toJavaDuration())

private val LONG_DATE_FORMATTER = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)

/**
 * Formats this instant as a localized, human-readable date string.
 *
 * e.g. `August 24, 2026` (for en-US).
 *
 * [locale] defaults to [Locale.getDefault] evaluated per call, not cached alongside our formatter,
 * since the system/app locale can change at runtime without the process restarting.
 *
 * @param zoneId The time zone to render the date in. Defaults to the device's local time zone.
 * @param locale The locale to format the date with. Defaults to the current default locale.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun Instant.formatDate(
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): String {
    return LONG_DATE_FORMATTER
        .withLocale(locale)
        .withDecimalStyle(DecimalStyle.of(locale))
        .format(this.atZone(zoneId))
}
