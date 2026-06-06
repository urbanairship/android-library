/* Copyright Airship and Contributors */
package com.urbanairship.util

import com.urbanairship.util.DateUtils.createIso8601TimeStamp
import com.urbanairship.util.DateUtils.parseIso8601
import java.text.ParseException
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
}
