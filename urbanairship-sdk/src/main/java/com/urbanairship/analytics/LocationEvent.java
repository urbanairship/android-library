/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import android.location.Location;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;

import com.urbanairship.json.JsonMap;
import com.urbanairship.util.UAStringUtil;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

/**
 * This class captures all the necessary information for Urban Airship
 * {@link com.urbanairship.analytics.Analytics}.
 */
public class LocationEvent extends Event {

    @IntDef(value={
            UPDATE_TYPE_CONTINUOUS,
            UPDATE_TYPE_SINGLE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface UpdateType {}


    /**
     * Continuous location update type
     */
    public final static int UPDATE_TYPE_CONTINUOUS = 0;

    /**
     * Single location update type
     */
    public final static int UPDATE_TYPE_SINGLE = 1;

    static final String TYPE = "location";

    static final String LATITUDE_KEY = "lat";
    static final String LONGITUDE_KEY = "long";
    static final String REQUESTED_ACCURACY_KEY = "requested_accuracy";
    static final String UPDATE_TYPE_KEY = "update_type";
    static final String PROVIDER_KEY = "provider";
    static final String H_ACCURACY_KEY = "h_accuracy";
    static final String V_ACCURACY_KEY = "v_accuracy";
    static final String FOREGROUND_KEY = "foreground";
    static final String UPDATE_DISTANCE_KEY = "update_dist";


    private final String provider;
    private final String latitude;
    private final String longitude;
    private final String accuracy;
    private final String requestedAccuracy;
    private final String updateDistance;
    private final String foreground;
    private final @UpdateType int updateType;

    /**
     * Constructor for LocationEvent.
     *
     * @param location An instance of Location.
     * @param type The location's UpdateType.
     * @param userRequestedAccuracy The associated user-requested location accuracy.
     * @param updateDist The associated update distance.
     * @param isForeground If the location was recorded when the app was foregrounded or not.
     */
    public LocationEvent(@NonNull Location location, @UpdateType int type, int userRequestedAccuracy, int updateDist, boolean isForeground) {
        super();

        /*
         * We use Locale.US because it is always available, consistent, and is
         * what null locale defaults to. We avoid Location.convert because it
         * uses a decimal formatter with the current default locale.
         */
        latitude = String.format(Locale.US, "%.6f", location.getLatitude());
        longitude = String.format(Locale.US, "%.6f", location.getLongitude());

        provider = UAStringUtil.isEmpty(location.getProvider()) ? "UNKNOWN" : location.getProvider();
        accuracy = String.valueOf(location.getAccuracy());
        requestedAccuracy = userRequestedAccuracy >= 0 ? String.valueOf(userRequestedAccuracy) : "NONE";
        updateDistance = updateDist >= 0 ? String.valueOf(updateDist) : "NONE";
        foreground = isForeground ? "true" : "false";
        updateType = type;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    protected final JsonMap getEventData() {
        return JsonMap.newBuilder()
                .put(LATITUDE_KEY, latitude)
                .put(LONGITUDE_KEY, longitude)
                .put(REQUESTED_ACCURACY_KEY, requestedAccuracy)
                .put(UPDATE_TYPE_KEY, updateType == UPDATE_TYPE_CONTINUOUS ? "CONTINUOUS" : "SINGLE")
                .put(PROVIDER_KEY, provider)
                .put(H_ACCURACY_KEY, accuracy)
                .put(V_ACCURACY_KEY, "NONE")
                .put(FOREGROUND_KEY, foreground)
                .put(UPDATE_DISTANCE_KEY, updateDistance)
                .build();
    }

    @Override
    @Priority
    public int getPriority() {
        return LOW_PRIORITY;
    }

}
