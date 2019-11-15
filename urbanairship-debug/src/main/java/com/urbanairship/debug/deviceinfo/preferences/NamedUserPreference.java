/* Copyright Airship and Contributors */

package com.urbanairship.debug.deviceinfo.preferences;

import android.content.Context;
import android.util.AttributeSet;

import com.urbanairship.UAirship;
import com.urbanairship.util.UAStringUtil;

import androidx.preference.EditTextPreference;

/**
 * DialogPreference to set the named user.
 */
public class NamedUserPreference extends EditTextPreference {

    public NamedUserPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setText(String text) {
        String namedUser = UAStringUtil.isEmpty(text) ? null : text;
        UAirship.shared().getNamedUser().setId(namedUser);
        notifyChanged();
    }

    @Override
    public String getText() {
        return UAirship.shared().getNamedUser().getId();
    }

    @Override
    public String getSummary() {
        return UAirship.shared().getNamedUser().getId();
    }

    @Override
    protected boolean shouldPersist() {
        return false;
    }
}
