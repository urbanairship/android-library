/* Copyright Airship and Contributors */

package com.urbanairship.analytics.location;

import com.urbanairship.Logger;
import com.urbanairship.analytics.Event;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.Checks;
import com.urbanairship.util.UAStringUtil;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.Size;

/**
 * A RegionEvent defines a region with an identifier, major and minor and optional
 * proximityRegion and/or circularRegion.
 */
public class RegionEvent extends Event implements JsonSerializable {

    /**
     * The event type.
     */
    @NonNull
    public static final String TYPE = "region_event";

    /**
     * The region ID key.
     */
    @NonNull
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
     * A circular region with a radius, latitude and longitude.
     */
    private final CircularRegion circularRegion;

    /**
     * A proximity region with an identifier, major and minor.
     */
    private final ProximityRegion proximityRegion;


    private RegionEvent(@NonNull RegionEvent.Builder builder) {
        this.regionId = builder.regionId;
        this.source = builder.source;
        this.boundaryEvent = builder.boundaryEvent;
        this.circularRegion = builder.circularRegion;
        this.proximityRegion = builder.proximityRegion;
    }

    /**
     * Builder factory method.
     *
     * @return A new builder instance.
     */
    @NonNull
    public static Builder newBuilder() {
        return new Builder();
    }

    @IntDef({ BOUNDARY_EVENT_ENTER, BOUNDARY_EVENT_EXIT })
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

    @NonNull
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
    public @Boundary
    int getBoundaryEvent() {
        return boundaryEvent;
    }

    /**
     * @hide
     */
    @NonNull
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public final JsonMap getEventData() {
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
                                                .put(LONGITUDE, String.format(Locale.US, "%.7f", circularRegion.getLongitude()))
                                                .build();

            data.put(CIRCULAR_REGION, circularRegionData);
        }

        return data.build();
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return getEventData().toJsonValue();
    }

    /**
     * Validates region event character count.
     *
     * @param string The event string to be validated.
     */
    static boolean regionEventCharacterCountIsValid(@NonNull String string) {
        return string.length() <= MAX_CHARACTER_LENGTH && string.length() > 0;
    }

    /**
     * Validates region event latitude.
     *
     * @param lat The latitude in degrees.
     * @return True if latitude is valid, false otherwise.
     */
    static boolean regionEventLatitudeIsValid(@NonNull Double lat) {
        return lat <= RegionEvent.MAX_LATITUDE && lat >= RegionEvent.MIN_LATITUDE;
    }

    /**
     * Validates region event longitude.
     *
     * @param lon The longitude in degrees.
     * @return True if longitude is valid, false otherwise.
     */
    static boolean regionEventLongitudeIsValid(@NonNull Double lon) {
        return lon <= RegionEvent.MAX_LONGITUDE && lon >= RegionEvent.MIN_LONGITUDE;
    }

    @Override
    public boolean isValid() {
        if (regionId == null || source == null) {
            Logger.error("The region ID and source must not be null.");
            return false;
        }

        if (!regionEventCharacterCountIsValid(regionId)) {
            Logger.error("The region ID must not be greater than %s or less than %s characters in length.", MAX_CHARACTER_LENGTH, 1);
            return false;
        }

        if (!regionEventCharacterCountIsValid(source)) {
            Logger.error("The source must not be greater than %s or less than %s characters in length.", MAX_CHARACTER_LENGTH, 1);
            return false;
        }

        if (boundaryEvent < 1 || boundaryEvent > 2) {
            Logger.error("The boundary event must either be an entrance (%s) or an exit (%s).", BOUNDARY_EVENT_ENTER, BOUNDARY_EVENT_EXIT);
            return false;
        }

        return true;
    }

    @Override
    @Priority
    public int getPriority() {
        return HIGH_PRIORITY;
    }

    /**
     * Builder class for {@link RegionEvent} Objects.
     */
    public static class Builder {

        /**
         * The ID of the region.
         */
        private String regionId;

        /**
         * Source of the region definition.
         */
        private String source;

        /**
         * The type of boundary crossing event.
         */
        private int boundaryEvent;

        /**
         * A circular region with a radius, latitude and longitude.
         */
        private CircularRegion circularRegion;

        /**
         * A proximity region with an identifier, major and minor.
         */
        private ProximityRegion proximityRegion;

        /**
         * Creates a new region event builder
         * <p>
         * The region ID and source must be between 1 and 255 characters or the event will be invalid.
         *
         * 255 characters.
         */
        private Builder() {
        }

        /**
         * Region identifier setter.
         *
         * @param regionId The region identifier.
         */
        @NonNull
        public Builder setRegionId(@NonNull @Size(min = 1, max = MAX_CHARACTER_LENGTH) String regionId) {
            this.regionId = regionId;
            return this;
        }

        /**
         * Region event source setter.
         *
         * @param source The region event source.
         */
        @NonNull
        public Builder setSource(@NonNull @Size(min = 1, max = MAX_CHARACTER_LENGTH) String source) {
            this.source = source;
            return this;
        }

        /**
         * Region boundary event setter.
         *
         * @param boundaryEvent The region boundary event.
         */
        @NonNull
        public Builder setBoundaryEvent(int boundaryEvent) {
            this.boundaryEvent = boundaryEvent;
            return this;
        }

        /**
         * Circular region setter.
         *
         * @param circularRegion The optional circular region.
         */
        @NonNull
        public Builder setCircularRegion(@Nullable CircularRegion circularRegion) {
            this.circularRegion = circularRegion;
            return this;
        }

        /**
         * Proximity region setter.
         *
         * @param proximityRegion The optional proximity region.
         */
        @NonNull
        public Builder setProximityRegion(@Nullable ProximityRegion proximityRegion) {
            this.proximityRegion = proximityRegion;
            return this;
        }

        /**
         * Builds the region event.
         *
         * @return The built region event.
         * @throws java.lang.IllegalArgumentException if the region ID or source is null, empty, or exceeds
         * max length
         */
        @NonNull
        public RegionEvent build() {
            Checks.checkNotNull(regionId, "Region identifier must not be null");
            Checks.checkNotNull(source, "Region event source must not be null");
            Checks.checkArgument(!UAStringUtil.isEmpty(regionId), "Region identifier must be greater than 0 characters.");
            Checks.checkArgument(regionId.length() <= MAX_CHARACTER_LENGTH, "Region identifier exceeds max identifier length: " + MAX_CHARACTER_LENGTH);
            Checks.checkArgument(!UAStringUtil.isEmpty(source), "Source must be greater than 0 characters.");
            Checks.checkArgument(source.length() <= MAX_CHARACTER_LENGTH, "Source exceeds max source length: " + MAX_CHARACTER_LENGTH);

            // Check boundary event
            if (boundaryEvent < 1 || boundaryEvent > 2) {
                throw new IllegalArgumentException("The boundary event must either be an entrance (" + BOUNDARY_EVENT_ENTER +
                        ") or an exit (" + BOUNDARY_EVENT_EXIT + ").");
            }

            return new RegionEvent(this);
        }

    }

}
