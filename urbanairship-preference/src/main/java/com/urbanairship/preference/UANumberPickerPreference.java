package com.urbanairship.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.DialogPreference;

/**
 * Airship NumberPicker preference.
 */
public abstract class UANumberPickerPreference extends DialogPreference {

    protected long value;

    public UANumberPickerPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public UANumberPickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public UANumberPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public UANumberPickerPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        this.value = getInitialValue();
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
    }

    /**
     * Gets the initial Airship value for the preference
     * @return the initial value for the preference
     */
    protected abstract long getInitialValue();

}
