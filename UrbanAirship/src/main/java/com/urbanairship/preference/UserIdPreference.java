package com.urbanairship.preference;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * The Rich Push User preference.
 */
public class UserIdPreference extends Preference implements UAPreference {

    public UserIdPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        view.setContentDescription(getPreferenceType().toString());
        return view;
    }

    @Override
    public PreferenceType getPreferenceType() {
        return PreferenceType.USER_ID;
    }

    @Override
    public void setValue(Object value) {
        setSummary((String) value);
    }

}
