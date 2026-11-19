/* Copyright Airship and Contributors */
package com.urbanairship.util

import android.util.Log
import com.urbanairship.AirshipLogHandler
import com.urbanairship.UALog
import com.urbanairship.util.DateUtils.createIso8601TimeStamp
import com.urbanairship.util.DateUtils.parseIso8601
import java.text.ParseException
import java.text.SimpleDateFormat
import org.junit.Assert
import org.junit.Test

public class DateUtilsTest {

    @Test(expected = ParseException::class)
    public fun testParseNullStringException() {
        parseIso8601(null)
    }

    @Test(expected = ParseException::class)
    public fun testParseEmptyStringException() {
        parseIso8601("")
    }

    @Test(expected = ParseException::class)
    public fun testParseInvalidStringException() {
        parseIso8601("wat")
    }

    @Test(expected = ParseException::class)
    public fun testParseTrailingGarbageRejected() {
        // The entire string must be consumed.
        parseIso8601("2015-04-01T12:00:00 trailing")
    }

    @Test
    public fun testParseInvalidStringDefaultValue() {
        Assert.assertEquals(-1, parseIso8601("wat", -1))
        Assert.assertEquals(-2, parseIso8601("", -2))
        Assert.assertEquals(-3, parseIso8601(null, -3))
    }

    @Test
    public fun testParseFullTimestamp() {
        Assert.assertEquals(1427889600000L, parseIso8601("2015-04-01T12:00:00"))
        Assert.assertEquals(1427889600000L, parseIso8601("2015-04-01 12:00:00"))
    }

    @Test
    public fun testParseFractionalSeconds() {
        // ISO 8601: the fractional part is a decimal fraction of a second.
        // .5 / .50 / .500 all mean 500ms, not 5/50/500ms-as-int.
        Assert.assertEquals(1689012646500L, parseIso8601("2023-07-10T18:10:46.5"))
        Assert.assertEquals(1689012646500L, parseIso8601("2023-07-10T18:10:46.50"))
        Assert.assertEquals(1689012646500L, parseIso8601("2023-07-10T18:10:46.500"))
        // .203 -> 203ms past the second
        Assert.assertEquals(1689012646203L, parseIso8601("2023-07-10T18:10:46.203"))
        // .12 -> 120ms past the second
        Assert.assertEquals(1689012646120L, parseIso8601("2023-07-10T18:10:46.12"))
        // .00 / .000 -> exactly on the second
        Assert.assertEquals(1672308930000L, parseIso8601("2022-12-29T10:15:30.00"))
        Assert.assertEquals(1672308930000L, parseIso8601("2022-12-29T10:15:30.000"))
    }

    @Test
    public fun testParseZuluSuffix() {
        Assert.assertEquals(1427889600000L, parseIso8601("2015-04-01T12:00:00Z"))
        Assert.assertEquals(1689012646203L, parseIso8601("2023-07-10T18:10:46.203Z"))
    }

    @Test
    public fun testParseZoneOffset() {
        // 12:00 UTC == 07:00 EST
        Assert.assertEquals(1427889600000L, parseIso8601("2015-04-01T07:00:00-05:00"))
        Assert.assertEquals(1427889600000L, parseIso8601("2015-04-01T07:00:00-0500"))
        // 12:00 UTC == 17:30 IST (+05:30)
        Assert.assertEquals(1427889600000L, parseIso8601("2015-04-01T17:30:00+05:30"))
        // A zero offset, both spellings.
        Assert.assertEquals(1427889600000L, parseIso8601("2015-04-01T12:00:00+00:00"))
        Assert.assertEquals(1427889600000L, parseIso8601("2015-04-01T12:00:00+0000"))
        // Fractional seconds alongside an offset.
        Assert.assertEquals(1689012646203L, parseIso8601("2023-07-10T13:10:46.203-05:00"))
        Assert.assertEquals(1689012646203L, parseIso8601("2023-07-10T13:10:46.203-0500"))
    }

