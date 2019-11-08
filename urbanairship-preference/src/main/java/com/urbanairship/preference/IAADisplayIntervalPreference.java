package com.urbanairship.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.urbanairship.UAirship;

import java.util.concurrent.TimeUnit;

/**
 * Number Picker Preference to set IAA (In-App Automation) Display Interval
 */
public class IAADisplayIntervalPreference extends UANumberPickerPreference {

    private static final int DIALOG_LAYOUT_RES_ID = R.layout.fragment_display_interval;

    public IAADisplayIntervalPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public IAADisplayIntervalPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public IAADisplayIntervalPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public IAADisplayIntervalPreference(Context context) {
        super(context);
    }

    @Override
    public void setValue(long v) {
        if(v != value) {
            value = v;
            UAirship.shared().getInAppMessagingManager().setDisplayInterval(v, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    protected long getInitialValue() {
        return UAirship.shared().getInAppMessagingManager().getDisplayInterval();
    }

    @Override
    public int getDialogLayoutResource() {
        return DIALOG_LAYOUT_RES_ID;
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        value = UAirship.shared().getInAppMessagingManager().getDisplayInterval();
        return value;
    }

    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        long initialValue = UAirship.shared().getInAppMessagingManager().getDisplayInterval();
        setValue(initialValue);
    }
}
