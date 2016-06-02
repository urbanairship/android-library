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
    public void testQuietTimeInterval() {
        QuietTimeInterval interval = new QuietTimeInterval.Builder()
                .setStartHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY) - 1)
                .setStartMin(30)
                .setEndHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 1)
                .setEndMin(15)
                .build();

        Date[] intervalDates = interval.getQuietTimeIntervalDateArray();

        Calendar start = new GregorianCalendar();
        start.setTime(intervalDates[0]);
        Calendar end = new GregorianCalendar();
        end.setTime(intervalDates[1]);

        assertEquals(Calendar.getInstance().get(Calendar.HOUR_OF_DAY) - 1, start.get(Calendar.HOUR_OF_DAY));
        assertEquals(Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 1, end.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, start.get(Calendar.MINUTE));
        assertEquals(15, end.get(Calendar.MINUTE));
        assertTrue(interval.isInQuietTime());

        interval = new QuietTimeInterval.Builder()
                .setStartHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 1)
                .setStartMin(30)
                .setEndHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY) - 1)
                .setEndMin(15)
                .build();

        end.setTime(interval.getQuietTimeIntervalDateArray()[1]);
        Calendar now = Calendar.getInstance();

        assertEquals(end.get(Calendar.DAY_OF_YEAR), now.get(Calendar.DAY_OF_YEAR) + 1);
        assertTrue(interval.isInQuietTime());

        interval = new QuietTimeInterval.Builder()
                .setStartHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY) - 1)
                .setStartMin(15)
                .setEndHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY) - 1)
                .setEndMin(30)
                .build();

        assertFalse(interval.isInQuietTime());
    }
}
