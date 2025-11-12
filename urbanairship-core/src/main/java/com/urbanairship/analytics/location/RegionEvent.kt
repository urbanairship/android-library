/* Copyright Airship and Contributors */
package com.urbanairship.analytics.location

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.Size
import com.urbanairship.UALog
import com.urbanairship.analytics.ConversionData
import com.urbanairship.analytics.Event
import com.urbanairship.analytics.EventType
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.util.Locale

/**
 * A RegionEvent defines a region with an identifier, major and minor and optional
 * proximityRegion and/or circularRegion.
 */
public class RegionEvent private constructor(
    /**
     * Source of the region definition.
     */
    private val source: String? = null,
    /**
     * The ID of the region.
     * * @hide
     */
    internal val regionId: String,

    /**
     * The type of boundary crossing event. Will be either [Boundary.ENTER] or [Boundary.EXIT].
     */
    public val boundaryEvent: Boundary,

    /**
     * A circular region with a radius, latitude and longitude.
     */
    private val circularRegion: CircularRegion?,

    /**
     * A proximity region with an identifier, major and minor.
     */
    private val proximityRegion: ProximityRegion?
) : Event(), JsonSerializable {

    public enum class Boundary(private val value: Int) {
        ENTER(1),
        EXIT(2);

        internal val reportValue: String
            get() = when(this) {
                ENTER -> "enter"
                EXIT -> "exit"
            }
    }

    override val type: EventType
        get() = when(boundaryEvent) {
            Boundary.ENTER -> EventType.REGION_ENTER
            Boundary.EXIT -> EventType.REGION_EXIT
        }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun getEventData(context: Context, conversionData: ConversionData): JsonMap {
        return createEventData()
    }

    override fun toJsonValue(): JsonValue {
        return createEventData().toJsonValue()
    }

    private fun createEventData(): JsonMap {
        val data = JsonMap.newBuilder()
            .put(REGION_ID, regionId)
            .put(SOURCE, source)
            .put(BOUNDARY_EVENT, boundaryEvent.reportValue)

        if (proximityRegion?.isValid == true) {
            data.put(PROXIMITY_REGION, jsonMapOf(
                PROXIMITY_REGION_ID to proximityRegion.proximityId,
                PROXIMITY_REGION_MAJOR to proximityRegion.major,
                PROXIMITY_REGION_MINOR to proximityRegion.minor,
                PROXIMITY_REGION_RSSI to proximityRegion.rssi,
                LATITUDE to proximityRegion.latitude?.toString(),
                LONGITUDE to proximityRegion.longitude?.toString()
            ))
        }

        if (circularRegion?.isValid == true) {
            data.put(CIRCULAR_REGION, jsonMapOf(
                CIRCULAR_REGION_RADIUS to String.format(Locale.US, "%.1f", circularRegion.radius),
                LATITUDE to String.format(Locale.US, "%.7f", circularRegion.latitude),
                LONGITUDE to String.format(Locale.US, "%.7f", circularRegion.longitude)
            ))
        }

        return data.build()
    }

    override fun isValid(): Boolean {
        if (source == null) {
            UALog.e("The source must not be null.")
            return false
        }

        if (!regionEventCharacterCountIsValid(regionId)) {
            UALog.e(
                "The region ID must not be greater than $MAX_CHARACTER_LENGTH or less than 1 characters in length."
            )
            return false
        }

        if (!regionEventCharacterCountIsValid(source)) {
            UALog.e(
                "The source must not be greater than $MAX_CHARACTER_LENGTH or less than 1 characters in length."
            )
            return false
        }

        return true
    }

    override val priority: Priority = Priority.HIGH

    /**
     * Builder class for [RegionEvent] Objects.
     */
    public class Builder(
        /**
         * The ID of the region.
         */
        @Size(min = 1, max = MAX_CHARACTER_LENGTH.toLong())
        public val regionId: String,

        /**
         * The type of boundary crossing event.
         */
        public var boundaryEvent: Boundary
    ) {

        /**
         * Source of the region definition.
         */
        public var source: String? = null

        /**
         * A circular region with a radius, latitude and longitude.
         */
        public var circularRegion: CircularRegion? = null

        /**
         * A proximity region with an identifier, major and minor.
         */
        public var proximityRegion: ProximityRegion? = null

        /**
         * Region event source setter.
         *
         * @param source The region event source.
         */
        public fun setSource(
            @Size(min = 1, max = MAX_CHARACTER_LENGTH.toLong())
            source: String
        ): Builder {
            return this.also { it.source = source }
        }

        /**
         * Region boundary event setter.
         *
         * @param boundaryEvent The region boundary event.
         */
        public fun setBoundaryEvent(boundaryEvent: Boundary): Builder {
            return this.also { it.boundaryEvent = boundaryEvent }
        }

        /**
         * Circular region setter.
         *
         * @param circularRegion The optional circular region.
         */
        public fun setCircularRegion(circularRegion: CircularRegion?): Builder {
            return this.also { it.circularRegion = circularRegion }
        }

        /**
         * Proximity region setter.
         *
         * @param proximityRegion The optional proximity region.
         */
        public fun setProximityRegion(proximityRegion: ProximityRegion?): Builder {
            return this.also { it.proximityRegion = proximityRegion }
        }

        /**
         * Builds the region event.
         *
         * @return The built region event.
         * @throws java.lang.IllegalArgumentException if the region ID or source is null, empty, or exceeds
         * max length
         */
        @Throws(IllegalArgumentException::class)
        public fun build(): RegionEvent {
            val source = this.source ?: throw IllegalArgumentException("Region event source must not be null")
            require(regionId.isNotEmpty()) {
                "Region identifier must be greater than 0 characters."
            }
            require(regionId.length <= MAX_CHARACTER_LENGTH) {
                "Region identifier exceeds max identifier length: $MAX_CHARACTER_LENGTH"
            }
            require(source.isNotEmpty()) {
                "Source must be greater than 0 characters."
            }
            require(source.length <= MAX_CHARACTER_LENGTH) {
                "Source exceeds max source length: $MAX_CHARACTER_LENGTH"
            }

            // Check boundary event
            return RegionEvent(
                regionId = regionId,
                source = source,
                boundaryEvent = boundaryEvent,
                circularRegion = circularRegion,
                proximityRegion = proximityRegion
            )
        }
    }

    public companion object {

        /**
         * The region ID key.
         */
        public const val REGION_ID: String = "region_id"

        /**
         * The region source key.
         */
        private const val SOURCE = "source"

        /**
         * The boundary event key.
         */
        private const val BOUNDARY_EVENT = "action"

        /**
         * The region event latitude key.
         */
        private const val LATITUDE = "latitude"

        /**
         * The region event longitude key.
         */
        private const val LONGITUDE = "longitude"

        /**
         * The proximity region key.
         */
        private const val PROXIMITY_REGION = "proximity"

        /**
         * The circular region key.
         */
        private const val CIRCULAR_REGION = "circular_region"

        /**
         * The circular region radius key.
         */
        private const val CIRCULAR_REGION_RADIUS = "radius"

        /**
         * The proximity region ID key.
         */
        private const val PROXIMITY_REGION_ID = "proximity_id"

        /**
         * The proximity region major key.
         */
        private const val PROXIMITY_REGION_MAJOR = "major"

        /**
         * The proximity region minor key.
         */
        private const val PROXIMITY_REGION_MINOR = "minor"

        /**
         * The proximity region RSSI key.
         */
        private const val PROXIMITY_REGION_RSSI = "rssi"

        /**
         * Builder factory method.
         *
         * @return A new builder instance.
         */
        @JvmStatic
        public fun newBuilder(
            regionId: String,
            boundaryEvent: Boundary
        ): Builder {
            return Builder(regionId, boundaryEvent)
        }

        /**
         * The maximum length for any region event string.
         */
        public const val MAX_CHARACTER_LENGTH: Int = 255

        /**
         * The maximum latitude for a region in degrees.
         */
        public const val MAX_LATITUDE: Double = 90.0

        /**
         * The minimum latitude for a region in degrees.
         */
        public const val MIN_LATITUDE: Double = -90.0

        /**
         * The maximum longitude for a region in degrees.
         */
        public const val MAX_LONGITUDE: Double = 180.0

        /**
         * The minimum longitude for a region in degrees.
         */
        public const val MIN_LONGITUDE: Double = -180.0

        /**
         * Validates region event character count.
         *
         * @param string The event string to be validated.
         */
        @JvmStatic
        public fun regionEventCharacterCountIsValid(string: String): Boolean {
            return string.length in 1..MAX_CHARACTER_LENGTH
        }

        /**
         * Validates region event latitude.
         *
         * @param lat The latitude in degrees.
         * @return True if latitude is valid, false otherwise.
         */
        @JvmStatic
        public fun regionEventLatitudeIsValid(lat: Double): Boolean {
            return lat in MIN_LATITUDE..MAX_LATITUDE
        }

        /**
         * Validates region event longitude.
         *
         * @param lon The longitude in degrees.
         * @return True if longitude is valid, false otherwise.
         */
        @JvmStatic
        public fun regionEventLongitudeIsValid(lon: Double): Boolean {
            return lon in MIN_LONGITUDE..MAX_LONGITUDE
        }
    }
}
