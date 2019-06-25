/* Copyright Airship and Contributors */

package com.urbanairship.preference;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.AttributeSet;

import com.urbanairship.UAirship;

/**
 * CheckboxPreference to allow/disallow background location updates.
 */
public class LocationBackgroundUpdatesAllowedPreference extends UACheckBoxPreference {

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public LocationBackgroundUpdatesAllowedPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public LocationBackgroundUpdatesAllowedPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LocationBackgroundUpdatesAllowedPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected boolean getInitialAirshipValue(@NonNull UAirship airship) {
        return airship.getLocationManager().isBackgroundLocationAllowed();
    }

    @Override
    protected void onApplyAirshipPreference(@NonNull UAirship airship, boolean enabled) {
        airship.getLocationManager().setBackgroundLocationAllowed(enabled);
    }

}
