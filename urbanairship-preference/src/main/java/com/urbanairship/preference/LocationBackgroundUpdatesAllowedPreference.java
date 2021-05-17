/* Copyright Airship and Contributors */

package com.urbanairship.preference;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;

import com.urbanairship.PrivacyManager;
import com.urbanairship.UAirship;
import com.urbanairship.modules.location.AirshipLocationClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
        if (airship.getLocationClient() != null) {
            return airship.getLocationClient().isBackgroundLocationAllowed();
        } else {
            return false;
        }
    }

    @Override
    protected void onApplyAirshipPreference(@NonNull UAirship airship, boolean enabled) {
        if (airship.getLocationClient() != null) {
            airship.getLocationClient().setBackgroundLocationAllowed(enabled);
        }

    }

    @Override
    public boolean isEnabled() {
        return UAirship.shared().getLocationClient() != null && super.isEnabled();
    }

}
