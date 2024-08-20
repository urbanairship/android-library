/* Copyright Airship and Contributors */

package com.urbanairship.automation

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class DisplayWindowTest {

    private fun calendar(date: Date?): Calendar {
        val result = GregorianCalendar(TimeZone.getTimeZone("GMT+3"))
        date?.let { result.time = it }
        return result
    }

    private val timeZoneHours = 3

    private fun secondsFromMidnight(seconds: Int): Date = Date((1704060000 + seconds - 3600) * 1000L)

    @Test
    public fun returnsNowOnMatch() {
        val window = DisplayWindow(
            includes = listOf(
                DisplayWindow.Include(rule = DisplayWindowRule.Daily)
            )
        )

        assertTrue(DisplayWindowResult.Now == window.nextAvailability(Date()))
    }

    @Test
    public fun testReturnNextDayOnExcludeDaily() {
        val window = DisplayWindow(
            includes = listOf(DisplayWindow.Include(rule = DisplayWindowRule.Daily)),
            excludes = listOf(DisplayWindow.Exclude(DisplayWindowRule.Daily))
        )

        val currentDate = Date()
        val calendar = calendar(currentDate)
        val nextDay = calendar.startOfDay(currentDate).apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }.time

        val delay = (nextDay.time - currentDate.time).milliseconds
        assertEquals(DisplayWindowResult.Retry(delay), window.nextAvailability(currentDate, calendar))
    }

    @Test
    public fun testReturnNextDayOnExcludeDailyFixedDate() {
        val window = DisplayWindow(
            includes = listOf(DisplayWindow.Include(rule = DisplayWindowRule.Daily)),
            excludes = listOf(DisplayWindow.Exclude(DisplayWindowRule.Daily))
        )

        val currentDate = secondsFromMidnight(100)
        val calendar = calendar(currentDate)

        val delay = (86400 - 100).seconds
        assertEquals(DisplayWindowResult.Retry(delay), window.nextAvailability(currentDate, calendar))
    }

    @Test
    public fun testReturnNextDayOnExcludeWeekly() {

        val currentDate = Date()
        val calendar = calendar(currentDate)
        val nextDay = calendar.startOfDay(currentDate).apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }.time

        val delay = (nextDay.time - currentDate.time).milliseconds

        val window = DisplayWindow(
            includes = listOf(DisplayWindow.Include(rule = DisplayWindowRule.Daily)),
            excludes = listOf(DisplayWindow.Exclude(DisplayWindowRule.Weekly(
                months = listOf(calendar.get(Calendar.MONTH)),
                daysOfWeek = listOf(calendar.get(Calendar.DAY_OF_WEEK))
            )))
        )

        assertEquals(DisplayWindowResult.Retry(delay), window.nextAvailability(currentDate, calendar))
    }

    @Test
    public fun testReturnNextDayOnExcludeMonthly() {
        val currentDate = Date()
        val calendar = calendar(currentDate)
        val nextDay = calendar.startOfDay(currentDate).apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }.time

        val delay = (nextDay.time - currentDate.time).milliseconds

        val window = DisplayWindow(
            includes = listOf(DisplayWindow.Include(rule = DisplayWindowRule.Daily)),
            excludes = listOf(DisplayWindow.Exclude(DisplayWindowRule.Monthly(
                months = listOf(calendar.get(Calendar.MONTH)),
                daysOfMonth = listOf(calendar.get(Calendar.DAY_OF_MONTH))
            )))
        )

        assertEquals(DisplayWindowResult.Retry(delay), window.nextAvailability(currentDate, calendar))
    }

    @Test
    public fun testRetryRespectsExcludeTimeWindow() {
        var window = DisplayWindow(
            includes = listOf(DisplayWindow.Include(DisplayWindowRule.Daily)),
            excludes = listOf(
                DisplayWindow.Exclude(
                    rule = DisplayWindowRule.Daily,
                    timeWindow = DisplayWindow.TimeWindow(
                        startHour = 1,
                        endHour = 2
                    ),
                    timeZoneOffset = timeZoneHours
                )
            )
        )

        val current = secondsFromMidnight(100)
        assertEquals(DisplayWindowResult.Now, window.nextAvailability(current, calendar(current)))

        window = DisplayWindow(
            includes = listOf(DisplayWindow.Include(DisplayWindowRule.Daily)),
            excludes = listOf(
                DisplayWindow.Exclude(
                    rule = DisplayWindowRule.Daily,
                    timeWindow = DisplayWindow.TimeWindow(
                        startHour = 0,
                        endHour = 1
                    ),
                    timeZoneOffset = timeZoneHours
                )
            )
        )

        assertEquals(DisplayWindowResult.Retry(3500.seconds), window.nextAvailability(current, calendar(current)))
    }

    @Test
    public fun testReturnNowOnAnyIncludeMatch() {
        val window = DisplayWindow(
            includes = listOf(
                DisplayWindow.Include(DisplayWindowRule.Daily),
                DisplayWindow.Include(
                    rule = DisplayWindowRule.Daily,
                    timeWindow = DisplayWindow.TimeWindow(startHour = 0, endHour = 0),
                    timeZoneOffset = 0
                )
            ),
            excludes = listOf(DisplayWindow.Exclude(DisplayWindowRule.Weekly(daysOfWeek = listOf())))
        )

        val current = Date(100)
        assertEquals(DisplayWindowResult.Now, window.nextAvailability(current, calendar(current)))
    }

    @Test
    public fun testReturnNextDayOnMissedInclude() {
        val window = DisplayWindow(
            includes = listOf(
                DisplayWindow.Include(
                    rule = DisplayWindowRule.Daily,
                    timeWindow = DisplayWindow.TimeWindow(startHour = 0, endHour = 0, endMinute = 20)
                )
            )
        )

        val date = secondsFromMidnight(3600 / 2 + 60)
        val expected = (86400 - 1860).seconds
        assertEquals(DisplayWindowResult.Retry(expected), window.nextAvailability(date, calendar(date)))
    }

    @Test
    public fun testReturnTimeToTheIncludeOnMissedInclude() {
        val window = DisplayWindow(
            includes = listOf(
                DisplayWindow.Include(
                    rule = DisplayWindowRule.Daily,
                    timeWindow = DisplayWindow.TimeWindow(startHour = 1, endHour = 2)
                )
            )
        )

        val date = secondsFromMidnight(100)
        assertEquals(DisplayWindowResult.Retry(3500.seconds), window.nextAvailability(date, calendar(date)))
    }

    @Test
    public fun testReturnNowOnAnyIncludeTimeWindowMatch() {
        val window = DisplayWindow(
            includes = listOf(
                DisplayWindow.Include(
                    rule = DisplayWindowRule.Daily,
                    timeWindow = DisplayWindow.TimeWindow(startHour = 1, endHour = 2)
                ),
                DisplayWindow.Include(
                    rule = DisplayWindowRule.Daily,
                    timeWindow = DisplayWindow.TimeWindow(startHour = 0, endHour = 1)
                )
            )
        )

        val date = secondsFromMidnight(100)
        assertEquals(DisplayWindowResult.Now, window.nextAvailability(date, calendar(date)))
    }

    @Test
    public fun testReturnsNextSlotDelayOnInclude() {
        val window = DisplayWindow(
            includes = listOf(
                DisplayWindow.Include(
                    rule = DisplayWindowRule.Daily,
                    timeWindow = DisplayWindow.TimeWindow(startHour = 0, endHour = 1)
                ),
                DisplayWindow.Include(
                    rule = DisplayWindowRule.Daily,
                    timeWindow = DisplayWindow.TimeWindow(startHour = 2, endHour = 3)
                )
            )
        )

        val date = secondsFromMidnight(60 * 60 + 100)
        assertEquals(DisplayWindowResult.Retry(3500.seconds), window.nextAvailability(date, calendar(date)))
    }

    @Test
    public fun testIncludeRespectsTimeZone() {
        var window = DisplayWindow(
            includes = listOf(
                DisplayWindow.Include(
                    rule = DisplayWindowRule.Daily,
                    timeWindow = DisplayWindow.TimeWindow(startHour = 1, endHour = 2),
                    timeZoneOffset = 0
                ))
        )

        val date = secondsFromMidnight(3660)
        assertEquals(
            DisplayWindowResult.Retry((86400 - 3660).seconds),
            window.nextAvailability(date, calendar(date)))

        window = DisplayWindow(
            includes = listOf(
                DisplayWindow.Include(
                    rule = DisplayWindowRule.Daily,
                    timeWindow = DisplayWindow.TimeWindow(startHour = 1, endHour = 2),
                    timeZoneOffset = timeZoneHours
                ))
        )
        assertEquals(DisplayWindowResult.Now, window.nextAvailability(date, calendar(date)))
    }

    @Test
    public fun testExcludeRespectsTimeZone() {
        val date = secondsFromMidnight(20 * 60 * 60 + 3600 / 2) // 20:30
        val calendar = calendar(date)
        val excludeMonth = calendar.get(Calendar.MONTH)
        val excludeDay = calendar.get(Calendar.DAY_OF_WEEK)

        var window = DisplayWindow(
            includes = listOf(DisplayWindow.Include(DisplayWindowRule.Daily)),
            excludes = listOf(
                DisplayWindow.Exclude(
                    rule = DisplayWindowRule.Weekly(listOf(excludeMonth), listOf(excludeDay))
                )
            )
        )

        val nextDay = calendar.startOfDay(date).apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }.time

        val delay = (nextDay.time - date.time).milliseconds

        assertEquals(DisplayWindowResult.Retry(delay), window.nextAvailability(date, calendar(date)))

        window = DisplayWindow(
            includes = listOf(DisplayWindow.Include(DisplayWindowRule.Daily)),
            excludes = listOf(
                DisplayWindow.Exclude(
                    rule = DisplayWindowRule.Weekly(listOf(excludeMonth), listOf(excludeDay)),
                    timeZoneOffset = 8
                )
            )
        )
        assertEquals(DisplayWindowResult.Now, window.nextAvailability(date, calendar(date)))
    }

    @Test
    public fun testExcludeTimezone() {
        val date = secondsFromMidnight(21 * 60 * 60)
        val window = DisplayWindow(
            includes = listOf(DisplayWindow.Include(DisplayWindowRule.Daily)),
            excludes = listOf(
                DisplayWindow.Exclude(
                    rule = DisplayWindowRule.Daily,
                    timeZoneOffset = timeZoneHours + 2
                )
            )
        )

        assertEquals(DisplayWindowResult.Retry(1.hours), window.nextAvailability(date, calendar(date)))
    }
}