    @Test
    public fun testParseRejectsNamedZoneSuffix() {
        // SimpleDateFormat's `Z` falls back to parsing a zone name when the text is not an
        // offset, which would make these accepted ISO 8601 timestamps.
        Assert.assertEquals(-1, parseIso8601("2015-04-01T12:00:00GMT", -1))
        Assert.assertEquals(-1, parseIso8601("2015-04-01T12:00:00UTC", -1))
        Assert.assertEquals(-1, parseIso8601("2015-04-01T12:00:00PST", -1))
        // The zone designator is still required to be well-formed.
        Assert.assertEquals(-1, parseIso8601("2015-04-01T12:00:00+05", -1))
    }

    /**
     * `parseRetryAfter` tries [parseIso8601] before `parseHttpDate`, so an HTTP-date must not
     * be parseable as ISO 8601 or the wrong instant wins.
     */
    @Test
    public fun testParseIso8601RejectsHttpDate() {
        Assert.assertEquals(-1, parseIso8601("Mon, 10 Jul 2023 18:10:46 GMT", -1))
        Assert.assertEquals(-1, parseIso8601("Monday, 10-Jul-23 18:10:46 GMT", -1))
        Assert.assertEquals(-1, parseIso8601("Mon Jul 10 18:10:46 2023", -1))
    }

    @Test
    public fun testParsePartialTime() {
        // Hour only
        Assert.assertEquals(1427889600000L, parseIso8601("2015-04-01T12"))
        Assert.assertEquals(1427889600000L, parseIso8601("2015-04-01 12"))
        // Hour + minute
        Assert.assertEquals(1427889600000L, parseIso8601("2015-04-01T12:00"))
    }

    @Test
    public fun testParseDateOnly() {
        Assert.assertEquals(1427846400000L, parseIso8601("2015-04-01"))
    }

    @Test
    public fun testParseYearAndMonth() {
        Assert.assertEquals(1427846400000L, parseIso8601("2015-04-01"))
        Assert.assertEquals(1427846400000L, parseIso8601("2015-04-01T00:00:00"))
        Assert.assertEquals(1425168000000L, parseIso8601("2015-03"))
        Assert.assertEquals(1420070400000L, parseIso8601("2015"))
    }

    @Test
    public fun testParseHttpDateImfFixdate() {
        // RFC 7231 §7.1.1.1 IMF-fixdate (preferred form)
        Assert.assertEquals(1689012646000L, DateUtils.parseHttpDate("Mon, 10 Jul 2023 18:10:46 GMT"))
    }

    @Test
    public fun testParseHttpDateRfc850() {
        // RFC 850 obsolete form, still supported per RFC 7231
        Assert.assertEquals(1689012646000L, DateUtils.parseHttpDate("Monday, 10-Jul-23 18:10:46 GMT"))
    }

    @Test
    public fun testParseHttpDateAsctime() {
        // asctime obsolete form, still supported per RFC 7231
        Assert.assertEquals(1689012646000L, DateUtils.parseHttpDate("Mon Jul 10 18:10:46 2023"))
    }

    @Test(expected = ParseException::class)
    public fun testParseHttpDateRejectsIso8601() {
        DateUtils.parseHttpDate("2023-07-10T18:10:46Z")
    }

    @Test
    public fun testCreateTimeStamp() {
        Assert.assertEquals("2015-04-01T12:00:00Z", createIso8601TimeStamp(1427889600000L))
        Assert.assertEquals("1970-01-01T00:00:00Z", createIso8601TimeStamp(0))
        Assert.assertEquals("1969-12-31T23:59:59Z", createIso8601TimeStamp(-1L))
    }

    @Test
    public fun testCreateTimeStampWithMillis() {
        Assert.assertEquals(
            "2023-07-10T18:10:46.203Z",
            createIso8601TimeStamp(1689012646203L, includeMillis = true)
        )
        Assert.assertEquals(
            "1970-01-01T00:00:00.000Z",
            createIso8601TimeStamp(0L, includeMillis = true)
        )
        // Round-trip: parse -> format with millis preserves precision.
        val original = "2024-01-15T08:30:45.123Z"
        Assert.assertEquals(original, createIso8601TimeStamp(parseIso8601(original), includeMillis = true))
    }

    @Test
    public fun testCreateTimeStampNoMillisDefault() {
        // Default omits millis even when the input has sub-second precision.
        Assert.assertEquals("2023-07-10T18:10:46Z", createIso8601TimeStamp(1689012646203L))
    }

