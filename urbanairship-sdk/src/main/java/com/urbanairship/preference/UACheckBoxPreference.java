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
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.urbanairship.LifeCycleCallbacks;
import com.urbanairship.UAirship;

/**
 * Urban Airship check box preference.
 */
@SuppressWarnings("deprecation") // For UAPreference
public abstract class UACheckBoxPreference extends CheckBoxPreference implements UAPreference {
    protected boolean isChecked = false;
    private static final long PREFERENCE_DELAY_MS = 1000;

    private LifeCycleCallbacks lifeCycleCallbacks;
    private Runnable applyAirshipPreferenceRunnable;
    private Handler handler;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public UACheckBoxPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public UACheckBoxPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public UACheckBoxPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        if (Build.VERSION.SDK_INT >= 14 && getContext().getApplicationContext() instanceof Application) {
            Application application = (Application) getContext().getApplicationContext();
            lifeCycleCallbacks = new LifeCycleCallbacks(application) {
                @Override
                public void onActivityStopped(Activity activity) {
                    applyAirshipPreferenceRunnable.run();
                }
            };
        }

        applyAirshipPreferenceRunnable = new Runnable() {
            @Override
            public void run() {
                handler.removeCallbacks(applyAirshipPreferenceRunnable);
                if (lifeCycleCallbacks != null) {
                    lifeCycleCallbacks.unregister();
                }

                onApplyAirshipPreference(UAirship.shared(), isChecked);
            }
        };

        handler = new Handler(Looper.getMainLooper());
        isChecked = getInitialAirshipValue(UAirship.shared());

        setDefaultValue(isChecked);
    }

    @Override
    public View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        view.setContentDescription(getClass().getSimpleName());
        return view;
    }

    @Override
    protected boolean shouldPersist() {
        return false;
    }

    @Override
    public void setChecked(boolean value) {
        super.setChecked(value);
        isChecked = value;

        if (lifeCycleCallbacks != null) {
            lifeCycleCallbacks.register();
        }

        handler.removeCallbacks(applyAirshipPreferenceRunnable);
        handler.postDelayed(applyAirshipPreferenceRunnable, PREFERENCE_DELAY_MS);
    }

    /**
     * Gets the initial Urban Airship value for the preference.
     *
     * @param airship The {@link UAirship} instance.
     * @return The initial value for the preference.
     */
    protected abstract boolean getInitialAirshipValue(UAirship airship);

    /**
     * Called when the preference should be set on Urban Airship.
     * @param airship The {@link UAirship} instance.
     * @param enabled The value of the preference.
     */
    protected abstract void onApplyAirshipPreference(UAirship airship, boolean enabled);

    /**
     * Called to get the content description of the preference's view.
     * @return The content description.
     */
    protected abstract String getContentDescription();

    @Override
    public PreferenceType getPreferenceType() {
        // Should no longer be used, so doing the quick workaround. Remove this in 7.0.0.
        return PreferenceType.valueOf(getContentDescription());
    }

    @Override
    public void setValue(Object value) {
        setChecked((Boolean) value);
    }
}