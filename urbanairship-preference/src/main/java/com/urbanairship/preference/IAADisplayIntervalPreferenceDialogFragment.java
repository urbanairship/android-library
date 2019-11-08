package com.urbanairship.preference;

import android.os.Bundle;
import android.view.View;
import android.widget.NumberPicker;

import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragmentCompat;

/**
 * PreferenceDialogFragment to set the IAA (In-App Automation) Display Interval
 */
public class IAADisplayIntervalPreferenceDialogFragment extends PreferenceDialogFragmentCompat {

    private static final int MAX_VALUE = 120;
    private static final int MIN_VALUE = 0;

    private NumberPicker numberPicker;

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        numberPicker = view.findViewById(R.id.display_interval);

        DialogPreference preference = getPreference();
        init(((IAADisplayIntervalPreference)preference).getValue());
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if(positiveResult) {
            numberPicker.clearFocus();
            saveDisplayInterval();
        }
    }

    /**
     * Create a new Instance of NumberPickerPreferenceDialogFragment
     * @param key the preference key
     * @return a new Instance of NumberPickerPreferenceDialogFragment
     */
    public static IAADisplayIntervalPreferenceDialogFragment newInstance(String key) {
        final IAADisplayIntervalPreferenceDialogFragment dialogFragment =  new IAADisplayIntervalPreferenceDialogFragment();
        Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        dialogFragment.setArguments(b);
        return dialogFragment;
    }

    /**
     * Initialize the Number Picker and the Spinner based on the last display interval value
     * @param lastDisplayInterval the last display interval value
     */
    private void init(long lastDisplayInterval) {
        numberPicker.setMinValue(MIN_VALUE);
        numberPicker.setMaxValue(MAX_VALUE);

        long lastDisplayIntervalInSecond = lastDisplayInterval / 1000L;
        numberPicker.setValue((int)lastDisplayIntervalInSecond);
    }

    /**
     * Save the display interval in milliseconds
     */
    private void saveDisplayInterval() {
        IAADisplayIntervalPreference preference = (IAADisplayIntervalPreference)getPreference();
        preference.setValue(numberPicker.getValue() * 1000L);
    }

}
