/* Copyright Airship and Contributors */

package com.urbanairship.automation

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import java.util.Calendar
import java.util.Date
import java.util.Objects
import java.util.SimpleTimeZone
import java.util.TimeZone
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ExecutionWindow(
    internal val includes: List<Rule>? = null,
    internal val excludes: List<Rule>? = null
) : JsonSerializable {

    internal companion object {
        private const val INCLUDES = "include"
        private const val EXCLUDES = "exclude"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): ExecutionWindow {
            val content = value.requireMap()
            return ExecutionWindow(
                includes = content.get(INCLUDES)?.requireList()?.mapNotNull(Rule::fromJson),
                excludes = content.get(EXCLUDES)?.requireList()?.mapNotNull(Rule::fromJson)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        INCLUDES to includes,
        EXCLUDES to excludes
    ).toJsonValue()

    @Throws(IllegalArgumentException::class)
    internal fun nextAvailability(
        date: Date,
        currentTimeZone: TimeZone? = null
    ): ExecutionWindowResult {
        val timeZone = currentTimeZone ?: TimeZone.getDefault()

        val excluded = excludes
            ?.mapNotNull { it.resolve(date, timeZone) }
            ?.filter { it.contains(date) }
            ?.minByOrNull { it.endDate }

        if (excluded != null) {
            return ExecutionWindowResult.Retry(
                delay = maxOf(1.seconds, excluded.endDate.durationSince(date))
            )
        }

        val nextInclude = includes
            ?.mapNotNull { it.resolve(date, timeZone) }
            ?.minByOrNull { it.startDate }

        return if (nextInclude == null || nextInclude.contains(date)) {
            ExecutionWindowResult.Now
        } else {
            ExecutionWindowResult.Retry(
                delay = maxOf(1.seconds, nextInclude.startDate.durationSince(date))
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ExecutionWindow

        if (includes != other.includes) return false
        if (excludes != other.excludes) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(includes, excludes)
    }

}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public sealed class ExecutionWindowResult {
    public data object Now: ExecutionWindowResult()

    public class Retry(public val delay: Duration): ExecutionWindowResult() {

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

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public sealed class Rule(
    internal val type: Type,
    internal val timeZone: TimeZone?
) : JsonSerializable {

    internal class Daily(
        val timeRange: TimeRange,
        timeZone: TimeZone? = null)
        : Rule(Type.DAILY, timeZone) {

        companion object {
            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Daily {
                val content = value.requireMap()

                return Daily(
                    timeRange = TimeRange.fromJson(content.require(TIME_RANGE)),
                    timeZone = content.get(TIME_ZONE)?.let(TimeZone::fromJson)
                )
            }
        }

        override fun resolve(date: Date, current: java.util.TimeZone): DateRange? {
            return calendar(timeZone, current)?.dateInterval(date, timeRange)
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            TIME_RANGE to timeRange,
            TIME_ZONE to timeZone
        ).toJsonValue()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Daily

            if (timeRange != other.timeRange) return false
            if (timeZone != other.timeZone) return false

            return true
        }

        override fun hashCode(): Int {
            return Objects.hash(timeRange, timeZone)
        }

    }

    internal class Weekly(
        val daysOfWeek: List<Int>,
        val timeRange: TimeRange? = null,
        timeZone: TimeZone? = null
    ) : Rule(Type.WEEKLY, timeZone) {

        companion object {
            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Weekly {
                val content = value.requireMap()

                return try {
                    Weekly(
                        daysOfWeek = content
                            .require(DAYS_OF_WEEK)
                            .requireList()
                            .mapNotNull { it.integer },
                        timeRange = content.get(TIME_RANGE)?.let(TimeRange::fromJson),
                        timeZone = content.get(TIME_ZONE)?.let(TimeZone::fromJson)
                    )
                } catch (ex: IllegalArgumentException) {
                    throw JsonException("Invalid parameter", ex)
                }
            }
        }

        init {
            require(daysOfWeek.isNotEmpty()) {
                "Invalid daysOfWeek, must contain at least 1 day of week"
            }

            require(daysOfWeek.all { it in 1..7 }) {
                "Invalid daysOfWeek: $daysOfWeek, all values must be [1-7]"
            }
        }

        override fun resolve(date: Date, current: java.util.TimeZone): DateRange? {
            val calendar = calendar(timeZone, current) ?: return null
            var nextDate = calendar.nextDate(date, daysOfWeek)

            if (timeRange == null) {
                return calendar.remainingDay(nextDate)
            }

            while (true) {
                val timeInterval = calendar.dateInterval(nextDate, timeRange)
                val remainingDay = calendar.remainingDay(nextDate)

                val result = timeInterval.intersection(remainingDay)
                if (result == null) {
                    nextDate = calendar.nextDate(
                        date = calendar.startOfDay(date, addingDays = 1).time,
                        weekdays = daysOfWeek
                    )
                    continue
                }

                return result
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            DAYS_OF_WEEK to daysOfWeek,
            TIME_RANGE to timeRange,
            TIME_ZONE to timeZone
        ).toJsonValue()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Weekly

            if (daysOfWeek != other.daysOfWeek) return false
            if (timeRange != other.timeRange) return false
            if (timeZone != other.timeZone) return false

            return true
        }

        override fun hashCode(): Int {
            return Objects.hash(daysOfWeek, timeRange, timeZone)
        }
    }

    internal class Monthly(
        val months: List<Int>? = null,
        val daysOfMonth: List<Int>? = null,
        val timeRange: TimeRange? = null,
        timeZone: TimeZone? = null
    ) : Rule(Type.MONTHLY, timeZone) {

        companion object {

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Monthly {
                val content = value.requireMap()
                return try {
                    Monthly(
                        months = content.get(MONTHS)
                            ?.requireList()
                            ?.mapNotNull { it.integer }
                            ?.map { it },
                        daysOfMonth = content.get(DAYS_OF_MONTH)?.requireList()?.mapNotNull { it.integer },
                        timeRange = content.get(TIME_RANGE)?.let(TimeRange::fromJson),
                        timeZone = content.get(TIME_ZONE)?.let(TimeZone::fromJson)
                    )
                } catch (ex: IllegalArgumentException) {
                    throw JsonException("Invalid parameter", ex)
                }
            }
        }

        init {
            require(daysOfMonth?.isNotEmpty() == true || months?.isNotEmpty() == true) {
                "monthly rule must define either months or days of month"
            }

            months?.let { items ->
                require(items.all { it in 1..12 }) {
                    "Invalid month: $items, all values must be [1-12]"
                }
            }

            daysOfMonth?.let { items ->
                require(items.all { it in 1..31 }) {
                    "Invalid days of month: $items, all values must be [1-31]"
                }
            }
        }

        override fun resolve(date: Date, current: java.util.TimeZone): DateRange? {
            val platformMonth = months?.map { it - 1 }

            val calendar = calendar(timeZone, current) ?: return null
            var nextDate = calendar.nextDate(date, platformMonth, daysOfMonth)

            if (timeRange == null) {
                return calendar.remainingDay(nextDate)
            }

            while (true) {
                val timeInterval = calendar.dateInterval(nextDate, timeRange)
                val remainingDay = calendar.remainingDay(nextDate)

                val result = timeInterval.intersection(remainingDay)
                if (result == null) {
                    nextDate = calendar.nextDate(
                        date = calendar.startOfDay(date, addingDays = 1).time,
                        months = platformMonth,
                        days = daysOfMonth
                    )

                    continue
                }
                return result
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            MONTHS to months?.map { it },
            DAYS_OF_MONTH to daysOfMonth,
            TIME_RANGE to timeRange,
            TIME_ZONE to timeZone
        ).toJsonValue()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Monthly

            if (months != other.months) return false
            if (daysOfMonth != other.daysOfMonth) return false
            if (timeRange != other.timeRange) return false
            if (timeZone != other.timeZone) return false

            return true
        }

        override fun hashCode(): Int {
            return Objects.hash(months, daysOfMonth, timeRange, timeZone)
        }
    }

    @Throws(IllegalArgumentException::class)
    internal fun calendar(timeZone: TimeZone?, current: java.util.TimeZone): AirshipCalendar? {
        val custom = timeZone ?: return AirshipCalendar(current)

        return when(val resolution = custom.resolve(current)) {
            is TimeZone.Resolution.Resolved -> AirshipCalendar(resolution.timeZone)
            is TimeZone.Resolution.Error -> {
                return when(resolution.mode) {
                    TimeZone.FailureMode.SKIP -> null
                    TimeZone.FailureMode.ERROR ->
                        throw IllegalArgumentException("Unable to resolve time zone: $timeZone")
                }
            }
        }
    }

    @Throws(IllegalArgumentException::class)
    internal abstract fun resolve(date: Date, current: java.util.TimeZone): DateRange?

    internal companion object {
        private const val TYPE = "type"
        private const val TIME_RANGE = "time_range"
        private const val TIME_ZONE = "time_zone"

        private const val MONTHS = "months"
        private const val DAYS_OF_WEEK = "days_of_week"
        private const val DAYS_OF_MONTH = "days_of_month"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): Rule {
            val content = value.requireMap()

            return try {
                when(Type.fromJson(content.require(TYPE))) {
                    Type.DAILY -> Daily.fromJson(value)
                    Type.WEEKLY -> Weekly.fromJson(value)
                    Type.MONTHLY -> Monthly.fromJson(value)
                }
            } catch (ex: IllegalArgumentException) {
                throw JsonException("Invalid parameter", ex)
            }
        }
    }

    internal enum class Type(val jsonValue: String): JsonSerializable {
        DAILY("daily"),
        WEEKLY("weekly"),
        MONTHLY("monthly");

        override fun toJsonValue(): JsonValue = JsonValue.wrap(jsonValue)

        companion object {
            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Type {
                return try {
                    entries.first { it.jsonValue == value.requireString() }
                } catch (ex: NoSuchElementException) {
                    throw JsonException("Invalid rule type $value", ex)
                }
            }
        }
    }

    internal data class TimeRange(
        internal val startHour: Int,
        internal val startMinute: Int = 0,
        internal val endHour: Int,
        internal val endMinute: Int = 0
    ) : JsonSerializable {

        internal companion object {
            private const val START_HOUR = "start_hour"
            private const val START_MINUTE = "start_minute"
            private const val END_HOUR = "end_hour"
            private const val END_MINUTE = "end_minute"

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): TimeRange {
                val content = value.requireMap()

                return try {
                    TimeRange(
                        startHour = content.requireField(START_HOUR),
                        startMinute = content.optionalField(START_MINUTE) ?: 0,
                        endHour = content.requireField(END_HOUR),
                        endMinute = content.optionalField(END_MINUTE) ?: 0
                    )
                }  catch (ex: IllegalArgumentException) {
                    throw JsonException("Invalid parameter", ex)
                }
            }
        }

        init {
            require(startHour in 0..23) {
                "Invalid startHour ($startHour), must be [0-23]"
            }
            require(startMinute in 0..59) {
                "Invalid startMinute ($startMinute), must be [0-59]"
            }
            require(endHour in 0..23) {
                "Invalid endHour ($endHour), must be [0-23]"
            }
            require(endMinute in 0..59) {
                "Invalid endMinute ($endMinute), must be [0-59]"
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

        val start: Int get() = startHour * 60 * 60 + startMinute * 60
        val end: Int get() = endHour * 60 * 60 + endMinute * 60
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public sealed class TimeZone(internal val type: Type): JsonSerializable {

        internal sealed class Resolution {
            data class Resolved(val timeZone: java.util.TimeZone): Resolution()
            data class Error(val mode: FailureMode): Resolution()
        }

        internal data object Utc: TimeZone(Type.UTC) {

            override fun resolve(current: java.util.TimeZone): Resolution {
                return Resolution.Resolved(SimpleTimeZone(0, "UTC"))
            }

            override fun toJsonValue(): JsonValue = jsonMapOf(TYPE to type).toJsonValue()
        }

        internal data class Identifiers(
            val ids: List<String>,
            val secondsFromUtc: Duration? = null,
            val onFailure: FailureMode = FailureMode.ERROR)
            : TimeZone(Type.IDENTIFIER) {

            override fun toJsonValue(): JsonValue = jsonMapOf(
                TYPE to type,
                IDENTIFIERS to ids,
                FALLBACK_SECONDS_FROM_UTC to secondsFromUtc,
                ON_FAILURE to onFailure
            ).toJsonValue()

            companion object {
                private const val IDENTIFIERS = "identifiers"
                private const val ON_FAILURE = "on_failure"
                private const val FALLBACK_SECONDS_FROM_UTC = "fallback_seconds_from_utc"

                @Throws(JsonException::class)
                fun fromJson(value: JsonValue): Identifiers {
                    val content = value.requireMap()

                    return Identifiers(
                        ids = content.require(IDENTIFIERS).requireList().map { it.requireString() },
                        secondsFromUtc = content.get(FALLBACK_SECONDS_FROM_UTC)?.getInt(0)?.seconds,
                        onFailure = FailureMode.fromJson(content.require(ON_FAILURE))
                    )
                }
            }

            override fun resolve(current: java.util.TimeZone): Resolution {
                val availableIds = java.util.TimeZone.getAvailableIDs()
                val identifier = ids.firstOrNull { availableIds.contains(it) }

                val result = identifier?.let { java.util.TimeZone.getTimeZone(it) }
                    ?: secondsFromUtc?.let { SimpleTimeZone(secondsFromUtc.inWholeMilliseconds.toInt(), "USR") }

                return result?.let { Resolution.Resolved(it) } ?: Resolution.Error(onFailure)
            }
        }

        internal data object Local: TimeZone(Type.LOCAL) {

            override fun resolve(current: java.util.TimeZone): Resolution {
                return Resolution.Resolved(java.util.TimeZone.getDefault())
            }

            override fun toJsonValue(): JsonValue = jsonMapOf(TYPE to type).toJsonValue()
        }

        internal abstract fun resolve(current: java.util.TimeZone): Resolution

        internal companion object {
            private const val TYPE = "type"

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): TimeZone {
                val content = value.requireMap()
                return when(Type.fromJson(content.require(TYPE))) {
                    Type.UTC -> Utc
                    Type.LOCAL -> Local
                    Type.IDENTIFIER -> Identifiers.fromJson(value)
                }
            }
        }

        internal enum class Type(val jsonValue: String): JsonSerializable {
            UTC("utc"),
            LOCAL("local"),
            IDENTIFIER("identifiers");

            override fun toJsonValue(): JsonValue = JsonValue.wrap(jsonValue)

            companion object {
                @Throws(JsonException::class)
                fun fromJson(value: JsonValue): Type {
                    return try {
                        entries.first { it.jsonValue == value.requireString() }
                    } catch (ex: NoSuchElementException) {
                        throw JsonException("Invalid failure timezone type $value", ex)
                    }
                }
            }
        }

        internal enum class FailureMode(val jsonValue: String): JsonSerializable {
            ERROR("error"),
            SKIP("skip");

            override fun toJsonValue(): JsonValue = JsonValue.wrap(jsonValue)

            companion object  {
                @Throws(JsonException::class)
                fun fromJson(value: JsonValue): FailureMode {
                    return try {
                        entries.first { it.jsonValue == value.requireString() }
                    } catch (ex: NoSuchElementException) {
                        throw JsonException("Invalid failure mode $value", ex)
                    }
                }
            }
        }
    }
}

internal class AirshipCalendar(timeZone: TimeZone) {
    private val calendar: Calendar = Calendar.getInstance(timeZone)

    fun startOfDay(date: Date, addingDays: Int = 0): Calendar {
        return calendar.startOfDay(date).also { it.add(Calendar.DAY_OF_YEAR, addingDays) }
    }

    fun remainingDay(date: Date): DateRange {
        return DateRange(
            startDate = calendar.copyForDate(date).time,
            endDate = startOfDay(date, 1).time
        )
    }

    internal fun dateCalendar(date: Date, hour: Int, minute: Int): Calendar {
        return startOfDay(date).also {
            it.set(Calendar.HOUR_OF_DAY, hour)
            it.set(Calendar.MINUTE, minute)
            it.set(Calendar.SECOND, 0)
            it.set(Calendar.MILLISECOND, 0)
        }
    }

    // Returns the date interval for the given date and timeRange. If the
    // date is passed the time range, the DateInterval will be for the next day.
    fun dateInterval(date: Date, timeRange: Rule.TimeRange): DateRange {
        if (timeRange.start == timeRange.end) {
            val todayStart = dateCalendar(
                date = date,
                hour = timeRange.startHour,
                minute = timeRange.startMinute)

            if (todayStart.time == date) {
                return DateRange(todayStart, 1.seconds)
            } else {
                val tomorrowStart = dateCalendar(
                    date = startOfDay(date, addingDays = 1).time,
                    hour = timeRange.startHour,
                    minute = timeRange.startMinute
                )

                return DateRange(tomorrowStart, 1.seconds)
            }
        }

        // start: 23, end: 1
        val yesterdayInterval = DateRange.yesterday(this, date, timeRange)
        if (yesterdayInterval.contains(date)) {
            return yesterdayInterval
        }

        val todayInterval = DateRange.today(this, date, timeRange)
        if (todayInterval.contains(date) || todayInterval.startDate >= date) {
            return todayInterval
        }

        return DateRange.tomorrow(this, date, timeRange)
    }

    // Returns the current date if it matches the weekdays,
    // or the date of the start of the next requested weekday
    fun nextDate(date: Date, weekdays: List<Int>): Date {
        val copy = calendar.copyForDate(date)

        val currentWeekday = copy.get(Calendar.DAY_OF_WEEK)
        val sortedWeekdays = weekdays.sorted()
        val targetWeekday = sortedWeekdays
            .firstOrNull { it >= currentWeekday }
            ?: sortedWeekdays.firstOrNull()
            ?: currentWeekday

        // Mod it with number of days in the week
        val daysUntilNextSlot = if (targetWeekday >= currentWeekday) {
            targetWeekday - currentWeekday
        } else {
            targetWeekday + (7 - currentWeekday)
        }

        return if (daysUntilNextSlot > 0) {
            startOfDay(date, addingDays = daysUntilNextSlot).time
        } else {
            date
        }
    }

    fun nextDate(date: Date, months: List<Int>?, days: List<Int>?): Date {
        if (!(months?.isNotEmpty() == true || days?.isNotEmpty() == true)) {
            return date
        }

        val copy = calendar.copyForDate(date)

        val currentDay = copy.get(Calendar.DAY_OF_MONTH)
        val currentMonth = copy.get(Calendar.MONTH)

        val sortedMonths = months?.sorted() ?: emptyList()
        val sortedDays = days?.sorted() ?: emptyList()

        val targetMonth = sortedMonths
            .firstOrNull { it >= currentMonth }
            ?: sortedMonths.firstOrNull()
            ?: currentMonth

        var targetDay = sortedDays.firstOrNull { it >= currentDay }

        // Our target month is this month
        if (targetMonth == currentMonth) {
            if (sortedDays.isEmpty() && targetDay == null) {
                return date
            } else if (targetDay != null) {
                return if (targetDay == currentDay) {
                    date
                } else {
                    startOfDay(date, (targetDay - currentDay)).time
                }
            }
        }

        // Pick the earliest day
        targetDay = sortedDays.firstOrNull() ?: 1

        if (sortedMonths.isEmpty()) {
            return (copy.nextMatching(date, day = targetDay) ?: copy.distantFuture()).time
        }

        return sortedMonths
            .mapNotNull { copy.nextMatching(date, month = it, day = targetDay) }
            .minOfOrNull { it.time }
            ?: copy.distantFuture().time
    }
}

internal class DateRange(
    val startDate: Date,
    val endDate: Date
) {
    constructor(calendar: Calendar, duration: Duration): this(
        startDate = calendar.time,
        endDate = calendar.addingSeconds(duration.inWholeSeconds.toInt()).time
    )

    fun contains(date: Date): Boolean {
        return date in startDate..<endDate
    }

    fun intersection(range: DateRange): DateRange? {
        if (startDate > range.endDate || endDate < range.startDate) {
            return null
        }

        return DateRange(
            startDate = maxOf(startDate, range.startDate),
            endDate = minOf(endDate, range.endDate)
        )
    }

    companion object {
        fun today(
            calendar: AirshipCalendar,
            date: Date,
            timeRange: Rule.TimeRange): DateRange {
            return intervalWithOffset(calendar, date, timeRange, 0)
        }

        fun yesterday(
            calendar: AirshipCalendar,
            date: Date,
            timeRange: Rule.TimeRange): DateRange {
            return intervalWithOffset(calendar, date, timeRange, -1)
        }

        fun tomorrow(
            calendar: AirshipCalendar,
            date: Date,
            timeRange: Rule.TimeRange): DateRange {
            return intervalWithOffset(calendar, date, timeRange, 1)
        }

        private fun intervalWithOffset(
            calendar: AirshipCalendar,
            date: Date,
            timeRange: Rule.TimeRange,
            addingDays: Int = 0): DateRange {

            val endDay = if (timeRange.start > timeRange.end) {
                addingDays + 1
            } else {
                addingDays
            }

            return DateRange(
                startDate = calendar.dateCalendar(
                    date = calendar.startOfDay(date, addingDays).time,
                    hour = timeRange.startHour,
                    minute = timeRange.startMinute
                ).time,
                endDate = calendar.dateCalendar(
                    date = calendar.startOfDay(date, endDay).time,
                    hour = timeRange.endHour,
                    minute = timeRange.endMinute
                ).time
            )
        }
    }
}

private fun Calendar.startOfDay(date: Date): Calendar {
    return (this.clone() as Calendar).apply {
        timeInMillis = date.time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

private fun Calendar.copyForDate(date: Date): Calendar {
    return (this.clone() as Calendar).apply {
        timeInMillis = date.time
    }
}

private fun Calendar.addingSeconds(seconds: Int): Calendar {
    return this.also { it.add(Calendar.SECOND, seconds) }
}

private fun Calendar.distantFuture(): Calendar {
    return this.also { add(Calendar.YEAR, 1) }
}

private fun Calendar.nextMatching(date: Date, month: Int? = null, day: Int): Calendar? {
    fun canAccept(calendar: Calendar): Boolean {
        if (calendar.time < date) {
            return false
        }

        if (month != null && calendar.get(Calendar.MONTH) != month) {
            return false
        }

        if (calendar.get(Calendar.DAY_OF_MONTH) != day) {
            return false
        }

        return true
    }

    fun setDesiredComponents(copy: Calendar) {
        copy.apply {
            month?.let { set(Calendar.MONTH, it) }
            set(Calendar.DAY_OF_MONTH, day)
        }
    }

    var result = startOfDay(date)
    setDesiredComponents(result)

    if (canAccept(result)) {
        return result
    }

    if (month == null) {
        for (index in 1..<Calendar.UNDECIMBER) {
            result = copyForDate(date)
            result.add(Calendar.MONTH, index)
            setDesiredComponents(result)

            if (canAccept(result)) {
                return result
            }
        }
    }

    result.add(Calendar.YEAR, 1)
    setDesiredComponents(result)

    return if (canAccept(result)) {
        result
    } else {
        null
    }
}

private fun Date.durationSince(other: Date): Duration {
    return (time - other.time).milliseconds
}
