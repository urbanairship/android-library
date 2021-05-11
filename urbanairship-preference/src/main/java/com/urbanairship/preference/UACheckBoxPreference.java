/* Copyright Airship and Contributors */

package com.urbanairship.preference;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;

import com.urbanairship.UAirship;
import com.urbanairship.app.ActivityListener;
import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.app.SimpleActivityListener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.CheckBoxPreference;

/**
 * Airship check box preference.
 */
public abstract class UACheckBoxPreference extends CheckBoxPreference {

    protected boolean isChecked = false;
    private static final long PREFERENCE_DELAY_MS = 1000;

    private ActivityListener listener;
    private Runnable applyAirshipPreferenceRunnable;
    private Handler handler;
    private boolean isInitialValueSet = false;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public UACheckBoxPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public UACheckBoxPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public UACheckBoxPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        listener = new SimpleActivityListener() {
            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                applyAirshipPreferenceRunnable.run();
            }
        };

        applyAirshipPreferenceRunnable = new Runnable() {
            @Override
            public void run() {
                handler.removeCallbacks(applyAirshipPreferenceRunnable);
                if (listener != null) {
                    GlobalActivityMonitor.shared(getContext().getApplicationContext()).removeActivityListener(listener);
                }

                onApplyAirshipPreference(UAirship.shared(), isChecked);
            }
        };

        handler = new Handler(Looper.getMainLooper());
        isChecked = getInitialAirshipValue(UAirship.shared());

        setDefaultValue(isChecked);
    }

    @Override
    protected boolean shouldPersist() {
        return false;
    }

    @Override
    public void setChecked(boolean value) {
        super.setChecked(value);
        isChecked = value;
        if (!isInitialValueSet) {
            isInitialValueSet = true;
            return;
        }

        if (listener != null) {
            GlobalActivityMonitor.shared(getContext()).addActivityListener(listener);
        }

        handler.removeCallbacks(applyAirshipPreferenceRunnable);
        handler.postDelayed(applyAirshipPreferenceRunnable, PREFERENCE_DELAY_MS);
    }

    /**
     * Gets the initial Airship value for the preference.
     *
     * @param airship The {@link UAirship} instance.
     * @return The initial value for the preference.
     */
    protected abstract boolean getInitialAirshipValue(@NonNull UAirship airship);

    /**
     * Called when the preference should be set on Airship.
     *
     * @param airship The {@link UAirship} instance.
     * @param enabled The value of the preference.
     */
    protected abstract void onApplyAirshipPreference(@NonNull UAirship airship, boolean enabled);

}
