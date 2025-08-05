/* Copyright Airship and Contributors */
package com.urbanairship.util

import com.urbanairship.util.DateUtils.createIso8601TimeStamp
import com.urbanairship.util.DateUtils.parseIso8601
import java.text.ParseException
import org.junit.Assert
import org.junit.Test

class DateUtilsTest {

    @Test(expected = ParseException::class)
    fun testParseNullStringException() {
        Assert.assertEquals(-1, parseIso8601(null))
    }

    @Test(expected = ParseException::class)
    fun testParseInvalidStringException() {
        Assert.assertEquals(-1, parseIso8601("wat"))
    }

    @Test
    fun testParseInvalidStringDefaultValue() {
        Assert.assertEquals(-1, parseIso8601("wat", -1))
        Assert.assertEquals(-2, parseIso8601("", -2))
    }

    @Test
    fun testParseTimeStamp() {
        Assert.assertEquals(1427889600000L, parseIso8601("2015-04-01 12:00:00", -1))
        Assert.assertEquals(1427889600000L, parseIso8601("2015-04-01T12:00:00", -1))
    }

    @Test
    fun testCreateTimeStamp() {
        Assert.assertEquals("2015-04-01T12:00:00", createIso8601TimeStamp(1427889600000L))
        Assert.assertEquals("1970-01-01T00:00:00", createIso8601TimeStamp(0))
        Assert.assertEquals("1969-12-31T23:59:59", createIso8601TimeStamp(-1L))
    }
}
