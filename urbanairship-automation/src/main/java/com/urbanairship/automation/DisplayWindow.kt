/* Copyright Airship and Contributors */

package com.urbanairship.automation

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.SimpleTimeZone
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DisplayWindow(
    internal val includes: List<Include>?,
    internal val excludes: List<Exclude>? = null
) : JsonSerializable {

    internal companion object {
        private const val INCLUDES = "includes"
        private const val EXCLUDES = "excludes"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): DisplayWindow {
            val content = value.requireMap()
            return DisplayWindow(
                includes = content.optionalField(INCLUDES),
                excludes = content.optionalField(EXCLUDES)
            )
        }

        fun calendar(timeZoneOffset: Int?): Calendar {
            val result = GregorianCalendar()

            timeZoneOffset?.let {
                result.timeZone = SimpleTimeZone(it.hours.toInt(DurationUnit.MILLISECONDS), "USER")
            }

            return result
        }
    }

    public fun nextAvailability(date: Date, customCalendar: Calendar? = null): DisplayWindowResult {
        val calendar = customCalendar ?: GregorianCalendar()
        val nextDay = calendar.startOfDay(date).apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }.time

        val nextDayDelay = (nextDay.time - date.time).milliseconds

        val exclude = excludes(date, calendar).firstOrNull()
        if (exclude != null) {
            val excludeCalendar = exclude.calendar(calendar)

            val excludeNextDay = excludeCalendar.startOfDay(date).apply {
                add(Calendar.DAY_OF_MONTH, 1)
            }.time

            val delay = exclude.timeWindow?.endOfSlot(date, excludeCalendar)
                ?: (excludeNextDay.time - date.time).milliseconds

            return DisplayWindowResult.Retry(delay)
        }

        var result: DisplayWindowResult = DisplayWindowResult.Retry(nextDayDelay)

        for (slot in includes(date, calendar)) {
            if (slot.timeWindow == null) {
                result = DisplayWindowResult.Now
                break
            }

            if (slot.timeWindow.contains(date, slot.calendar(calendar))) {
                result = DisplayWindowResult.Now
                break
            }

            val delay = slot.timeWindow.nextSlot(date, slot.calendar(calendar)) ?: continue
            result = DisplayWindowResult.Retry(delay)
            break
        }

        return result
    }

    private fun excludes(date: Date, local: Calendar): List<Exclude> {
        val items = excludes ?: return emptyList()
        return items
            .map { Pair(it, it.calendar(local)) }
            .filter { it.first.rule.isMatching(it.second) }
            .filter { it.first.timeWindow?.contains(date, it.second) ?: true }
            .map { it.first }
            .sorted()

    }

    private fun includes(date: Date, local: Calendar): List<Include> {
        val items = includes ?: return emptyList()

        return items
            .map { Pair(it, it.calendar(local)) }
            .filter { it.first.rule.isMatching(it.second) }
            .map { it.first }
            .sorted()
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        INCLUDES to includes,
        EXCLUDES to excludes
    ).toJsonValue()

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public class Include(
        internal val rule: DisplayWindowRule,
        internal val timeWindow: TimeWindow? = null,
        internal val timeZoneOffset: Int? = null
    ) : JsonSerializable, Comparable<Include> {

        internal companion object {
            private const val RULE = "rule"
            private const val TIME_WINDOW = "time_window"
            private const val TIME_ZONE_OFFSET = "time_zone_offset"

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Include {
                val content = value.requireMap()

                return Include(
                    rule = DisplayWindowRule.fromJson(content.require(RULE)),
                    timeWindow = content.get(TIME_WINDOW)?.let(TimeWindow::fromJson),
                    timeZoneOffset = content.optionalField(TIME_ZONE_OFFSET)
                )
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            RULE to rule,
            TIME_WINDOW to timeWindow,
            TIME_ZONE_OFFSET to timeZoneOffset
        ).toJsonValue()

        internal fun calendar(default: Calendar): Calendar {
            val offset = timeZoneOffset ?: return default
            return DisplayWindow.calendar(offset)
        }

        override fun compareTo(other: Include): Int {
            if (timeWindow == null && other.timeWindow == null) {
                return  0
            }

            if (timeWindow == null) {
                return -1
            }

            if (other.timeWindow == null) {
                return 1
            }

            return timeWindow.compareTo(other.timeWindow)
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public class Exclude(
        internal val rule: DisplayWindowRule,
        internal val timeWindow: TimeWindow? = null,
        internal val timeZoneOffset: Int? = null
    ) : JsonSerializable, Comparable<Exclude> {

        internal companion object {
            private const val RULE = "rule"
            private const val TIME_WINDOW = "time_window"
            private const val TIME_ZONE_OFFSET = "time_zone_offset"

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Exclude {
                val content = value.requireMap()

                return Exclude(
                    rule = DisplayWindowRule.fromJson(content.require(RULE)),
                    timeWindow = content.get(TIME_WINDOW)?.let(TimeWindow::fromJson),
                    timeZoneOffset = content.optionalField(TIME_ZONE_OFFSET)
                )
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            RULE to rule,
            TIME_WINDOW to timeWindow,
            TIME_ZONE_OFFSET to timeZoneOffset
        ).toJsonValue()

        internal fun calendar(default: Calendar): Calendar {
            val offset = timeZoneOffset ?: return default
            return DisplayWindow.calendar(offset)
        }

        override fun compareTo(other: Exclude): Int {
            if (timeWindow == null && other.timeWindow == null) {
                return  0
            }

            if (timeWindow == null) {
                return -1
            }

            if (other.timeWindow == null) {
                return 1
            }

            return timeWindow.compareTo(other.timeWindow)
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public class TimeWindow(
        internal val startHour: Int,
        internal val startMinute: Int? = null,
        internal val endHour: Int,
        internal val endMinute: Int? = null
    ) : JsonSerializable, Comparable<TimeWindow> {

        internal companion object {
            private const val START_HOUR = "start_hour"
            private const val START_MINUTE = "start_minute"
            private const val END_HOUR = "end_hour"
            private const val END_MINUTE = "end_minute"

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): TimeWindow {
                val content = value.requireMap()

                return TimeWindow(
                    startHour = content.requireField(START_HOUR),
                    startMinute = content.optionalField(START_MINUTE),
                    endHour = content.requireField(END_HOUR),
                    endMinute = content.optionalField(END_MINUTE)
                )
            }
        }

        override fun toJsonValue(): JsonValue {
            return jsonMapOf(
                START_HOUR to startHour,
                START_MINUTE to startMinute,
                END_HOUR to endHour,
                END_MINUTE to endMinute
            ).toJsonValue()
        }

        internal fun contains(date: Date, calendar: Calendar): Boolean {
            val startDate = calendar.startOfDay(date).apply {
                add(Calendar.HOUR, startHour)
                startMinute?.let { add(Calendar.MINUTE, it) }
            }

            val endDate = calendar.startOfDay(date).apply {
                add(Calendar.HOUR, endHour)
                endMinute?.let { add(Calendar.MINUTE, it) }
            }

            return startDate.time < date && endDate.time > date
        }

        internal fun nextSlot(date: Date, calendar: Calendar): Duration? {
            val target = calendar.startOfDay(date).apply {
                add(Calendar.HOUR, startHour)
                startMinute?.let { add(Calendar.MINUTE, it) }
            }.time

            if (target < date) {
                return null
            }

            return (target.time - date.time).milliseconds
        }

        internal fun endOfSlot(date: Date, calendar: Calendar): Duration {
            val target = calendar.startOfDay(date).apply {
                add(Calendar.HOUR, endHour)
                endMinute?.let { add(Calendar.MINUTE, it) }
            }.time

            return (target.time - date.time).milliseconds
        }

        override fun compareTo(other: TimeWindow): Int {
            val current = startHour * 60 + (startMinute ?: 0)
            val other = other.startHour * 60 + (other.startMinute ?: 0)

            return current.compareTo(other)
        }
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public sealed class DisplayWindowResult {
    public data object Now: DisplayWindowResult()

    public class Retry(public val delay: Duration): DisplayWindowResult() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Retry

            return delay == other.delay
        }

        override fun hashCode(): Int {
            return delay.hashCode()
        }

        override fun toString(): String {
            return "Retry(delay=$delay)"
        }
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public sealed class DisplayWindowRule() : JsonSerializable {
    internal data object Daily : DisplayWindowRule()

    internal data class Weekly(
        val months: List<Int>? = null,
        val daysOfWeek: List<Int>
    ) : DisplayWindowRule() {
        companion object {
            @Throws(JsonException::class)
            fun fromJson(value: JsonMap): Weekly {
                return Weekly(
                    months = value.optionalField(MONTHS),
                    daysOfWeek = value.requireField(DAYS_OF_WEEK)
                )
            }
        }

        fun makeJson(): JsonValue = jsonMapOf(
            MONTHS to months,
            DAYS_OF_WEEK to daysOfWeek
        ).toJsonValue()
    }

    internal data class Monthly(
        val months: List<Int>? = null,
        val daysOfMonth: List<Int>
    ) : DisplayWindowRule() {

        companion object {
            @Throws(JsonException::class)
            fun fromJson(value: JsonMap): Monthly {
                return Monthly(
                    months = value.optionalField(MONTHS),
                    daysOfMonth = value.requireField(DAYS_OF_MONTH)
                )
            }
        }

        fun makeJson(): JsonValue = jsonMapOf(
            MONTHS to months,
            DAYS_OF_MONTH to daysOfMonth
        ).toJsonValue()
    }

    internal companion object {
        private const val DAILY = "daily"
        private const val WEEKLY = "weekly"
        private const val MONTHLY = "monthly"
        private const val MONTHS = "months"
        private const val DAYS_OF_WEEK = "days_of_week"
        private const val DAYS_OF_MONTH = "days_of_month"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): DisplayWindowRule {
            if (value.isString && value.requireString() == DAILY) {
                return Daily
            }

            val content = value.requireMap()

            return if (content.containsKey(WEEKLY)) {
                Weekly.fromJson(content)
            } else if (content.containsKey(MONTHLY)) {
                Monthly.fromJson(content)
            } else {
                throw JsonException("failed to parse display rule from $content")
            }
        }
    }

    override fun toJsonValue(): JsonValue {
        return when(this) {
            Daily -> JsonValue.wrap(DAILY)
            is Monthly -> jsonMapOf(MONTHLY to makeJson()).toJsonValue()
            is Weekly -> jsonMapOf(WEEKLY to makeJson()).toJsonValue()
        }
    }

    internal fun isMatching(calendar: Calendar): Boolean {
        return when(this) {
            Daily -> true
            is Weekly -> {
                daysOfWeek.contains(calendar.get(Calendar.DAY_OF_WEEK)) &&
                        (months?.contains(calendar.get(Calendar.MONTH)) ?: true)
            }
            is Monthly -> {
                daysOfMonth.contains(calendar.get(Calendar.DAY_OF_MONTH)) &&
                        (months?.contains(calendar.get(Calendar.MONTH)) ?: true)
            }
        }
    }
}

internal fun Calendar.startOfDay(date: Date): Calendar {
    return (this.clone() as Calendar).apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
