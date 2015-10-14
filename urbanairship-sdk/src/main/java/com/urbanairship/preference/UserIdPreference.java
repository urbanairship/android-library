package com.urbanairship.preference;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.urbanairship.UAirship;

/**
 * The Rich Push User preference.
 */
@SuppressWarnings("deprecation") // For UAPreference
public class UserIdPreference extends Preference implements UAPreference {

    private static final String CONTENT_DESCRIPTION = "USER_ID";

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public UserIdPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public UserIdPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public UserIdPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        view.setContentDescription(CONTENT_DESCRIPTION);
        setSummary(UAirship.shared().getRichPushManager().getRichPushUser().getId());
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
