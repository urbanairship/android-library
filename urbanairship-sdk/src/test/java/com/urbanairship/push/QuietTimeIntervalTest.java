/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Test;

import java.util.Calendar;
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

        Calendar now = Calendar.getInstance();

        // 3:30 should be in quiet time
        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 30);
        assertTrue(interval.isInQuietTime(now));

        // 4:15 should be in quiet time
        now.set(Calendar.HOUR_OF_DAY, 4);
        now.set(Calendar.MINUTE, 15);
        assertTrue(interval.isInQuietTime(now));

        // 3:29 should be out of quiet time
        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 29);
        assertFalse(interval.isInQuietTime(now));

        // 4:15 should be out of quiet time
        now.set(Calendar.HOUR_OF_DAY, 4);
        now.set(Calendar.MINUTE, 16);
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

        Calendar now = Calendar.getInstance();

        // 3:30 should be in quiet time
        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 30);
        assertTrue(interval.isInQuietTime(now));

        // 3:15 should be in quiet time
        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 15);
        assertTrue(interval.isInQuietTime(now));

        // 3:29 should be out of quiet time
        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 29);
        assertFalse(interval.isInQuietTime(now));

        // 3:16 should be out of quiet time
        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 16);
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

        Calendar now = Calendar.getInstance();

        // 3:30 should be in quiet time
        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 30);
        assertTrue(interval.isInQuietTime(now));

        // 3:29 should be out of quiet time
        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 29);
        assertFalse(interval.isInQuietTime(now));

        // 3:31 should be out of quiet time
        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 31);
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

        // 0:00 should be in quiet time
        now.set(Calendar.HOUR_OF_DAY, 0);
        now.set(Calendar.MINUTE, 0);
        assertTrue(interval.isInQuietTime(now));

        // 23:59 should be in quiet time
        now.set(Calendar.HOUR_OF_DAY, 23);
        now.set(Calendar.MINUTE, 59);
        assertTrue(interval.isInQuietTime(now));

        // 12:00 should be in quiet time
        now.set(Calendar.HOUR_OF_DAY, 12);
        now.set(Calendar.MINUTE, 00);
        assertTrue(interval.isInQuietTime(now));

        interval = new QuietTimeInterval.Builder()
                .setStartHour(23)
                .setStartMin(59)
                .setEndHour(0)
                .setEndMin(0)
                .build();

        // 23:59 is in quiet time
        now.set(Calendar.HOUR_OF_DAY, 23);
        now.set(Calendar.MINUTE, 59);
        assertTrue(interval.isInQuietTime(now));

        // 0:00 should be in quiet time
        now.set(Calendar.HOUR_OF_DAY, 0);
        now.set(Calendar.MINUTE, 0);
        assertTrue(interval.isInQuietTime(now));

        // 0:01 should be out of quiet time
        now.set(Calendar.HOUR_OF_DAY, 0);
        now.set(Calendar.MINUTE, 1);
        assertFalse(interval.isInQuietTime(now));

        // 23:58 should be out of quiet time
        now.set(Calendar.HOUR_OF_DAY, 23);
        now.set(Calendar.MINUTE, 58);
        assertFalse(interval.isInQuietTime(now));
    }
}
