/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.preference;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.preference.DialogPreference;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TimePicker;

import com.urbanairship.UAirship;

import java.util.Calendar;

/**
 * Abstract DialogPreference that allows setting quiet time.
 */
public abstract class QuietTimePickerPreference extends DialogPreference {
    private TimePicker timePicker = null;
    private long currentTime = -1;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public QuietTimePickerPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        currentTime = getInitialAirshipValue(UAirship.shared());
    }

    public QuietTimePickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        currentTime = getInitialAirshipValue(UAirship.shared());
    }

    public QuietTimePickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        currentTime = getInitialAirshipValue(UAirship.shared());
    }

    @Override
    public View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        view.setContentDescription(getContentDescription());
        return view;
    }

    @Override
    protected View onCreateDialogView() {
        timePicker = new TimePicker(getContext());

        timePicker.setIs24HourView(DateFormat.is24HourFormat(getContext()));

        Calendar calendar = getCalendar();
        timePicker.setCurrentHour(calendar.get(Calendar.HOUR_OF_DAY));
        timePicker.setCurrentMinute(calendar.get(Calendar.MINUTE));

        return timePicker;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, timePicker.getCurrentHour());
            calendar.set(Calendar.MINUTE, timePicker.getCurrentMinute());

            long time = calendar.getTimeInMillis();
            if (callChangeListener(time)) {
                currentTime = time;
                onApplyAirshipPreference(UAirship.shared(), currentTime);
                notifyChanged();
            }
        }
    }

    @Override
    public String getSummary() {
        return DateFormat.getTimeFormat(getContext()).format(getCalendar().getTime());
    }

    /**
     * Helper to create a new calendar with the current time of the preference
     *
     * @return Calendar of the current time of the preference
     */
    private Calendar getCalendar() {
        Calendar calendar = Calendar.getInstance();

        if (currentTime != -1) {
            calendar.setTimeInMillis(currentTime);
        }

        return calendar;
    }

    @Override
    protected boolean shouldPersist() {
        return false;
    }

    /**
     * Gets the initial Urban Airship value for the preference.
     *
     * @param airship The {@link UAirship} instance.
     * @return The initial value for the preference.
     */
    protected abstract long getInitialAirshipValue(UAirship airship);

    /**
     * Called when the preference should be set on Urban Airship.
     * @param airship The {@link UAirship} instance.
     * @param time The value of the preference.
     */
    protected abstract void onApplyAirshipPreference(UAirship airship, long time);

    /**
     * Called to get the content description of the preference's view.
     * @return The content description.
     */
    protected abstract String getContentDescription();
}
