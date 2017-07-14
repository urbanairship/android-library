/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.location;

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Size;

import com.urbanairship.Logger;
import com.urbanairship.analytics.Event;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

/**
 * A RegionEvent defines a region with an identifier, major and minor and optional
 * proximityRegion and/or circularRegion.
 */
public class RegionEvent extends Event implements JsonSerializable {
    /**
     * The event type.
     */
    public static final String TYPE = "region_event";

    /**
     * The region ID key.
     */
    public static final String REGION_ID = "region_id";

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

    /**
     * Gets the boundary event type - will be either {@link RegionEvent#BOUNDARY_EVENT_ENTER} or
     * {@link RegionEvent#BOUNDARY_EVENT_EXIT}.
     *
     * @return The boundary event type.
     */
    public @Boundary int getBoundaryEvent() {
        return boundaryEvent;
    }

    @Override
    protected final JsonMap getEventData() {

        if (!isValid()) {
            return null;
        }

        JsonMap.Builder data = JsonMap.newBuilder()
                .put(REGION_ID, regionId)
                .put(SOURCE, source)
                .put(BOUNDARY_EVENT, boundaryEvent == 1 ? "enter" : "exit");

        if (proximityRegion != null && proximityRegion.isValid()) {
            JsonMap.Builder proximityRegionData = JsonMap.newBuilder()
                    .put(PROXIMITY_REGION_ID, proximityRegion.getProximityId())
                    .put(PROXIMITY_REGION_MAJOR, proximityRegion.getMajor())
                    .put(PROXIMITY_REGION_MINOR, proximityRegion.getMinor())
                    .putOpt(PROXIMITY_REGION_RSSI, proximityRegion.getRssi());

            if (proximityRegion.getLatitude() != null) {
                proximityRegionData.put(LATITUDE, Double.toString(proximityRegion.getLatitude()));
            }

            if (proximityRegion.getLongitude() != null) {
                proximityRegionData.put(LONGITUDE, Double.toString(proximityRegion.getLongitude()));
            }

            data.put(PROXIMITY_REGION, proximityRegionData.build());
        }

        if (circularRegion != null && circularRegion.isValid()) {
            JsonMap circularRegionData = JsonMap.newBuilder()
                    .put(CIRCULAR_REGION_RADIUS, String.format(Locale.US, "%.1f", circularRegion.getRadius()))
                    .put(LATITUDE, String.format(Locale.US, "%.7f", circularRegion.getLatitude()))
                    .put(LONGITUDE,  String.format(Locale.US, "%.7f", circularRegion.getLongitude()))
                    .build();

            data.put(CIRCULAR_REGION, circularRegionData);
        }

        return data.build();
    }

    @Override
    public JsonValue toJsonValue() {

        if (!isValid()) {
            return null;
        }

        JsonMap.Builder data = JsonMap.newBuilder()
                                      .put(REGION_ID, regionId)
                                      .put(SOURCE, source)
                                      .put(BOUNDARY_EVENT, boundaryEvent == 1 ? "enter" : "exit");

        if (proximityRegion != null && proximityRegion.isValid()) {
            JsonMap.Builder proximityRegionData = JsonMap.newBuilder()
                                                         .put(PROXIMITY_REGION_ID, proximityRegion.getProximityId())
                                                         .put(PROXIMITY_REGION_MAJOR, proximityRegion.getMajor())
                                                         .put(PROXIMITY_REGION_MINOR, proximityRegion.getMinor())
                                                         .putOpt(PROXIMITY_REGION_RSSI, proximityRegion.getRssi())
                                                         .putOpt(LATITUDE, proximityRegion.getLatitude())
                                                         .putOpt(LATITUDE, proximityRegion.getLatitude());

            data.put(RegionEvent.PROXIMITY_REGION, proximityRegionData.build());
        }

        if (circularRegion != null && circularRegion.isValid()) {
            JsonMap circularRegionData = JsonMap.newBuilder()
                                                .put(CIRCULAR_REGION_RADIUS, circularRegion.getRadius())
                                                .put(LATITUDE, circularRegion.getLatitude())
                                                .put(LONGITUDE,  circularRegion.getLongitude())
                                                .build();

            data.put(CIRCULAR_REGION, circularRegionData);
        }

        return data.build().toJsonValue();
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

    @Override
    @Priority
    public int getPriority() {
        return HIGH_PRIORITY;
    }
}
