/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonMap
import com.urbanairship.util.Clock
import com.urbanairship.util.FormatterUtils.toSecondsString
import java.util.Calendar
import java.util.Date
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/**
 * This abstract class encapsulates analytics events.
 */
public abstract class Event @JvmOverloads public constructor(
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    internal val timeMilliseconds: Long = clock.currentTimeMillis()
) {

    /**
     * Returns the UUID associated with the event.
     * @return an Event id String.
     */
    public val eventId: String = UUID.randomUUID().toString()

    public enum class Priority(private val value: Int) {
        LOW(0),
        NORMAL(1),
        HIGH(2)
    }

    public open val priority: Priority = Priority.NORMAL

    /**
     * Returns the timestamp associated with the event.
     * @return Seconds from the epoch, as a String.
     */
    public val time: String
        get() = timeMilliseconds.milliseconds.toSecondsString()

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public abstract val type: EventType

    /**
     * Override in `Event` sub-classes to create the event data.
     *
     * @param conversionData The conversion data.
     * @return The event data.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public abstract fun getEventData(conversionData: ConversionData): JsonMap

    /**
     * Returns the current time zone.
     *
     * @return The time zone as a long.
     */
    internal val timezone: Long
        get() {
            val tz = Calendar.getInstance().timeZone
            return (tz.getOffset(clock.currentTimeMillis()) / 1000).toLong()
        }

    /**
     * Indicates whether it is currently daylight savings time.
     *
     * @return `true` if it is currently daylight savings time, `false` otherwise.
     */
    internal val isDaylightSavingsTime: Boolean
        get() = Calendar.getInstance().timeZone.inDaylightTime(Date())

    /**
     * Validates the Event.
     *
     * @return True if valid, false otherwise.
     */
    public open fun isValid(): Boolean {
        return true
    }

    public companion object {

        //top level event fields
        public const val TYPE_KEY: String = "type"
        public const val TIME_KEY: String = "time"
        public const val DATA_KEY: String = "data"
        public const val EVENT_ID_KEY: String = "event_id"

        //event data fields
        public const val SESSION_ID_KEY: String = "session_id"
        public const val CARRIER_KEY: String = "carrier"
        public const val PUSH_ID_KEY: String = "push_id"
        public const val METADATA_KEY: String = "metadata"
        public const val TIME_ZONE_KEY: String = "time_zone"
        public const val DAYLIGHT_SAVINGS_KEY: String = "daylight_savings"
        public const val OS_VERSION_KEY: String = "os_version"
        public const val LIB_VERSION_KEY: String = "lib_version"
        public const val PACKAGE_VERSION_KEY: String = "package_version"
        public const val LAST_METADATA_KEY: String = "last_metadata"
    }
}
