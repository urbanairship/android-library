/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.preference;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;

import com.urbanairship.UAirship;

/**
 * CheckboxPreference to enable/disable push notification vibration.
 */
public class VibrateEnablePreference extends UACheckBoxPreference {

    private static final String CONTENT_DESCRIPTION = "VIBRATE_ENABLED";

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public VibrateEnablePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public VibrateEnablePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public VibrateEnablePreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected boolean getInitialAirshipValue(@NonNull UAirship airship) {
        return airship.getPushManager().isVibrateEnabled();
    }

    @Override
    protected void onApplyAirshipPreference(@NonNull UAirship airship, boolean enabled) {
        airship.getPushManager().setVibrateEnabled(enabled);
    }

    @NonNull
    @Override
    protected String getContentDescription() {
        return CONTENT_DESCRIPTION;
    }
}
