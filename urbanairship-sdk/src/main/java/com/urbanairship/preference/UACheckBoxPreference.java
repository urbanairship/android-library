/* Copyright 2016 Urban Airship and Contributors */

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
public abstract class UACheckBoxPreference extends CheckBoxPreference {
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
}
