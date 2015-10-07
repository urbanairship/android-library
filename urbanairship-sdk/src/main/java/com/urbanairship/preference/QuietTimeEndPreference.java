/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

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
