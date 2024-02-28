/* Copyright Airship and Contributors */

package com.urbanairship.sample.preference;

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
        if (UAStringUtil.isEmpty(text)) {
            UAirship.shared().getContact().reset();
        } else {
            UAirship.shared().getContact().identify(text);
        }

        notifyChanged();
    }

    @Override
    public String getText() {
        return UAirship.shared().getContact().getNamedUserId();
    }

    @Override
    public String getSummary() {
        return UAirship.shared().getContact().getNamedUserId();
    }

    @Override
    protected boolean shouldPersist() {
        return false;
    }

}
