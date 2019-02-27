/* Copyright Urban Airship and Contributors */

package com.urbanairship.sample.preference;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;

import com.urbanairship.UAirship;
import com.urbanairship.aaid.AdvertisingIdTracker;
import com.urbanairship.preference.UACheckBoxPreference;

public class EnableAdvertisingIdPreference extends UACheckBoxPreference {

    public EnableAdvertisingIdPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public EnableAdvertisingIdPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public EnableAdvertisingIdPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected boolean getInitialAirshipValue(@NonNull UAirship airship) {
        return AdvertisingIdTracker.shared().isEnabled();
    }

    @Override
    protected void onApplyAirshipPreference(@NonNull UAirship airship, boolean enabled) {
        AdvertisingIdTracker.shared().setEnabled(enabled);
    }
}
