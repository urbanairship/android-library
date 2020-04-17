/* Copyright Airship and Contributors */

package com.urbanairship.preference;

import android.content.Context;
import android.util.AttributeSet;

import com.urbanairship.UAirship;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Preference that can be used to embed a data collection preference in a settings screen.
 */
public class DataCollectionEnabledPreference extends UACheckBoxPreference {

    public DataCollectionEnabledPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public DataCollectionEnabledPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public DataCollectionEnabledPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected boolean getInitialAirshipValue(@NonNull UAirship airship) {
        return UAirship.shared().isDataCollectionEnabled();
    }

    @Override
    protected void onApplyAirshipPreference(@NonNull UAirship airship, boolean enabled) {
        UAirship.shared().setDataCollectionEnabled(enabled);
    }

}
