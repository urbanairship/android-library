/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.preference;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;

import com.urbanairship.UAirship;

import java.util.Date;

/**
 * DialogPreference to set the quiet time start.
 */
public class QuietTimeStartPreference extends QuietTimePickerPreference {

    private static final String CONTENT_DESCRIPTION = "QUIET_TIME_START";

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public QuietTimeStartPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public QuietTimeStartPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public QuietTimeStartPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected String getContentDescription() {
        return CONTENT_DESCRIPTION;
    }

    @Override
    protected long getInitialAirshipValue(UAirship airship) {
        Date[] quietTimes = airship.getPushManager().getQuietTimeInterval();
        return quietTimes != null ? quietTimes[0].getTime() : -1;
    }

    @Override
    protected void onApplyAirshipPreference(UAirship airship, long time) {
        Date[] quietTimes = airship.getPushManager().getQuietTimeInterval();
        Date end = quietTimes != null ? quietTimes[1] : new Date();
        airship.getPushManager().setQuietTimeInterval(new Date(time), end);
    }
}
