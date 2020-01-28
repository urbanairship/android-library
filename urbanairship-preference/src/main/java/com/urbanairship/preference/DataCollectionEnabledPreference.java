/* Copyright Airship and Contributors */

package com.urbanairship.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;

import com.urbanairship.UAirship;

/**
 * Preference that can be used to embed data collection preference in a settings screen.
 */
public class DataCollectionEnabledPreference extends UACheckBoxPreference {

    public DataCollectionEnabledPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public DataCollectionEnabledPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public DataCollectionEnabledPreference(Context context, AttributeSet attrs) {
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
