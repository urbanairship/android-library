package com.urbanairship.preference;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.preference.Preference;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.urbanairship.UAirship;

/**
 * The Rich Push User preference.
 */
public class UserIdPreference extends Preference {

    private static final String CONTENT_DESCRIPTION = "USER_ID";

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public UserIdPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public UserIdPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public UserIdPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull ViewGroup parent) {
        View view = super.onCreateView(parent);
        view.setContentDescription(CONTENT_DESCRIPTION);
        setSummary(UAirship.shared().getInbox().getUser().getId());
        return view;
    }
}
