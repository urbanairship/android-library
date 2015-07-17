/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.location;

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Size;

import com.urbanairship.Logger;
import com.urbanairship.analytics.Event;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

/**
 * A UAProximityRegion defines a proximity region with an identifier, major and minor and optional
 * proximityRegion and/or circularRegion.
 */
public class RegionEvent extends Event {
    /**
     * The event type.
     */
    public static final String TYPE = "region_event";

    /**
     * The region ID key.
     */
    private static final String REGION_ID = "region_id";

    /**
     * The region source key.
     */
    private static final String SOURCE = "source";

    /**
     * The boundary event key.
     */
    private static final String BOUNDARY_EVENT = "action";

    /**
     * The region event latitude key.
     */
    private static final String LATITUDE = "latitude";

    /**
     * The region event longitude key.
     */
    private static final String LONGITUDE = "longitude";

    /**
     * The proximity region key.
     */
    private static final String PROXIMITY_REGION = "proximity";

    /**
     * The circular region key.
     */
    private static final String CIRCULAR_REGION = "circular_region";

    /**
     * The circular region radius key.
     */
    private static final String CIRCULAR_REGION_RADIUS = "radius";

    /**
     * The proximity region ID key.
     */
    private static final String PROXIMITY_REGION_ID = "proximity_id";

    /**
     * The proximity region major key.
     */
    private static final String PROXIMITY_REGION_MAJOR = "major";

    /**
     * The proximity region minor key.
     */
    private static final String PROXIMITY_REGION_MINOR = "minor";

    /**
     * The proximity region RSSI key.
     */
    private static final String PROXIMITY_REGION_RSSI = "rssi";

    /**
     * Source of the region definition.
     */
    private final String source;

    /**
     * The ID of the region.
     */
    private final String regionId;

    /**
     * The type of boundary crossing event.
     */
    private final int boundaryEvent;

    /**
     * A circular region with a radius, latitude and longitude from its center.
     */
    private CircularRegion circularRegion;

    /**
     * A proximity region with an identifier, major and minor.
     */
    private ProximityRegion proximityRegion;

