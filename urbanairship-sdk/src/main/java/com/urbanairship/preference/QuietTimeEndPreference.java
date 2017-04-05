/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.preference;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;

import com.urbanairship.UAirship;

import java.util.Date;

/**
 * DialogPreference to set the quiet time end.
 */
public class QuietTimeEndPreference extends QuietTimePickerPreference {

    private static final String CONTENT_DESCRIPTION = "QUIET_TIME_END";

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public QuietTimeEndPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public QuietTimeEndPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public QuietTimeEndPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected String getContentDescription() {
        return CONTENT_DESCRIPTION;
    }

    @Override
    protected long getInitialAirshipValue(UAirship airship) {
        Date[] quietTimes = airship.getPushManager().getQuietTimeInterval();
        return quietTimes != null ? quietTimes[1].getTime() : -1;
    }

    @Override
    protected void onApplyAirshipPreference(UAirship airship, long time) {
        Date[] quietTimes = airship.getPushManager().getQuietTimeInterval();
        Date start = quietTimes != null ? quietTimes[0] : new Date();
        airship.getPushManager().setQuietTimeInterval(start, new Date(time));
    }
}
