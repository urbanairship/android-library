/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.location;

import android.support.annotation.FloatRange;

import com.urbanairship.Logger;

/**
 * A CircularRegion defines a circular region with a radius, latitude and longitude.
 */
public class CircularRegion {
    /**
     * The maximum radius for a region event in meters.
     */
    public final static int MAX_RADIUS = 100000;

    /**
     * The radius of the circular region in meters.
     */
    private final double radius;

    /**
     * The latitude from the center of the circular region in degrees.
     */
    private final double latitude;

    /**
     * The longitude from the center of the circular region in degrees.
     */
    private final double longitude;

    /**
     * Constructor for creating a circular region.
     *
     * @param radius The radius of the circular region in meters.
     * @param latitude The latitude of the circular region's center point in degrees.
     * @param longitude The longitude of the circular region's center point in degrees.
     */
    public CircularRegion(@FloatRange(from = 0.0, to = MAX_RADIUS) double radius,
                          @FloatRange(from = RegionEvent.MIN_LATITUDE, to = RegionEvent.MAX_LATITUDE) double latitude,
                          @FloatRange(from = RegionEvent.MIN_LONGITUDE, to = RegionEvent.MAX_LONGITUDE) double longitude) {

        this.radius = radius;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Gets the circular region's radius in meters.
     *
     * @return The radius in meters.
     */
    public double getRadius() {
        return this.radius;
    }

    /**
     * Gets the circular region's latitude in degrees.
     *
     * @return The latitude in degrees.
     */
    public double getLatitude() {
        return this.latitude;
    }

    /**
     * Gets the circular region's longitude in degrees.
     *
     * @return The longitude in degrees.
     */
    public double getLongitude() {
        return this.longitude;
    }

    /**
     * Validates the circular region object.
     *
     * @return True if the circular region is valid, false otherwise.
     */
    public boolean isValid() {
        if (this.radius > MAX_RADIUS || this.radius <= 0) {
            Logger.error("The radius must be greater than " + 0 +
                    " and less than or equal to " + MAX_RADIUS + " meters.");
            return false;
        }

        if (!RegionEvent.regionEventLatitudeIsValid(this.latitude)) {
          Logger.error("The latitude must be greater than or equal to " + RegionEvent.MIN_LATITUDE +
                  " and less than or equal to " + RegionEvent.MAX_LATITUDE + " degrees.");
            return false;
        }

        if (!RegionEvent.regionEventLongitudeIsValid(this.longitude)) {
            Logger.error("The longitude must be greater than or equal to " + RegionEvent.MIN_LONGITUDE +
                    " and less than or equal to " + RegionEvent.MAX_LONGITUDE + " degrees.");
            return false;
        }

        return true;
    }
}
