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

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;

import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionCompletionCallback;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ActionValue;

/**
 * CheckboxPreference to enable/disable location updates.
 */
public class LocationUpdatesEnabledPreference extends UACheckBoxPreference {

    private static final String CONTENT_DESCRIPTION = "LOCATION_UPDATES_ENABLED";

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public LocationUpdatesEnabledPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public LocationUpdatesEnabledPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LocationUpdatesEnabledPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setChecked(boolean value) {
        if (isChecked != value && value && shouldRequestPermissions()) {

            ActionRunRequest.createRequest(new Action() {
                @NonNull
                @Override
                public ActionResult perform(@NonNull ActionArguments arguments) {
                    int[] result = requestPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION);
                    for (int i = 0; i < result.length; i++) {
                        if (result[i] == PackageManager.PERMISSION_GRANTED) {
                            return ActionResult.newResult(ActionValue.wrap(true));
                        }
                    }

                    return ActionResult.newResult(ActionValue.wrap(false));
                }
            }).run(new ActionCompletionCallback() {
                @Override
                public void onFinish(@NonNull ActionArguments arguments, @NonNull ActionResult result) {
                    if (result.getValue().getBoolean(false)) {
                        setChecked(true);
                    }
                }
            });
        } else {
            super.setChecked(value);
        }
    }

    private boolean shouldRequestPermissions() {
        if (Build.VERSION.SDK_INT < 23) {
            return false;
        }

        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED &&
                ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED;
    }

    @Override
    protected boolean getInitialAirshipValue(UAirship airship) {
        return airship.getLocationManager().isLocationUpdatesEnabled();
    }

    @Override
    protected void onApplyAirshipPreference(UAirship airship, boolean enabled) {
        airship.getLocationManager().setLocationUpdatesEnabled(enabled);
    }

    @Override
    protected String getContentDescription() {
        return CONTENT_DESCRIPTION;
    }
}
