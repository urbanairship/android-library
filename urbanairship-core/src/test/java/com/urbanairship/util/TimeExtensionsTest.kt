/* Copyright Airship and Contributors */
package com.urbanairship.util

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class TimeExtensionsTest {

    private companion object {
        private val INSTANT = Instant.parse("2026-08-24T12:00:00Z")
    }

    @Test
    public fun testFormatDateLongStyle() {
        // Full month name + day + year, matching FormatStyle.LONG.
        Assert.assertEquals(
            "August 24, 2026",
            INSTANT.formatDate(zoneId = ZoneId.of("UTC"), locale = Locale.US)
        )
    }

    @Test
    public fun testFormatDateUsesGivenLocale() {
        Assert.assertEquals(
            "24 août 2026",
            INSTANT.formatDate(zoneId = ZoneId.of("UTC"), locale = Locale.FRENCH)
        )
    }

    @Test
    public fun testFormatDateUsesGivenZoneId() {
        // Same instant, just before midnight UTC, renders as a different calendar date
        // depending on the time zone it's viewed in.
        val nearMidnight = Instant.parse("2026-08-24T23:30:00Z")

        Assert.assertEquals(
            "August 24, 2026",
            nearMidnight.formatDate(zoneId = ZoneId.of("UTC"), locale = Locale.US)
        )
        Assert.assertEquals(
            "August 25, 2026",
            nearMidnight.formatDate(zoneId = ZoneId.of("+02:00"), locale = Locale.US)
        )
    }

    @Test
    public fun testFormatDateUsesLocaleNativeDigits() {
        // Regression test: DateTimeFormatter defaults to ASCII digits regardless of locale
        // unless withDecimalStyle(DecimalStyle.of(locale)) is applied. Arabic and Persian
        // render digits in their native numbering systems.
        Assert.assertEquals(
            "٢٤ أغسطس ٢٠٢٦",
            INSTANT.formatDate(zoneId = ZoneId.of("UTC"), locale = Locale.forLanguageTag("ar"))
        )
        Assert.assertEquals(
            "۲۴ اوت ۲۰۲۶",
            INSTANT.formatDate(zoneId = ZoneId.of("UTC"), locale = Locale.forLanguageTag("fa"))
        )
    }
}
