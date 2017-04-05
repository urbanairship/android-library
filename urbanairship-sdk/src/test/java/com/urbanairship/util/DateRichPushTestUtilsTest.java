/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.util;

import com.urbanairship.BaseTestCase;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.text.ParseException;

import static junit.framework.Assert.assertEquals;

public class DateRichPushTestUtilsTest extends BaseTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testParseNullStringException() throws ParseException {
        exception.expect(ParseException.class);
        assertEquals(-1, DateUtils.parseIso8601(null));
    }

    @Test
    public void testParseInvalidStringException() throws ParseException {
        exception.expect(ParseException.class);
        assertEquals(-1, DateUtils.parseIso8601("wat"));
    }

    @Test
    public void testParseInvalidStringDefaultValue() {
        assertEquals(-1, DateUtils.parseIso8601("wat", -1));
        assertEquals(-2, DateUtils.parseIso8601("", -2));
        assertEquals(-3, DateUtils.parseIso8601(null, -3));
    }

    @Test
    public void testParseTimeStamp() {
        assertEquals(1427889600000l, DateUtils.parseIso8601("2015-04-01 12:00:00", -1));
        assertEquals(1427889600000l, DateUtils.parseIso8601("2015-04-01T12:00:00", -1));
    }

    @Test
    public void testCreateTimeStamp() {
        assertEquals("2015-04-01T12:00:00", DateUtils.createIso8601TimeStamp(1427889600000l));
        assertEquals("1970-01-01T00:00:00", DateUtils.createIso8601TimeStamp(0));
        assertEquals("1969-12-31T23:59:59", DateUtils.createIso8601TimeStamp(-1l));
    }
}
