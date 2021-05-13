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
 * CheckboxPreference to enable/disable analytic events.
 */
public class AnalyticsEnablePreference extends UACheckBoxPreference {

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public AnalyticsEnablePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public AnalyticsEnablePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AnalyticsEnablePreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    private final PrivacyManager.Listener privacyManagerListener = new PrivacyManager.Listener() {
        @Override
        public void onEnabledFeaturesChanged() {
            setEnabled(UAirship.shared().getPrivacyManager().isEnabled(PrivacyManager.FEATURE_ANALYTICS));
        }
    };

    @Override
    public void onAttached() {
        super.onAttached();
        UAirship.shared().getPrivacyManager().addListener(privacyManagerListener);
    }

    @Override
    public void onDetached() {
        super.onDetached();
        UAirship.shared().getPrivacyManager().removeListener(privacyManagerListener);
    }

    @Override
    protected boolean getInitialAirshipValue(@NonNull UAirship airship) {
        return airship.getPrivacyManager().isEnabled(PrivacyManager.FEATURE_ANALYTICS);
    }

    @Override
    protected void onApplyAirshipPreference(@NonNull UAirship airship, boolean enabled) {
        if (enabled) {
            airship.getPrivacyManager().enable(PrivacyManager.FEATURE_ANALYTICS);
        } else {
            airship.getPrivacyManager().disable(PrivacyManager.FEATURE_ANALYTICS);
        }
    }

}