    @IntDef({BOUNDARY_EVENT_ENTER, BOUNDARY_EVENT_EXIT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Boundary {}

    /**
     * Enter boundary event.
     */
    public static final int BOUNDARY_EVENT_ENTER = 1;

    /**
     * Exit boundary event.
     */
    public static final int BOUNDARY_EVENT_EXIT = 2;

    /**
     * The maximum length for any region event string.
     */
    public static final int MAX_CHARACTER_LENGTH = 255;

    /**
     * The maximum latitude for a region in degrees.
     */
    public static final double MAX_LATITUDE = 90.0;

    /**
     * The minimum latitude for a region in degrees.
     */
    public static final double MIN_LATITUDE = -90.0;

    /**
     * The maximum longitude for a region in degrees.
     */
    public static final double MAX_LONGITUDE = 180.0;

    /**
     * The minimum longitude for a region in degrees.
     */
    public static final double MIN_LONGITUDE = -180.0;

    /**
     * Constructor for creating a region event.
     *
     * @param regionId The ID of the region object.
     * @param source The source of the region definition.
     * @param boundaryEvent The type of boundary crossing event.
     */
    public RegionEvent(@NonNull @Size(max = MAX_CHARACTER_LENGTH) String regionId,
                       @NonNull @Size(max = MAX_CHARACTER_LENGTH) String source,
                       @Boundary int boundaryEvent) {

        this.regionId = regionId;
        this.source = source;
        this.boundaryEvent = boundaryEvent;
    }

    @Override
    public final String getType() {
        return TYPE;
    }

    @Override
    protected final JSONObject getEventData() {
        JSONObject data = new JSONObject();
        JSONObject proximityRegionData;
        JSONObject circularRegionData;

        if (!isValid()) {
            return null;
        }

        try {
            data.putOpt(REGION_ID, regionId);
            data.putOpt(SOURCE, source);

            data.putOpt(BOUNDARY_EVENT, boundaryEvent == 1 ? "enter" : "exit");

            if (proximityRegion != null && proximityRegion.isValid()) {
                proximityRegionData = new JSONObject();

                proximityRegionData.putOpt(PROXIMITY_REGION_ID, proximityRegion.getProximityId());
                proximityRegionData.putOpt(PROXIMITY_REGION_MAJOR, proximityRegion.getMajor());
                proximityRegionData.putOpt(PROXIMITY_REGION_MINOR, proximityRegion.getMinor());
                proximityRegionData.putOpt(LATITUDE, Double.toString(proximityRegion.getLatitude()));
                proximityRegionData.putOpt(LONGITUDE, Double.toString(proximityRegion.getLongitude()));
                proximityRegionData.putOpt(PROXIMITY_REGION_RSSI, proximityRegion.getRssi());

                data.putOpt(PROXIMITY_REGION, proximityRegionData);
            }

            if (circularRegion != null && circularRegion.isValid()) {
                circularRegionData = new JSONObject();

                circularRegionData.putOpt(CIRCULAR_REGION_RADIUS, String.format(Locale.US, "%.1f", circularRegion.getRadius()));
                circularRegionData.putOpt(LATITUDE,  String.format(Locale.US, "%.7f", circularRegion.getLatitude()));
                circularRegionData.putOpt(LONGITUDE, String.format(Locale.US, "%.7f", circularRegion.getLongitude()));

                data.putOpt(CIRCULAR_REGION, circularRegionData);
            }

        } catch (JSONException exception) {
            Logger.error("Error constructing JSON data for " + getType());
        }

        return data;
    }

    /**
     * Proximity region setter.
     *
     * @param proximityRegion The optional proximity region.
     */
    public void setProximityRegion(ProximityRegion proximityRegion) {
        this.proximityRegion = proximityRegion;
    }

    /**
     * Circular region setter.
     *
     * @param circularRegion The optional circular region.
     */
    public void setCircularRegion(CircularRegion circularRegion) {
        this.circularRegion = circularRegion;
    }

    /**
     * Validates region event character count.
     *
     * @param string The event string to be validated.
     */
    static boolean regionEventCharacterCountIsValid(String string) {
        return string.length() <= MAX_CHARACTER_LENGTH && string.length() > 0;
    }

    /**
     * Validates region event latitude.
     *
     * @param lat The latitude in degrees.
     * @return True if latitude is valid, false otherwise.
     */
    static boolean regionEventLatitudeIsValid(Double lat) {
        return lat <= RegionEvent.MAX_LATITUDE && lat >= RegionEvent.MIN_LATITUDE;
    }

    /**
     * Validates region event longitude.
     *
     * @param lon The longitude in degrees.
     * @return True if longitude is valid, false otherwise.
     */
    static boolean regionEventLongitudeIsValid(Double lon) {
        return lon <= RegionEvent.MAX_LONGITUDE && lon >= RegionEvent.MIN_LONGITUDE;
    }

    @Override
    public boolean isValid() {
        //noinspection ConstantConditions
        if (regionId == null || source == null) {
            Logger.error("The region ID and source must not be null.");
            return false;
        }

        if (!regionEventCharacterCountIsValid(regionId)) {
            Logger.error("The region ID must not be greater than " + MAX_CHARACTER_LENGTH +
                    " or less than " + 1 + " characters in length.");
            return false;
        }

        if (!regionEventCharacterCountIsValid(source)) {
            Logger.error("The source must not be greater than " + MAX_CHARACTER_LENGTH +
                    " or less than " + 1 + " characters in length.");
            return false;
        }

        if (boundaryEvent < 1 || boundaryEvent > 2) {
            Logger.error("The boundary event must either be an entrance (" + BOUNDARY_EVENT_ENTER +
                    ") or an exit (" + BOUNDARY_EVENT_EXIT + ").");
            return false;
        }

        return true;
    }
}
