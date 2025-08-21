/* Copyright Airship and Contributors */
package com.urbanairship.push

import androidx.annotation.IntRange
import androidx.core.util.ObjectsCompat
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.util.Calendar
import java.util.Date

/**
 * Model object representing a quiet time interval.
 */
internal class QuietTimeInterval private constructor(builder: Builder) : JsonSerializable {

    private val startHour = builder.startHour
    private val startMin = builder.startMin
    private val endHour = builder.endHour
    private val endMin = builder.endMin

    /**
     * Determines whether we are currently in the middle of "Quiet Time".  Returns false if Quiet Time is disabled,
     * and evaluates whether or not the current date/time falls within the Quiet Time interval set by the user.
     *
     * @param now Reference time for determining if interval is in quiet time.
     * @return A boolean indicating whether it is currently "Quiet Time".
     */
    fun isInQuietTime(now: Calendar): Boolean {
        val start = Calendar.getInstance()
        start[Calendar.HOUR_OF_DAY] = startHour
        start[Calendar.MINUTE] = startMin
        start[Calendar.SECOND] = 0
        start[Calendar.MILLISECOND] = 0

        val end = Calendar.getInstance()
        end[Calendar.HOUR_OF_DAY] = endHour
        end[Calendar.MINUTE] = endMin
        end[Calendar.SECOND] = 0
        end[Calendar.MILLISECOND] = 0

        val copy = now.clone() as Calendar
        copy[Calendar.SECOND] = 0
        copy[Calendar.MILLISECOND] = 0

        // Equal to either start or end
        if (copy.compareTo(start) == 0 || copy.compareTo(end) == 0) {
            return true
        }

        // End is equal to start but now is not equal to either end or start
        if (end.compareTo(start) == 0) {
            return false
        }

        // End is after start
        if (end.after(start)) {
            return copy.after(start) && copy.before(end)
        }

        // End is before start
        return copy.before(end) || copy.after(start)
    }

    /**
     * Returns the Quiet Time interval currently set by the user.
     */
    val quietTimeIntervalDateArray: Array<Date>?
        get() {
            if (startHour == NOT_SET_VAL || startMin == NOT_SET_VAL || endHour == NOT_SET_VAL || endMin == NOT_SET_VAL) {
                return null
            }

            // Grab the start date.
            val start = Calendar.getInstance()
            start[Calendar.HOUR_OF_DAY] = startHour
            start[Calendar.MINUTE] = startMin
            start[Calendar.SECOND] = 0
            start[Calendar.MILLISECOND] = 0

            // Prepare the end date.
            val end = Calendar.getInstance()
            end[Calendar.HOUR_OF_DAY] = endHour
            end[Calendar.MINUTE] = endMin
            end[Calendar.SECOND] = 0
            end[Calendar.MILLISECOND] = 0

            val startDate = start.time
            val endDate = end.time
            return arrayOf(startDate, endDate)
        }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        START_HOUR_KEY to startHour,
        START_MIN_KEY to startMin,
        END_HOUR_KEY to endHour,
        END_MIN_KEY to endMin
    ).toJsonValue()

    override fun toString(): String {
        return "QuietTimeInterval{startHour=$startHour, startMin=$startMin, endHour=$endHour, endMin=$endMin}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) { return true }
        if (other == null || javaClass != other.javaClass) { return false }

        val that = other as QuietTimeInterval

        if (startHour != that.startHour) { return false }
        if (startMin != that.startMin) { return false }
        if (endHour != that.endHour) { return false }
        if (endMin != that.endMin) { return false }

        return true
    }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(startHour, startMin, endHour, endMin)
    }

    /**
     * QuietTimeInterval builder class.
     */
    class Builder {

        internal var startHour: Int = -1
            private set
        var startMin: Int = -1
            private set
        var endHour: Int = -1
            private set
        var endMin: Int = -1
            private set

        /**
         * Set the quiet time interval.
         *
         * @param startTime The interval start time as a Date.
         * @param endTime The interval end time as a Date.
         * @return The builder with the interval set.
         */
        fun setQuietTimeInterval(startTime: Date, endTime: Date): Builder {
            return this.also {
                val startCal = Calendar.getInstance()
                startCal.time = startTime
                it.startHour = startCal[Calendar.HOUR_OF_DAY]
                it.startMin = startCal[Calendar.MINUTE]

                val endCal = Calendar.getInstance()
                endCal.time = endTime
                it.endHour = endCal[Calendar.HOUR_OF_DAY]
                it.endMin = endCal[Calendar.MINUTE]
            }
        }

        /**
         * Set the quiet time interval start hour. Value should be between 0 and 23.
         *
         * @param startHour The start hour as an int.
         * @return The builder with the start hour set.
         */
        fun setStartHour(@IntRange(from = 0, to = 23) startHour: Int): Builder {
            return this.also { it.startHour = startHour }
        }

        /**
         * Set the quiet time interval start min. Value should be between 0 and 59.
         *
         * @param startMin The start min as an int.
         * @return The builder with the start min set.
         */
        fun setStartMin(@IntRange(from = 0, to = 59) startMin: Int): Builder {
            return this.also { it.startMin = startMin }
        }

        /**
         * Set the quiet time interval end hour.  Value should be between 0 and 23.
         *
         * @param endHour The end hour as an int.
         * @return The builder with the end hour set.
         */
        fun setEndHour(@IntRange(from = 0, to = 23) endHour: Int): Builder {
            return this.also { it.endHour = endHour }
        }

        /**
         * Set the quiet time interval end min. Value should be between 0 and 59.
         *
         * @param endMin The end min as an int.
         * @return The builder with the end min set.
         */
        fun setEndMin(@IntRange(from = 0, to = 59) endMin: Int): Builder {
            return this.also { it.endMin = endMin }
        }

        /**
         * Build the QuietTimeInterval instance.
         *
         * @return The QuietTimeInterval instance.
         */
        fun build(): QuietTimeInterval {
            return QuietTimeInterval(this)
        }
    }

    companion object {

        private const val START_HOUR_KEY = "start_hour"
        private const val START_MIN_KEY = "start_min"
        private const val END_HOUR_KEY = "end_hour"
        private const val END_MIN_KEY = "end_min"
        private const val NOT_SET_VAL = -1

        /**
         * Static helper method to deserialize JSON into a QuietTimeInterval instance.
         *
         * @param value The JSON value.
         * @return The deserialized QuietTimeInterval instance.
         */
        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): QuietTimeInterval {
            val jsonMap = value.requireMap()
            if (jsonMap.isEmpty) {
                throw JsonException("Invalid quiet time interval: $value")
            }

            return Builder()
                .setStartHour(jsonMap.opt(START_HOUR_KEY).getInt(NOT_SET_VAL))
                .setStartMin(jsonMap.opt(START_MIN_KEY).getInt(NOT_SET_VAL))
                .setEndHour(jsonMap.opt(END_HOUR_KEY).getInt(NOT_SET_VAL))
                .setEndMin(jsonMap.opt(END_MIN_KEY).getInt(NOT_SET_VAL))
                .build()
        }

        /**
         * Builder factory method.
         *
         * @return A new builder instance.
         */
        fun newBuilder(): Builder {
            return Builder()
        }
    }
}
