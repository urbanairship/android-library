/* Copyright Airship and Contributors */

package com.urbanairship.automation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import junit.framework.TestCase.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ExecutionWindowTest {
    private var defaultTimeZone = TimeZone.getTimeZone("GMT")

    private fun calendar(): Calendar {
        return GregorianCalendar(defaultTimeZone)
    }

    // Jan 1, 2024 leap year!
    private val referenceDate: Date
        get() {
            return calendar().apply {
                set(Calendar.YEAR, 2024)
                set(Calendar.MONTH, Calendar.JANUARY)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
        }

    @Test
    public fun testParsing() {
        val json = """
        {
          "include": [
            {
              "type": "weekly",
              "days_of_week": [1,2,3,4,5],
              "time_range": {
                "start_hour": 8,
                "start_minute": 30,
                "end_hour": 5,
                "end_minute": 59
              }
            },
          ],
          "exclude": [
            {
              "type": "daily",
              "time_range": {
                "start_hour": 12,
                "start_minute": 0,
                "end_hour": 13,
                "end_minute": 0
              },
              "time_zone": {
                "type": "local"
              }
            },
            {
              "type": "monthly",
              "months": [12],
              "days_of_month": [24, 31]
            },
            {
              "type": "monthly",
              "months": [1],
              "days_of_month": [1]
            }
          ]
        }
        """

        val expected = ExecutionWindow(
            includes = listOf(
                Rule.Weekly(
                    daysOfWeek = listOf(1,2,3,4,5),
                    timeRange = Rule.TimeRange(
                        startHour = 8,
                        startMinute = 30,
                        endHour = 5,
                        endMinute = 59
                    )
                )
            ),
            excludes = listOf(
                Rule.Daily(
                    timeRange = Rule.TimeRange(
                        startHour = 12,
                        startMinute = 0,
                        endHour = 13,
                        endMinute = 0
                    ),
                    timeZone = Rule.TimeZone.Local
                ),
                Rule.Monthly(
                    months = listOf(12),
                    daysOfMonth = listOf(24, 31)
                ),
                Rule.Monthly(
                    months = listOf(1),
                    daysOfMonth = listOf(1)
                ),
            )
        )

        verify(json, expected)
    }

    @Test
    public fun testDaily() {
        val json = """
        {
          "include": [
            {
              "type": "daily",
              "time_range": {
                "start_hour": 12,
                "start_minute": 1,
                "end_hour": 13,
                "end_minute": 2
              },
              "time_zone": {
                "type": "utc"
              }
            },
          ]
        }
        """

        val expected = ExecutionWindow(
            includes = listOf(
                Rule.Daily(
                    timeRange = Rule.TimeRange(
                        startHour = 12, startMinute = 1, endHour = 13, endMinute = 2
                    ),
                    timeZone = Rule.TimeZone.Utc
                )
            )
        )

        verify(json, expected)
    }

    @Test
    public fun testInvalidDaily() {
        val json = """
        {
          "include": [
            {
              "type": "daily"
            },
          ]
        }
        """

        assertThrows(JsonException::class.java) {
            verify(json, ExecutionWindow())
        }
    }

    @Test
    public fun testInvalidTimeRange() {
        val json = """
        {
          "include": [
            {
              "type": "daily",
              "time_range": {
                start_minute: -1,
                start_hour: 12,
                end_minute: 0,
                end_hour: 1
              }
            }
          ]
        }
        """

        assertThrows(JsonException::class.java) {
            verify(json, ExecutionWindow())
        }
    }

    @Test
    public fun testInvalidTimeZoneType() {
        val json = """
        {
          "include": [
            {
              "type": "daily",
              "time_range": {
                "start_hour": 12,
                "start_minute": 1,
                "end_hour": 13,
                "end_minute": 2
              },
              "time_zone": {
                "type": "something"
              }
            },
          ]
        }
        """

        assertThrows(JsonException::class.java) {
            verify(json, ExecutionWindow())
        }
    }

    @Test
    public fun testTimeZoneIdentifiers() {
        val json = """
        {
          "include": [
            {
              "type": "daily",
              "time_range": {
                "start_hour": 12,
                "start_minute": 1,
                "end_hour": 13,
                "end_minute": 2
              },
              "time_zone": {
                "type": "identifiers",
                "identifiers": ["America/Los_Angeles", "Africa/Abidjan"],
                "on_failure": "skip"
              }
            }
          ]
        }
        """

        val expected = ExecutionWindow(
            includes = listOf(
                Rule.Daily(
                    timeRange = Rule.TimeRange(
                        startHour = 12,
                        startMinute = 1,
                        endHour = 13,
                        endMinute = 2
                    ),
                    timeZone = Rule.TimeZone.Identifiers(
                        ids = listOf("America/Los_Angeles", "Africa/Abidjan"),
                        onFailure = Rule.TimeZone.FailureMode.SKIP
                    )
                )
            )
        )

        verify(json, expected)
    }

    @Test
    public fun testMonthly() {
        val json = """
        {
          "include": [
            {
              "type": "monthly",
              "months": [1, 12],
              "days_of_month": [1, 31]
            },
          ]
        }
        """

        val expected = ExecutionWindow(
            includes = listOf(
                Rule.Monthly(
                    months = listOf(1, 12),
                    daysOfMonth = listOf(1, 31)
                )
            )
        )

        verify(json, expected)
    }

    @Test
    public fun testMonthlyOnlyMonths() {
        val json = """
        {
          "include": [
            {
              "type": "monthly",
              "months": [1, 12]
            },
          ]
        }
        """

        val expected = ExecutionWindow(
            includes = listOf(
                Rule.Monthly(
                    months = listOf(1, 12),
                )
            )
        )

        verify(json, expected)
    }

    @Test
    public fun testMonthlyOnlyDays() {
        val json = """
        {
          "include": [
            {
              "type": "monthly",
              "days_of_month": [1, 31]
            },
          ]
        }
        """

        val expected = ExecutionWindow(
            includes = listOf(
                Rule.Monthly(
                    daysOfMonth = listOf(1, 31)
                )
            )
        )

        verify(json, expected)
    }

    @Test
    public fun testInvalidMonthly() {
        val json = """
        {
          "include": [
            {
              "type": "monthly"
            },
          ]
        }
        """

        assertThrows(JsonException::class.java) {
            verify(json, ExecutionWindow())
        }
    }

    @Test
    public fun testInvalidMonthlyEmpty() {
        val json = """
        {
          "include": [
            {
              "type": "monthly",
              "days_of_month": [],
              "months": []
            },
          ]
        }
        """

        assertThrows(JsonException::class.java) {
            verify(json, ExecutionWindow())
        }
    }

    @Test
    public fun testInvalidMonthlyMonthsBelow1() {
        val json = """
        {
          "include": [
            {
              "type": "monthly",
              "months": [0]
            },
          ]
        }
        """

        assertThrows(JsonException::class.java) {
            verify(json, ExecutionWindow())
        }
    }

    @Test
    public fun testInvalidMonthlyMonthAbove12() {
        val json = """
        {
          "include": [
            {
              "type": "monthly",
              "months": [13]
            },
          ]
        }
        """

        assertThrows(JsonException::class.java) {
            verify(json, ExecutionWindow())
        }
    }

    @Test
    public fun testInvalidMonthlyDaysAbove31() {
        val json = """
        {
          "include": [
            {
              "type": "monthly",
              "days_of_month": [32]
            },
          ]
        }
        """

        assertThrows(JsonException::class.java) {
            verify(json, ExecutionWindow())
        }
    }

    @Test
    public fun testInvalidMonthlyDaysBelow1() {
        val json = """
        {
          "include": [
            {
              "type": "monthly",
              "days_of_month": [0]
            },
          ]
        }
        """

        assertThrows(JsonException::class.java) {
            verify(json, ExecutionWindow())
        }
    }

    @Test
    public fun testWeekly() {
        val json = """
        {
          "include": [
            {
              "type": "weekly",
              "days_of_week": [1, 7]
            },
          ]
        }
        """

        val expected = ExecutionWindow(
            includes = listOf(
                Rule.Weekly(
                    daysOfWeek = listOf(1,7)
                )
            )
        )

        verify(json, expected)
    }

    @Test
    public fun testWeeklyInvalidEmptyDaysOfWeek() {
        val json = """
        {
          "include": [
            {
              "type": "weekly",
              "days_of_week": []
            },
          ]
        }
        """
        assertThrows(JsonException::class.java) {
            verify(json, ExecutionWindow())
        }
    }

    @Test
    public fun testWeeklyInvalidEmptyDaysOfWeekBelow1() {
        val json = """
        {
          "include": [
            {
              "type": "weekly",
              "days_of_week": [0]
            },
          ]
        }
        """
        assertThrows(JsonException::class.java) {
            verify(json, ExecutionWindow())
        }
    }

    @Test
    public fun testWeeklyInvalidEmptyDaysOfAbove7() {
        val json = """
        {
          "include": [
            {
              "type": "weekly",
              "days_of_week": [8]
            },
          ]
        }
        """
        assertThrows(JsonException::class.java) {
            verify(json, ExecutionWindow())
        }
    }

    @Test
    public fun testReturnNowOnMatch() {
        val window = ExecutionWindow(
            includes = listOf(
                Rule.Daily(Rule.TimeRange(startHour = 0, endHour = 23))
            )
        )

        assertEquals(ExecutionWindowResult.Now, window.test())
    }

    @Test
    public fun testEmptyWindow() {
        val window = ExecutionWindow()
        assertEquals(ExecutionWindowResult.Now, window.test())
    }

    @Test
    public fun testIncludeTimeRangeSameStartAndEnd() {
        val date = referenceDate

        val window = ExecutionWindow(
            includes = listOf(
                Rule.Daily(Rule.TimeRange(startHour = 0, endHour = 0))
            )
        )

        assertEquals(ExecutionWindowResult.Now, window.test(date))

        date.adding(1.seconds)
        assertEquals(
            ExecutionWindowResult.Retry(1.days - 1.seconds),
            window.test(date))

        date.subtracting(2.seconds)
        assertEquals(ExecutionWindowResult.Retry(1.seconds), window.test(date))
    }

    @Test
    public fun testIncludeTimeRange() {
        val date = referenceDate
        val window = ExecutionWindow(
            includes = listOf(
                Rule.Daily(Rule.TimeRange(startHour = 3, endHour = 4))
            )
        )

        assertEquals(ExecutionWindowResult.Retry(3.hours), window.test(date))

        date.adding(3.hours)
        assertEquals(ExecutionWindowResult.Now, window.test(date))

        date.adding(1.hours)
        assertEquals(ExecutionWindowResult.Retry(23.hours), window.test(date))
    }

    @Test
    public fun testExcludeTimeRangeSameStartAndEnd() {
        val date = referenceDate
        val window = ExecutionWindow(
            excludes = listOf(
                Rule.Daily(Rule.TimeRange(startHour = 0, endHour = 0))
            )
        )

        assertEquals(ExecutionWindowResult.Retry(1.seconds), window.test(date))

        date.adding(1.seconds)
        assertEquals(ExecutionWindowResult.Now, window.test(date))

        date.subtracting(2.seconds)
        assertEquals(ExecutionWindowResult.Now, window.test(date))
    }

    @Test
    public fun testExcludeEndOfTimeRange() {
        val date = referenceDate
        date.adding(3.hours)

        val window = ExecutionWindow(
            excludes = listOf(
                Rule.Daily(Rule.TimeRange(startHour = 3, endHour = 0))
            )
        )

        assertEquals(ExecutionWindowResult.Retry(21.hours), window.test(date))

        date.adding(21.hours)
        assertEquals(ExecutionWindowResult.Now, window.test(date))
    }

    @Test
    public fun testExcludeTimeRangeWrap() {
        val date = calendar().apply {
            set(Calendar.YEAR, 2024)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val window = ExecutionWindow(
            excludes = listOf(
                Rule.Daily(Rule.TimeRange(startHour = 23, endHour = 1))
            )
        )

        assertEquals(ExecutionWindowResult.Retry(2.hours), window.test(date))

        date.adding(1.hours)
        assertEquals(ExecutionWindowResult.Retry(1.hours), window.test(date))

        date.adding(1.hours)
        assertEquals(ExecutionWindowResult.Now, window.test(date))
    }

    @Test
    public fun testIncludeAndExcludeSameRule() {
        val date = referenceDate
        date.adding(3.hours)

        val window = ExecutionWindow(
            includes = listOf(
                Rule.Daily(Rule.TimeRange(startHour = 3, endHour = 0))
            ),
            excludes = listOf(
                Rule.Daily(Rule.TimeRange(startHour = 3, endHour = 0))
            )
        )

        assertEquals(
            ExecutionWindowResult.Retry(1.days - 3.hours),
            window.test(date)
        )
    }

    @Test
    public fun testIncludeWeekly() {
        val date = calendar().apply {
            time = referenceDate
            set(Calendar.DAY_OF_WEEK, 4)
        }.time

        val window = ExecutionWindow(
            includes = listOf(
                Rule.Weekly(daysOfWeek = listOf(3, 5))
            )
        )

        assertEquals(ExecutionWindowResult.Retry(1.days), window.test(date))

        date.adding(1.days)
        assertEquals(ExecutionWindowResult.Now, window.test(date))

        date.adding(1.days)
        assertEquals(ExecutionWindowResult.Retry(4.days), window.test(date))

        date.adding(3.days)
        assertEquals(ExecutionWindowResult.Retry(1.days), window.test(date))

        date.adding(1.days)
        assertEquals(ExecutionWindowResult.Now, window.test(date))
    }

    @Test
    public fun testIncludeWeeklyTimeRange() {
        val date = calendar().apply {
            time = referenceDate
            set(Calendar.DAY_OF_WEEK, 4)
        }.time

        val window = ExecutionWindow(
            includes = listOf(
                Rule.Weekly(
                    daysOfWeek = listOf(3, 5),
                    timeRange = Rule.TimeRange(startHour = 3, endHour = 0)
                )
            )
        )

        assertEquals(
            ExecutionWindowResult.Retry(1.days + 3.hours),
            window.test(date)
        )

        date.adding(1.days + 3.hours - 1.seconds)
        assertEquals(ExecutionWindowResult.Retry(1.seconds), window.test(date))

        date.adding(1.seconds)
        assertEquals(ExecutionWindowResult.Now, window.test(date))

        date.adding(21.hours - 1.seconds)
        assertEquals(ExecutionWindowResult.Now, window.test(date))

        date.adding(1.seconds)
        assertEquals(
            ExecutionWindowResult.Retry(4.days + 3.hours),
            window.test(date)
        )
    }

    @Test
    public fun testIncludeWeeklyTimeRangeWithTimeZone() {
        val date = calendar().apply {
            time = referenceDate
            set(Calendar.DAY_OF_WEEK, 4)
        }.time

        val window = ExecutionWindow(
            includes = listOf(
                Rule.Weekly(
                    daysOfWeek = listOf(3, 5),
                    timeRange = Rule.TimeRange(startHour = 3, endHour = 0),
                    timeZone = secondsFromGmtTimeZone(1.hours.inWholeSeconds.toInt())
                )
            )
        )

        assertEquals(
            ExecutionWindowResult.Retry(1.days + 2.hours),
            window.test(date)
        )

        date.adding(1.days + 2.hours - 1.seconds)
        assertEquals(ExecutionWindowResult.Retry(1.seconds), window.test(date))

        date.adding(1.seconds)
        assertEquals(ExecutionWindowResult.Now, window.test(date))

        date.adding(21.hours - 1.seconds)
        assertEquals(ExecutionWindowResult.Now, window.test(date))

        date.adding(1.seconds)
        assertEquals(
            ExecutionWindowResult.Retry(4.days + 3.hours),
            window.test(date)
        )
    }

    @Test
    public fun testExcludeWeeklyTimeRange() {
        val date = calendar().apply {
            time = referenceDate
            set(Calendar.DAY_OF_WEEK, 4)
        }.time

        val window = ExecutionWindow(
            excludes = listOf(
                Rule.Weekly(
                    daysOfWeek = listOf(3, 5),
                    timeRange = Rule.TimeRange(startHour = 3, endHour = 0),
                )
            )
        )

        assertEquals(ExecutionWindowResult.Now, window.test(date))

        date.adding(1.days + 3.hours - 1.seconds)
        assertEquals(ExecutionWindowResult.Now, window.test(date))

        date.adding(1.seconds)
        assertEquals(ExecutionWindowResult.Retry(21.hours), window.test(date))

        date.adding(21.hours - 1.seconds)
        assertEquals(ExecutionWindowResult.Retry(1.seconds), window.test(date))

        date.adding(1.seconds)
        assertEquals(ExecutionWindowResult.Now, window.test(date))
    }

    @Test
    public fun testIncludeMonthly() {
        val date = calendar().apply {
            time = referenceDate
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }.time

        val window = ExecutionWindow(
            includes = listOf(
                Rule.Monthly(
                    months = listOf(2, 4),
                    daysOfMonth = listOf(15, 10)
                )
            )
        )

        assertEquals(ExecutionWindowResult.Retry(40.days), window.test(date))

        date.adding(40.days)
        assertEquals(ExecutionWindowResult.Now, window.test(date))

        date.adding(1.days)
        assertEquals(ExecutionWindowResult.Retry(4.days), window.test(date))

        date.adding(4.days)
        assertEquals(ExecutionWindowResult.Now, window.test(date))

        date.adding(1.days)
        assertEquals(ExecutionWindowResult.Retry(54.days), window.test(date))

        date.adding(55.days)
        assertEquals(ExecutionWindowResult.Retry(4.days), window.test(date))

        date.adding(5.days)
        assertEquals(ExecutionWindowResult.Retry(300.days), window.test(date))
    }

    @Test
    public fun testMonthlyNoMonthsAfterDay() {
        val date = calendar().apply {
            time = referenceDate
            set(Calendar.DAY_OF_MONTH, 16)
        }.time

        val window = ExecutionWindow(
            includes = listOf(
                Rule.Monthly(
                    daysOfMonth = listOf(15)
                )
            )
        )

        assertEquals(ExecutionWindowResult.Retry(30.days), window.test(date))
    }

    @Test
    public fun testMonthlyNextMonth() {
        val date = calendar().apply {
            time = referenceDate
            set(Calendar.MONTH, Calendar.FEBRUARY)
            set(Calendar.DAY_OF_MONTH, 16)
        }.time

        val window = ExecutionWindow(
            includes = listOf(
                Rule.Monthly(
                    months = listOf(1),
                    daysOfMonth = listOf(15)
                ),
                Rule.Monthly(
                    months = listOf(3),
                    daysOfMonth = listOf(2, 3)
                )
            )
        )

        assertEquals(ExecutionWindowResult.Retry(15.days), window.test(date))
    }

    @Test
    public fun testMonthlyNextMonthNoDays() {
        val date = calendar().apply {
            time = referenceDate
            set(Calendar.MONTH, Calendar.FEBRUARY)
            set(Calendar.DAY_OF_MONTH, 16)
        }.time

        val window = ExecutionWindow(
            includes = listOf(
                Rule.Monthly(months = listOf(1, 3)),
            )
        )

        assertEquals(ExecutionWindowResult.Retry(14.days), window.test(date))
    }

    @Test
    public fun testMonthlyNextYear() {
        val date = calendar().apply {
            time = referenceDate
            set(Calendar.MONTH, Calendar.FEBRUARY)
            set(Calendar.DAY_OF_MONTH, 15)
        }.time

        val window = ExecutionWindow(
            includes = listOf(
                Rule.Monthly(
                    months = listOf(1),
                    daysOfMonth = listOf(14)
                ),
            )
        )

        assertEquals(ExecutionWindowResult.Retry(334.days), window.test(date))
    }

    @Test
    public fun testIncludeMonthlyWithTimeZone() {
        val date = calendar().apply {
            time = referenceDate
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }.time

        val window = ExecutionWindow(
            includes = listOf(
                Rule.Monthly(
                    months = listOf(2, 4),
                    daysOfMonth = listOf(15, 10),
                    timeZone = secondsFromGmtTimeZone(7.hours.inWholeSeconds.toInt())
                ),
            )
        )

        assertEquals(
            ExecutionWindowResult.Retry(40.days - 7.hours),
            window.test(date)
        )

        date.adding(40.days - 7.hours)
        assertEquals(ExecutionWindowResult.Now, window.test(date))

        date.adding(1.days)
        assertEquals(ExecutionWindowResult.Retry(4.days), window.test(date))

        date.adding(4.days)
        assertEquals(ExecutionWindowResult.Now, window.test(date))

        date.adding(1.days)
        assertEquals(ExecutionWindowResult.Retry(54.days), window.test(date))

        date.adding(55.days)
        assertEquals(ExecutionWindowResult.Retry(4.days), window.test(date))

        date.adding(5.days)
        assertEquals(ExecutionWindowResult.Retry(300.days), window.test(date))
    }

    @Test
    public fun testImpossibleMonthlyInclude() {
        val date = calendar().apply {
            time = referenceDate
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }.time

        val window = ExecutionWindow(
            includes = listOf(
                Rule.Monthly(
                    months = listOf(2),
                    daysOfMonth = listOf(31),
                    timeRange = Rule.TimeRange(startHour = 5, endHour = 23)
                ),
            )
        )

        //we don't have distantFuture in android, so it's replaced with 1 year
        assertEquals(
            ExecutionWindowResult.Retry(366.days + 5.hours), //
            window.test(date)
        )
    }

    @Test
    public fun testMonthlySkipsInvalidMonths() {
        val date = calendar().apply {
            time = referenceDate
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }.time

        val window = ExecutionWindow(
            includes = listOf(
                Rule.Monthly(
                    months = listOf(2, 10),
                    daysOfMonth = listOf(31)
                )
            )
        )

        assertEquals(ExecutionWindowResult.Retry(304.days), window.test(date))
    }

    @Test
    public fun testImpossibleMonthlyExclude() {
        val date = calendar().apply {
            time = referenceDate
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }.time

        val window = ExecutionWindow(
            excludes = listOf(
                Rule.Monthly(
                    months = listOf(2),
                    daysOfMonth = listOf(31)
                )
            )
        )

        assertEquals(ExecutionWindowResult.Now, window.test(date))
    }

    @Test
    public fun testMonthlyWithoutMonths() {
        val date = calendar().apply {
            time = referenceDate
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }.time

        val window = ExecutionWindow(
            includes = listOf(
                Rule.Monthly(
                    daysOfMonth = listOf(31)
                )
            )
        )

        assertEquals(ExecutionWindowResult.Retry(30.days), window.test(date))

        date.adding(31.days)
        assertEquals(ExecutionWindowResult.Retry(30.days), window.test(date))
    }

    @Test
    public fun testMonthlyWithOnlyMonths() {
        val date = calendar().apply {
            time = referenceDate
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }.time

        val window = ExecutionWindow(
            includes = listOf(
                Rule.Monthly(months = listOf(10, 12))
            )
        )

        assertEquals(ExecutionWindowResult.Retry(274.days), window.test(date))

        date.adding(274.days)
        assertEquals(ExecutionWindowResult.Now, window.test(date))

        for (index in 0..<30) {
            date.adding(1.days)
            assertEquals(ExecutionWindowResult.Now, window.test(date))
        }

        date.adding(1.days)
        assertEquals(ExecutionWindowResult.Retry(30.days), window.test(date))
    }

    @Test
    public fun testEmptyMonthlyIncludeThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            ExecutionWindow(
                includes = listOf(Rule.Monthly())
            )
        }
    }

    @Test
    public fun testEmptyMonthlyExcludeThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            ExecutionWindow(
                excludes = listOf(Rule.Monthly())
            )
        }
    }

    @Test
    public fun testComplexRule() {
        val date = calendar().apply {
            time = referenceDate
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }.time

        val window = ExecutionWindow(
            includes = listOf(
                Rule.Daily(
                    timeRange = Rule.TimeRange(startHour = 1, endHour = 2),
                    timeZone = secondsFromGmtTimeZone(1.hours.inWholeSeconds.toInt())
                ),
                Rule.Weekly(
                    daysOfWeek = listOf(5),
                    timeRange = Rule.TimeRange(startHour = 3, endHour = 5),
                    timeZone = Rule.TimeZone.Utc
                ),
                Rule.Monthly(
                    months = listOf(2, 4),
                    daysOfMonth = listOf(2),
                    timeRange = Rule.TimeRange(startHour = 10, endHour = 22)
                )
            ),
            excludes = listOf(
                Rule.Monthly(months = listOf(1,3,5,7,9,11))
            )
        )

        // Exclude monthly without days is only 1 day at a time
        assertEquals(ExecutionWindowResult.Retry(1.days), window.test(date))

        for (index in 0..<30) {
            date.adding(1.days)
            assertEquals(ExecutionWindowResult.Retry(1.days), window.test(date))
        }

        // Feb 1
        date.adding(1.days)
        // Timezone offset for the daily rule is 1, so its makes it [0-1]
        assertEquals(ExecutionWindowResult.Now, window.test(date))

        date.adding(1.hours)
        // 2 hour until weekly rule for DOW 5
        assertEquals(ExecutionWindowResult.Retry(2.hours), window.test(date))

        date.adding(2.hours)
        assertEquals(ExecutionWindowResult.Now, window.test(date))

        date.adding(2.hours)
        // 19 hours until the daily rule again
        assertEquals(ExecutionWindowResult.Retry(19.hours), window.test(date))

        date.adding(19.hours)
        assertEquals(ExecutionWindowResult.Now, window.test(date))

        date.adding(1.hours)
        // 9 hours until the monthly rule
        assertEquals(ExecutionWindowResult.Retry(9.hours), window.test(date))

        date.adding(9.hours)
        assertEquals(ExecutionWindowResult.Now, window.test(date))

        date.adding((12.hours - 1.seconds))
        assertEquals(ExecutionWindowResult.Now, window.test(date))

        date.adding(1.seconds)
        // 2 hour until the daily rule again
        assertEquals(ExecutionWindowResult.Retry(2.hours), window.test(date))
    }

    @Test
    public fun testTransitionOutOfDST() {
        // Sun March 10 2024 we transition from PDT to PST
        defaultTimeZone = TimeZone.getTimeZone("America/Los_Angeles")

        val midnight = calendar().apply {
            set(Calendar.YEAR, 2024)
            set(Calendar.MONTH, Calendar.MARCH)
            set(Calendar.DAY_OF_MONTH, 10)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        // Sun March 10 2024
        val transition = calendar().apply {
            set(Calendar.YEAR, 2024)
            set(Calendar.MONTH, Calendar.MARCH)
            set(Calendar.DAY_OF_MONTH, 10)
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val window = ExecutionWindow(
            includes = listOf(
                Rule.Daily(Rule.TimeRange(startHour = 2, endHour = 4))
            )
        )

        // 12:00 PST
        assertEquals(ExecutionWindowResult.Retry(2.hours), window.test(midnight))

        // 3:00 PDT
        assertEquals(ExecutionWindowResult.Now, window.test(transition))

        // 4:00 PDT
        transition.adding(1.hours)
        assertEquals(ExecutionWindowResult.Retry(22.hours), window.test(transition))
    }

    @Test
    public fun testTransitionToDST() {
        // Sun Nov 3 2024 we transition from PST to PDT
        defaultTimeZone = TimeZone.getTimeZone("America/Los_Angeles")

        val midnight = calendar().apply {
            set(Calendar.YEAR, 2024)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 3)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        // Sun March 10 2024
        val transition = calendar().apply {
            set(Calendar.YEAR, 2024)
            set(Calendar.MONTH, Calendar.MARCH)
            set(Calendar.DAY_OF_MONTH, 10)
            set(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val window = ExecutionWindow(
            includes = listOf(
                Rule.Daily(Rule.TimeRange(startHour = 2, endHour = 4))
            )
        )

        // 12:00 PDT
        assertEquals(ExecutionWindowResult.Retry(3.hours), window.test(midnight))

        // 1:00 PST
        assertEquals(ExecutionWindowResult.Retry(1.hours), window.test(transition))

        // 2:00 PDT
        transition.adding(1.hours)
        assertEquals(ExecutionWindowResult.Now, window.test(transition))
    }

    @Test
    public fun testErrorTimeZoneIdentifiersFailed() {
        val window = ExecutionWindow(
            includes = listOf(
                Rule.Daily(
                    timeRange = Rule.TimeRange(startHour = 3, endHour = 0),
                    timeZone = Rule.TimeZone.Identifiers(
                        ids = listOf("invalid"),
                        onFailure = Rule.TimeZone.FailureMode.ERROR)
                )
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            window.test()
        }
    }

    @Test
    public fun testSkipTimeZoneIdentifiersFailed() {
        val window = ExecutionWindow(
            includes = listOf(
                Rule.Daily(
                    timeRange = Rule.TimeRange(startHour = 0, endHour = 10),
                    timeZone = Rule.TimeZone.Identifiers(
                        ids = listOf("invalid"),
                        onFailure = Rule.TimeZone.FailureMode.SKIP
                    )
                )
            )
        )

        assertEquals(ExecutionWindowResult.Now, window.test())
    }

    private fun verify(json: String, expected: ExecutionWindow) {
        val fromJson = ExecutionWindow.fromJson(JsonValue.parseString(json))
        assertEquals(expected, fromJson)

        val roundTrip = ExecutionWindow.fromJson(fromJson.toJsonValue())
        assertEquals(roundTrip, fromJson)
    }

    private fun ExecutionWindow.test(
        date: Date = referenceDate,
        timeZone: TimeZone = defaultTimeZone
    ): ExecutionWindowResult = this.nextAvailability(date, timeZone)

    private fun Date.adding(duration: Duration) {
        time += duration.inWholeMilliseconds
    }

    private fun Date.subtracting(duration: Duration) {
        time -= duration.inWholeMilliseconds
    }

    private fun secondsFromGmtTimeZone(seconds: Int): Rule.TimeZone {
        return Rule.TimeZone.Identifiers(
            ids = emptyList(),
            secondsFromUtc = seconds.seconds
        )
    }
}
