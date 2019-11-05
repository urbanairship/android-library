package com.urbanairship.preference;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.NumberPicker;
import android.widget.Spinner;

import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragmentCompat;

/**
 * PreferenceDialogFragment to set the IAA (In-App Automation) Display Interval
 */
public class NumberPickerPreferenceDialogFragment extends PreferenceDialogFragmentCompat {

    private static final int DEFAULT_MAX_VALUE = 200;
    private static final int MAX_SECONDS_AND_DAYS_VALUE = 200;
    private static final int MAX_MINUTES_AND_HOURS_VALUE = 100;
    private static final int MIN_VALUE = 0;

    private NumberPicker numberPicker;
    private Spinner timeUnitSpinner;

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        numberPicker = view.findViewById(R.id.display_interval);
        timeUnitSpinner = view.findViewById(R.id.time_unit);

        timeUnitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                    case 3:
                        numberPicker.setMaxValue(MAX_SECONDS_AND_DAYS_VALUE);
                        break;
                    case 1:
                    case 2:
                        numberPicker.setMaxValue(MAX_MINUTES_AND_HOURS_VALUE);
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        DialogPreference preference = getPreference();

        init(((NumberPickerPreference)preference).getValue());
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
    public static NumberPickerPreferenceDialogFragment newInstance(String key) {
        final NumberPickerPreferenceDialogFragment dialogFragment =  new NumberPickerPreferenceDialogFragment();
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
        numberPicker.setMaxValue(DEFAULT_MAX_VALUE);

        long lastDisplayIntervalInSecond = lastDisplayInterval / 1000L;

        if (lastDisplayIntervalInSecond <= MAX_SECONDS_AND_DAYS_VALUE) {
            timeUnitSpinner.setSelection(0);
            numberPicker.setValue((int)lastDisplayIntervalInSecond);
        } else if (lastDisplayIntervalInSecond / 60L <= MAX_MINUTES_AND_HOURS_VALUE) {
            timeUnitSpinner.setSelection(1);
            numberPicker.setValue((int)(lastDisplayIntervalInSecond / 60L));
        } else if (lastDisplayIntervalInSecond / 60 / 60L <= MAX_MINUTES_AND_HOURS_VALUE) {
            timeUnitSpinner.setSelection(2);
            numberPicker.setValue((int)(lastDisplayIntervalInSecond / 60 / 60L));
        } else {
            timeUnitSpinner.setSelection(3);
            numberPicker.setValue((int)(lastDisplayIntervalInSecond / 60 / 60 / 24L));
        }
    }

    /**
     * Save the display interval in milliseconds
     */
    private void saveDisplayInterval() {
        NumberPickerPreference preference = (NumberPickerPreference)getPreference();

        switch (timeUnitSpinner.getSelectedItemPosition()) {
            case 0:
                preference.setValue(numberPicker.getValue() * 1000L);
                break;
            case 1:
                preference.setValue(numberPicker.getValue() * 60 * 1000L);
                break;
            case 2:
                preference.setValue(numberPicker.getValue() * 60 * 60 * 1000L);
                break;
            case 3:
                preference.setValue(numberPicker.getValue() * 24 * 60 * 60 * 1000L);
                break;
            default:
                preference.setValue(numberPicker.getValue());
                break;
        }
    }

}
