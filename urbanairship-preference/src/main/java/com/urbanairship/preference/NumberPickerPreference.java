package com.urbanairship.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;

import com.urbanairship.UAirship;

import java.util.concurrent.TimeUnit;

/**
 * Number Picker Preference to set IAA (In-App Automation) Display Interval
 */
public class NumberPickerPreference extends DialogPreference {

    private static final int dialogLayoutResId = R.layout.fragment_display_interval;
    private long value;

    public NumberPickerPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public NumberPickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public NumberPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NumberPickerPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        this.value = UAirship.shared().getInAppMessagingManager().getDisplayInterval();
    }

    public long getValue() {
        return value;
    }

    public void setValue(long v) {
        if(v != value) {
            value = v;
            UAirship.shared().getInAppMessagingManager().setDisplayInterval(v, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public int getDialogLayoutResource() {
        return dialogLayoutResId;
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
