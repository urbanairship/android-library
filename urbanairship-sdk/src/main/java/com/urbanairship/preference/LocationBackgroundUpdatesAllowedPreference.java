/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.preference;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;

import com.urbanairship.UAirship;

/**
 * CheckboxPreference to allow/disallow background location updates.
 */
public class LocationBackgroundUpdatesAllowedPreference extends UACheckBoxPreference {

    private static final String CONTENT_DESCRIPTION = "LOCATION_BACKGROUND_UPDATES_ALLOWED";

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public LocationBackgroundUpdatesAllowedPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public LocationBackgroundUpdatesAllowedPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LocationBackgroundUpdatesAllowedPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected boolean getInitialAirshipValue(UAirship airship) {
        return airship.getLocationManager().isBackgroundLocationAllowed();
    }

    @Override
    protected void onApplyAirshipPreference(UAirship airship, boolean enabled) {
        airship.getLocationManager().setBackgroundLocationAllowed(enabled);
    }

    @Override
    protected String getContentDescription() {
        return CONTENT_DESCRIPTION;
    }
}
