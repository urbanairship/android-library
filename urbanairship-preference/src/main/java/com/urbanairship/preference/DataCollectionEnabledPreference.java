/* Copyright Airship and Contributors */

package com.urbanairship.preference;

import android.content.Context;
import android.util.AttributeSet;

import com.urbanairship.PrivacyManager;
import com.urbanairship.UAirship;
import com.urbanairship.modules.location.AirshipLocationClient;

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

    private final PrivacyManager.Listener privacyManagerListener = new PrivacyManager.Listener() {
        @Override
        public void onEnabledFeaturesChanged() {
            setEnabled(UAirship.shared().getPrivacyManager().isAnyFeatureEnabled());
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
        return airship.getPrivacyManager().isAnyFeatureEnabled();
    }

    @Override
    protected void onApplyAirshipPreference(@NonNull UAirship airship, boolean enabled) {
        if (enabled) {
            if (!airship.getPrivacyManager().isAnyFeatureEnabled()) {
                airship.getPrivacyManager().enable(PrivacyManager.FEATURE_ALL);
            }
        } else {
            airship.getPrivacyManager().disable(PrivacyManager.FEATURE_ALL);
        }
    }

}