    /**
     * Guards every declared pattern against a letter the minimum API level rejects.
     *
     * `SimpleDateFormat` accepts a different set of letters per API level and throws on one it
     * does not know, which fails this object's initializer. The JVM accepts `X`, `Y` and `u`
     * where API 23 does not, so building each pattern here would pass with a pattern that is
     * fatal on device — only the letters themselves can be checked.
     */
    @Test
    public fun testEveryDeclaredPatternUsesSupportedLetters() {
        val declared = DateUtils.ISO_8601_PATTERNS + DateUtils.HTTP_DATE_PATTERNS +
                listOf(DateUtils.OUTPUT_PATTERN, DateUtils.OUTPUT_PATTERN_MILLIS)

        for (pattern in declared) {
            var literal = false
            for (character in pattern) {
                if (character == '\'') {
                    literal = !literal
                    continue
                }
                if (literal || !character.isLetter()) {
                    continue
                }
                Assert.assertTrue(
                    "Pattern \"$pattern\" uses '$character', which SimpleDateFormat rejects below API 24",
                    character in SUPPORTED_PATTERN_LETTERS
                )
            }
        }
    }

    /**
     * An ISO 8601 parse pattern must end in a field rather than a literal. [parseIso8601]
     * rejects an input whose normalized form ends in a letter, so a pattern ending in a
     * quoted literal could never be reached. The HTTP-date patterns are exempt: that path
     * neither normalizes nor pre-screens its input.
     */
    @Test
    public fun testIso8601ParsePatternsEndInAField() {
        for (pattern in DateUtils.ISO_8601_PATTERNS) {
            Assert.assertFalse(
                "Parse pattern \"$pattern\" ends in a literal, so no input can reach it",
                pattern.endsWith("'")
            )
        }
    }

    @Test
    public fun testEveryDeclaredPatternBuilds() {
        Assert.assertEquals(
            DateUtils.ISO_8601_PATTERNS,
            DateUtils.formats(DateUtils.ISO_8601_PATTERNS).map { it.toPattern() }
        )
        Assert.assertEquals(
            DateUtils.HTTP_DATE_PATTERNS,
            DateUtils.formats(DateUtils.HTTP_DATE_PATTERNS).map { it.toPattern() }
        )
    }

    /**
     * [DateUtils.formats] is the only way the parse lists are built, so driving it with the
     * declared patterns is what pins their leniency: a pattern the platform rejects is dropped
     * and the rest still build.
     */
    @Test
    public fun testRejectedPatternIsSkippedNotThrown() {
        // `b` is not a SimpleDateFormat pattern letter on any API level.
        for (patterns in listOf(DateUtils.ISO_8601_PATTERNS, DateUtils.HTTP_DATE_PATTERNS)) {
            Assert.assertEquals(
                patterns,
                DateUtils.formats(patterns + "bbbb").map { it.toPattern() }
            )
        }
    }

    /**
     * Logging a skipped pattern must not throw either. [UALog.logHandler] is public and the
     * handler is invoked unguarded, so an integrator's handler can throw where [DateUtils]
     * builds its formats — during class initialization, where nothing may escape.
     */
    @Test
    public fun testThrowingLogHandlerDoesNotEscape() {
        val handler = UALog.logHandler
        val level = UALog.logLevel
        UALog.logHandler = AirshipLogHandler { _, _, _, _ -> throw RuntimeException("handler") }
        UALog.logLevel = Log.VERBOSE
        try {
            Assert.assertEquals(emptyList<SimpleDateFormat>(), DateUtils.formats(listOf("bbbb")))
        } finally {
            UALog.logHandler = handler
            UALog.logLevel = level
        }
    }

    private companion object {

        /**
         * Pattern letters `SimpleDateFormat` accepts from API 23 up — the intersection of the
         * Apache Harmony set used below API 24 and the OpenJDK set used from API 24. `X`, `Y`
         * and `u` are OpenJDK-only and throw on an older device.
         */
        private val SUPPORTED_PATTERN_LETTERS = "DEFGHKLMSWZadhkmswyz".toSet()
    }
}
