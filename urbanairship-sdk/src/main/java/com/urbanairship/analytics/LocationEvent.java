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

package com.urbanairship.analytics;

import android.location.Location;
import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.util.UAStringUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * This class captures all the necessary information for Urban Airship
 * {@link com.urbanairship.analytics.Analytics}.
 */
public class LocationEvent extends Event {

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

    /**
     * An enum representing the location update type.
     */
    public enum UpdateType {
        CONTINUOUS, SINGLE
    }

    private final String provider;
    private final String latitude;
    private final String longitude;
    private final String accuracy;
    private final String requestedAccuracy;
    private final String updateDistance;
    private final String foreground;
    private final UpdateType updateType;

    /**
     * Constructor for LocationEvent.
     *
     * @param location An instance of Location.
     * @param type The location's UpdateType.
     * @param userRequestedAccuracy The associated user-requested location accuracy.
     * @param updateDist The associated update distance.
     * @param isForeground If the location was recorded when the app was foregrounded or not.
     */
    public LocationEvent(@NonNull Location location, UpdateType type, int userRequestedAccuracy, int updateDist, boolean isForeground) {
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
    protected final JSONObject getEventData() {

        JSONObject data = new JSONObject();

        try {
            data.put(LATITUDE_KEY, latitude);
            data.put(LONGITUDE_KEY, longitude);
            data.put(REQUESTED_ACCURACY_KEY, requestedAccuracy);
            data.put(UPDATE_TYPE_KEY, updateType.toString());
            data.put(PROVIDER_KEY, provider);
            data.put(H_ACCURACY_KEY, accuracy);
            data.put(V_ACCURACY_KEY, "NONE");
            data.put(FOREGROUND_KEY, foreground);
            data.put(UPDATE_DISTANCE_KEY, updateDistance);
        } catch (JSONException e) {
            Logger.error("LocationEvent - Error constructing JSON data.", e);
        }

        return data;
    }

}
