/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QuietTimeIntervalTest extends BaseTestCase {

    @Test
    public void testParsing() throws JsonException {
        Integer startHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) - 1;
        Integer endHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 1;
        QuietTimeInterval interval = new QuietTimeInterval.Builder()
                .setStartHour(startHour)
                .setStartMin(30)
                .setEndHour(endHour)
                .setEndMin(15)
                .build();

        JsonValue value = interval.toJsonValue();
        String json =
                "{ " +
                    "\"start_hour\": " + startHour + ", " +
                    "\"start_min\": " + 30 + "," +
                    "\"end_hour\": " + endHour + "," +
                    "\"end_min\": " + 15 +
                "}";
        assertEquals(interval, QuietTimeInterval.parseJson(json));

        Map<String, Integer> jsonMap = new HashMap<>();
        jsonMap.put("start_hour", startHour);
        jsonMap.put("start_min", 30);
        jsonMap.put("end_hour", endHour);
        jsonMap.put("end_min", 15);
        assertEquals(value, JsonValue.wrap(jsonMap));
    }

    @Test
    public void testQuietTimeIntervalStartBeforeEnd() {
        QuietTimeInterval interval = new QuietTimeInterval.Builder()
                .setStartHour(3)
                .setStartMin(30)
                .setEndHour(4)
                .setEndMin(15)
                .build();

        Date[] intervalDates = interval.getQuietTimeIntervalDateArray();

        Calendar start = new GregorianCalendar();
        Calendar end = new GregorianCalendar();
        Calendar now = Calendar.getInstance();

        start.setTime(intervalDates[0]);
        end.setTime(intervalDates[1]);

        assertEquals(3, start.get(Calendar.HOUR_OF_DAY));
        assertEquals(4, end.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, start.get(Calendar.MINUTE));
        assertEquals(15, end.get(Calendar.MINUTE));

        // Check that a time between 3:30 and 4:15 is in quiet time
        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 45);
        assertTrue(interval.isInQuietTime(now));

        // Check that a time before 3:30 is not in quiet time
        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 10);
        assertFalse(interval.isInQuietTime(now));

        // Check that a time after 4:15 is not in quiet time
        now.set(Calendar.HOUR_OF_DAY, 4);
        now.set(Calendar.MINUTE, 20);
        assertFalse(interval.isInQuietTime(now));
    }

    @Test
    public void testQuietTimeIntervalStartAfterEnd() {
        QuietTimeInterval interval = new QuietTimeInterval.Builder()
                .setStartHour(3)
                .setStartMin(30)
                .setEndHour(3)
                .setEndMin(15)
                .build();

        Calendar end = new GregorianCalendar();
        Calendar now = Calendar.getInstance();

        end.setTime(interval.getQuietTimeIntervalDateArray()[1]);
        assertEquals(end.get(Calendar.DAY_OF_YEAR), now.get(Calendar.DAY_OF_YEAR) + 1);

        // Check that a time after 3:30 is in quiet time
        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 45);
        assertTrue(interval.isInQuietTime(now));

        // Check that a time before 3:30 is not in quiet time
        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 10);
        assertFalse(interval.isInQuietTime(now));

        // Check that a time before 3:15 the next day is in quiet time
        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 10);
        now.set(Calendar.DAY_OF_YEAR, Calendar.getInstance().get(Calendar.DAY_OF_YEAR) + 1);
        assertTrue(interval.isInQuietTime(now));

        // Check that a time after 3:15 the next day is not in quiet time
        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 20);
        assertFalse(interval.isInQuietTime(now));
    }

    @Test
    public void testQuietTimeEqualStartEnd() {
        // Test that when start is equals to end, end is set to a day later.
        QuietTimeInterval interval = new QuietTimeInterval.Builder()
                .setStartHour(3)
                .setStartMin(30)
                .setEndHour(3)
                .setEndMin(30)
                .build();

        Calendar end = new GregorianCalendar();
        Calendar now = Calendar.getInstance();

        end.setTime(interval.getQuietTimeIntervalDateArray()[1]);
        assertEquals(end.get(Calendar.DAY_OF_YEAR), now.get(Calendar.DAY_OF_YEAR) + 1);

        // Check that a time after 3:30 is in quiet time
        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 45);
        assertTrue(interval.isInQuietTime(now));

        // Check that a time before 3:30 is not in quiet time
        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 10);
        assertFalse(interval.isInQuietTime(now));

        // Check that a time before 3:15 the next day is in quiet time
        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 10);
        now.set(Calendar.DAY_OF_YEAR, Calendar.getInstance().get(Calendar.DAY_OF_YEAR) + 1);
        assertTrue(interval.isInQuietTime(now));

        // Check that a time after 3:15 the next day is not in quiet time
        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 40);
        assertFalse(interval.isInQuietTime(now));
    }

    @Test
    public void testQuietTimeEdgeTimes() {
        QuietTimeInterval interval = new QuietTimeInterval.Builder()
                .setStartHour(0)
                .setStartMin(0)
                .setEndHour(23)
                .setEndMin(59)
                .build();

        Calendar now = Calendar.getInstance();

        // Check that a time after 0:00 is in quiet time
        now.set(Calendar.HOUR_OF_DAY, 0);
        now.set(Calendar.MINUTE, 1);
        assertTrue(interval.isInQuietTime(now));

        // Check that a time before 23:59 is in quiet time
        now.set(Calendar.HOUR_OF_DAY, 23);
        now.set(Calendar.MINUTE, 58);
        assertTrue(interval.isInQuietTime(now));

        // Check that a time after 23:59 the next day is not in quiet time
        now.set(Calendar.HOUR_OF_DAY, 0);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.DAY_OF_YEAR, Calendar.getInstance().get(Calendar.DAY_OF_YEAR) + 1);
        assertFalse(interval.isInQuietTime(now));

        interval = new QuietTimeInterval.Builder()
                .setStartHour(23)
                .setStartMin(59)
                .setEndHour(0)
                .setEndMin(0)
                .build();

        Calendar end = new GregorianCalendar();
        now = Calendar.getInstance();

        end.setTime(interval.getQuietTimeIntervalDateArray()[1]);
        assertEquals(end.get(Calendar.DAY_OF_YEAR), now.get(Calendar.DAY_OF_YEAR) + 1);

        // Check that a time before 53:59 is not in quiet time
        now.set(Calendar.HOUR_OF_DAY, 23);
        now.set(Calendar.MINUTE, 58);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 999);
        assertFalse(interval.isInQuietTime(now));

        // Check that a time at 53:59 is in quiet time
        now.set(Calendar.HOUR_OF_DAY, 23);
        now.set(Calendar.MINUTE, 59);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);
        assertTrue(interval.isInQuietTime(now));

        // Check that a time after 53:59 is in quiet time
        now.set(Calendar.HOUR_OF_DAY, 23);
        now.set(Calendar.MINUTE, 59);
        now.set(Calendar.SECOND, 5);
        assertTrue(interval.isInQuietTime(now));

        // Check that a time at 0:00 the next day is in quiet time
        now.set(Calendar.HOUR_OF_DAY, 0);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);
        now.set(Calendar.DAY_OF_YEAR, Calendar.getInstance().get(Calendar.DAY_OF_YEAR) + 1);
        assertTrue(interval.isInQuietTime(now));

        // Check that a time after 0:00 the next day is not in quiet time
        now.set(Calendar.HOUR_OF_DAY, 0);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 5);
        assertFalse(interval.isInQuietTime(now));
    }
}
