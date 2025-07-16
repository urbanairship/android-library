/* Copyright Airship and Contributors */
package com.urbanairship.analytics.location

import androidx.annotation.FloatRange
import com.urbanairship.UALog

/**
 * A CircularRegion defines a circular region with a radius, latitude and longitude.
 */
public class CircularRegion
/**
 * Constructor for creating a circular region.
 *
 * @param radius The radius of the circular region in meters.
 * @param latitude The latitude of the circular region's center point in degrees.
 * @param longitude The longitude of the circular region's center point in degrees.
 */ public constructor(
    /**
     * The radius of the circular region in meters.
     */
    @JvmField @param:FloatRange(from = 0.0, to = MAX_RADIUS.toDouble())
    public val radius: Double,
    /**
     * The latitude from the center of the circular region in degrees.
     */
    @JvmField @param:FloatRange(from = RegionEvent.MIN_LATITUDE, to = RegionEvent.MAX_LATITUDE)
    public val latitude: Double,
    /**
     * The longitude from the center of the circular region in degrees.
     */
    @JvmField @param:FloatRange(from = RegionEvent.MIN_LONGITUDE, to = RegionEvent.MAX_LONGITUDE)
    public val longitude: Double
) {

    /**
     * Validates the circular region object.
     */
    public val isValid: Boolean
        get() {
            if (this.radius > MAX_RADIUS || this.radius <= 0) {
                UALog.e("The radius must be greater than 0 and less than or equal to $MAX_RADIUS meters.")
                return false
            }

            if (!RegionEvent.regionEventLatitudeIsValid(this.latitude)) {
                UALog.e(
                    "The latitude must be greater than or equal to ${RegionEvent.MIN_LATITUDE} " +
                            "and less than or equal to ${RegionEvent.MAX_LATITUDE} degrees."
                )
                return false
            }

            if (!RegionEvent.regionEventLongitudeIsValid(this.longitude)) {
                UALog.e(
                    "The longitude must be greater than or equal to ${RegionEvent.MIN_LONGITUDE} " +
                            "and less than or equal to ${RegionEvent.MAX_LONGITUDE} degrees."
                )
                return false
            }

            return true
        }

    public companion object {
        /**
         * The maximum radius for a region event in meters.
         */
        public const val MAX_RADIUS: Int = 100000
    }
}
