/* Copyright Airship and Contributors */

package com.urbanairship.preference;

import android.content.Context;
import android.util.AttributeSet;

import com.urbanairship.UAirship;

import androidx.annotation.NonNull;

public class OptInPreference extends UACheckBoxPreference {

    public OptInPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public OptInPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public OptInPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected boolean getInitialAirshipValue(@NonNull UAirship airship) {
        return UAirship.shared().isDataOptIn();
    }

    @Override
    protected void onApplyAirshipPreference(@NonNull UAirship airship, boolean enabled) {
        UAirship.shared().setDataOptIn(enabled);
    }

}
