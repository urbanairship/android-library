/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.json.JsonMap
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.FormatterUtils.toSecondsString
import java.time.Duration as JavaDuration
import java.time.Instant
import kotlin.time.toKotlinDuration

/**
 * A screen tracking event allows users to track an activity by associating a
 * screen identifier within an activity's onStart callback.
 */
internal class ScreenTrackingEvent(
    private val screen: String,
    private val previousScreen: String?,
    private val startTime: Instant,
    private val stopTime: Instant
) : Event() {

    override fun isValid(): Boolean {
        if (screen.length !in 1..SCREEN_TRACKING_EVENT_MAX_CHARACTERS) {
            UALog.e("Screen identifier string must be between 1 and 255 characters long.")
            return false
        }

        if (startTime.isAfter(stopTime)) {
            UALog.e("Screen tracking duration must be positive or zero.")
            return false
        }

        return true
    }

    override val type: EventType = EventType.SCREEN_TRACKING

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun getEventData(context: Context, conversionData: ConversionData): JsonMap = jsonMapOf(
        SCREEN_KEY to screen,
        PREVIOUS_SCREEN_KEY to previousScreen,
        START_TIME_KEY to startTime.toSecondsString(),
        STOP_TIME_KEY to stopTime.toSecondsString(),
        DURATION_KEY to JavaDuration.between(startTime, stopTime).toKotlinDuration().toSecondsString()
    )

    companion object {

        /**
         * The maximum screen tracking event identifier length.
         */
        private const val SCREEN_TRACKING_EVENT_MAX_CHARACTERS = 255

        /**
         * The screen key.
         */
        private const val SCREEN_KEY = "screen"

        /**
         * The previous screen key.
         */
        private const val PREVIOUS_SCREEN_KEY = "previous_screen"

        /**
         * The start time key.
         */
        private const val START_TIME_KEY = "entered_time"

        /**
         * The stop time key.
         */
        private const val STOP_TIME_KEY = "exited_time"

        /**
         * The duration key.
         */
        private const val DURATION_KEY = "duration"
    }
}
