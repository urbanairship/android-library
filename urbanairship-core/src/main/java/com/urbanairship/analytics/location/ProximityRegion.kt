/* Copyright Airship and Contributors */
package com.urbanairship.analytics.location

import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import com.urbanairship.UALog

/**
 * A ProximityRegion defines a proximity region with an identifier, major, minor
 * and optional latitude, longitude and RSSI.
 */
public class ProximityRegion
/**
 * Constructor for creating proximity region.
 *
 * @param proximityId The ID of the region object.
 * @param major The major.
 * @param minor The minor.
 */ public constructor(
    /**
     * The proximity region's identifier.
     */
    @JvmField public val proximityId: String,
    /**
     * The proximity region's major.
     */
    @JvmField @param:IntRange(from = 0, to = MAX_MAJOR_MINOR_VALUE.toLong())
    public val major: Int,
    /**
     * The proximity region's minor.
     */
    @JvmField @param:IntRange(from = 0, to = MAX_MAJOR_MINOR_VALUE.toLong())
    public val minor: Int
) {

    /**
     * The proximity region's latitude in degrees.
     */
    public var latitude: Double? = null
        private set

    /**
     * The proximity region's longitude in degrees.
     */
    public var longitude: Double? = null
        private set

    /**
     * The proximity region's received signal strength indication in dBm.
     */
    public var rssi: Int? = null
        /**
         * Sets the proximity region's received signal strength indication.
         *
         * @param rssi The proximity region's received signal strength indication.
         */
        set(rssi) {
            if (rssi == null) {
                field = null
                return
            }

            if (rssi > MAX_RSSI || rssi < MIN_RSSI) {
                UALog.e(
                    "The rssi must be greater than or equal to $MIN_RSSI " +
                            "and less than or equal to $MAX_RSSI dBm."
                )
                field = null
                return
            }

            field = rssi
        }

    /**
     * Sets the proximity region's latitude and longitude.
     *
     * @param latitude The proximity region's latitude.
     * @param longitude The proximity region's longitude.
     */
    public fun setCoordinates(
        @FloatRange(from = RegionEvent.MIN_LATITUDE, to = RegionEvent.MAX_LATITUDE)
        latitude: Double?,

        @FloatRange(from = RegionEvent.MIN_LONGITUDE, to = RegionEvent.MAX_LONGITUDE)
        longitude: Double?
    ) {
        if (latitude == null || longitude == null) {
            this.latitude = null
            this.longitude = null
            return
        }

        if (!RegionEvent.regionEventLatitudeIsValid(latitude)) {
            UALog.e(
                "The latitude must be greater than or equal to ${RegionEvent.MIN_LATITUDE} " +
                        "and less than or equal to ${RegionEvent.MAX_LATITUDE} degrees."
            )
            this.latitude = null
            return
        }

        if (!RegionEvent.regionEventLongitudeIsValid(longitude)) {
            UALog.e(
                "The longitude must be greater than or equal to ${RegionEvent.MIN_LONGITUDE} " +
                        "and less than or equal to ${RegionEvent.MAX_LONGITUDE} degrees."
            )
            this.longitude = null
            return
        }

        this.latitude = latitude
        this.longitude = longitude
    }

    /**
     * Validates the proximity region object.
     */
    public val isValid: Boolean
        get() {
            if (!RegionEvent.regionEventCharacterCountIsValid(proximityId)) {
                UALog.e(
                    "The proximity ID must not be greater than ${RegionEvent.MAX_CHARACTER_LENGTH} " +
                            "or less than 1 characters in length."
                )
                return false
            }

            if (major !in 0..MAX_MAJOR_MINOR_VALUE) {
                UALog.e(
                    "The major must not be greater than $MAX_MAJOR_MINOR_VALUE or less than 0."
                )
                return false
            }

            if (minor !in 0..MAX_MAJOR_MINOR_VALUE) {
                UALog.e(
                    "The minor must not be greater than $MAX_MAJOR_MINOR_VALUE or less than 0."
                )
                return false
            }

            return true
        }

    private companion object {

        /**
         * The maximum proximity region major and minor value.
         */
        private const val MAX_MAJOR_MINOR_VALUE = 65535

        /**
         * The maximum RSSI for a proximity region in dBm.
         */
        private const val MAX_RSSI = 100

        /**
         * The minimum RSSI for a proximity region in dBm.
         */
        private const val MIN_RSSI = -100
    }
}
