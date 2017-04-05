/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.preference;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;

import com.urbanairship.UAirship;


/**
 * CheckboxPreference to enable/disable push notifications.
 */
public class PushEnablePreference extends UACheckBoxPreference  {

    private static final String CONTENT_DESCRIPTION = "USER_NOTIFICATIONS_ENABLED";

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public PushEnablePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public PushEnablePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public PushEnablePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected boolean getInitialAirshipValue(UAirship airship) {
        return airship.getPushManager().getUserNotificationsEnabled();
    }

    @Override
    protected void onApplyAirshipPreference(UAirship airship, boolean enabled) {
        airship.getPushManager().setUserNotificationsEnabled(enabled);
    }

    @Override
    protected String getContentDescription() {
        return CONTENT_DESCRIPTION;
    }
}
