/* Copyright Airship and Contributors */
package com.urbanairship.push

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.BaseTestCase
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.push.QuietTimeInterval
import java.util.Calendar
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class QuietTimeIntervalTest {

    @Test
    public fun testParsing() {
        val startHour = Calendar.getInstance()[Calendar.HOUR_OF_DAY] - 1
        val endHour = Calendar.getInstance()[Calendar.HOUR_OF_DAY] + 1
        val interval = QuietTimeInterval.newBuilder()
            .setStartHour(startHour)
            .setStartMin(30)
            .setEndHour(endHour)
            .setEndMin(15)
            .build()

        val value = interval.toJsonValue()
        val json =
            "{\"start_hour\": $startHour, \"start_min\": 30,\"end_hour\": $endHour,\"end_min\": 15}"

        Assert.assertEquals(interval, QuietTimeInterval.fromJson(JsonValue.parseString(json)))

        val jsonMap = jsonMapOf(
            "start_hour" to startHour,
            "start_min" to 30,
            "end_hour" to endHour,
            "end_min" to 15
        ).toJsonValue()
        Assert.assertEquals(value, jsonMap)
    }

    @Test
    public fun testQuietTimeIntervalStartBeforeEnd() {
        val interval = QuietTimeInterval.newBuilder()
            .setStartHour(3)
            .setStartMin(30)
            .setEndHour(4)
            .setEndMin(15)
            .build()

        val now = Calendar.getInstance()

        // 3:30 should be in quiet time
        now[Calendar.HOUR_OF_DAY] = 3
        now[Calendar.MINUTE] = 30
        Assert.assertTrue(interval.isInQuietTime(now))

        // 4:15 should be in quiet time
        now[Calendar.HOUR_OF_DAY] = 4
        now[Calendar.MINUTE] = 15
        Assert.assertTrue(interval.isInQuietTime(now))

        // 3:29 should be out of quiet time
        now[Calendar.HOUR_OF_DAY] = 3
        now[Calendar.MINUTE] = 29
        Assert.assertFalse(interval.isInQuietTime(now))

        // 4:15 should be out of quiet time
        now[Calendar.HOUR_OF_DAY] = 4
        now[Calendar.MINUTE] = 16
        Assert.assertFalse(interval.isInQuietTime(now))
    }

    @Test
    public fun testQuietTimeIntervalStartAfterEnd() {
        val interval = QuietTimeInterval.newBuilder()
            .setStartHour(3)
            .setStartMin(30)
            .setEndHour(3)
            .setEndMin(15)
            .build()

        val now = Calendar.getInstance()

        // 3:30 should be in quiet time
        now[Calendar.HOUR_OF_DAY] = 3
        now[Calendar.MINUTE] = 30
        Assert.assertTrue(interval.isInQuietTime(now))

        // 3:15 should be in quiet time
        now[Calendar.HOUR_OF_DAY] = 3
        now[Calendar.MINUTE] = 15
        Assert.assertTrue(interval.isInQuietTime(now))

        // 3:29 should be out of quiet time
        now[Calendar.HOUR_OF_DAY] = 3
        now[Calendar.MINUTE] = 29
        Assert.assertFalse(interval.isInQuietTime(now))

        // 3:16 should be out of quiet time
        now[Calendar.HOUR_OF_DAY] = 3
        now[Calendar.MINUTE] = 16
        Assert.assertFalse(interval.isInQuietTime(now))
    }

    @Test
    public fun testQuietTimeEqualStartEnd() {
        // Test that when start is equals to end, end is set to a day later.
        val interval = QuietTimeInterval.newBuilder()
            .setStartHour(3)
            .setStartMin(30)
            .setEndHour(3)
            .setEndMin(30)
            .build()

        val now = Calendar.getInstance()

        // 3:30 should be in quiet time
        now[Calendar.HOUR_OF_DAY] = 3
        now[Calendar.MINUTE] = 30
        Assert.assertTrue(interval.isInQuietTime(now))

        // 3:29 should be out of quiet time
        now[Calendar.HOUR_OF_DAY] = 3
        now[Calendar.MINUTE] = 29
        Assert.assertFalse(interval.isInQuietTime(now))

        // 3:31 should be out of quiet time
        now[Calendar.HOUR_OF_DAY] = 3
        now[Calendar.MINUTE] = 31
        Assert.assertFalse(interval.isInQuietTime(now))
    }

    @Test
    public fun testQuietTimeEdgeTimes() {
        var interval = QuietTimeInterval.newBuilder()
            .setStartHour(0)
            .setStartMin(0)
            .setEndHour(23)
            .setEndMin(59)
            .build()

        val now = Calendar.getInstance()

        // 0:00 should be in quiet time
        now[Calendar.HOUR_OF_DAY] = 0
        now[Calendar.MINUTE] = 0
        Assert.assertTrue(interval.isInQuietTime(now))

        // 23:59 should be in quiet time
        now[Calendar.HOUR_OF_DAY] = 23
        now[Calendar.MINUTE] = 59
        Assert.assertTrue(interval.isInQuietTime(now))

        // 12:00 should be in quiet time
        now[Calendar.HOUR_OF_DAY] = 12
        now[Calendar.MINUTE] = 0
        Assert.assertTrue(interval.isInQuietTime(now))

        interval = QuietTimeInterval.newBuilder()
            .setStartHour(23)
            .setStartMin(59)
            .setEndHour(0)
            .setEndMin(0)
            .build()

        // 23:59 is in quiet time
        now[Calendar.HOUR_OF_DAY] = 23
        now[Calendar.MINUTE] = 59
        Assert.assertTrue(interval.isInQuietTime(now))

        // 0:00 should be in quiet time
        now[Calendar.HOUR_OF_DAY] = 0
        now[Calendar.MINUTE] = 0
        Assert.assertTrue(interval.isInQuietTime(now))

        // 0:01 should be out of quiet time
        now[Calendar.HOUR_OF_DAY] = 0
        now[Calendar.MINUTE] = 1
        Assert.assertFalse(interval.isInQuietTime(now))

        // 23:58 should be out of quiet time
        now[Calendar.HOUR_OF_DAY] = 23
        now[Calendar.MINUTE] = 58
        Assert.assertFalse(interval.isInQuietTime(now))
    }
}
