/* Copyright Airship and Contributors */

package com.urbanairship.preference;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;

import com.urbanairship.PrivacyManager;
import com.urbanairship.UAirship;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * CheckboxPreference to enable/disable push notifications.
 */
public class PushEnablePreference extends UACheckBoxPreference {

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public PushEnablePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public PushEnablePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public PushEnablePreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected boolean getInitialAirshipValue(@NonNull UAirship airship) {
        return airship.getPushManager().getUserNotificationsEnabled() && airship.getPrivacyManager().isEnabled(PrivacyManager.FEATURE_PUSH);
    }

    @Override
    protected void onApplyAirshipPreference(@NonNull UAirship airship, boolean enabled) {
        airship.getPushManager().setUserNotificationsEnabled(enabled);
    }

}
