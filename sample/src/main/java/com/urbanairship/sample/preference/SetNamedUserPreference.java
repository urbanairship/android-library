/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.sample.preference;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.urbanairship.UAirship;
import com.urbanairship.util.UAStringUtil;

/**
 * DialogPreference to set the named user.
 *
 */
public class SetNamedUserPreference extends DialogPreference {

    private EditText editTextView;
    private String currentNamedUser;

    public SetNamedUserPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        currentNamedUser = UAirship.shared().getNamedUser().getId();
    }

    @Override
    protected View onCreateDialogView() {
        editTextView = new EditText(getContext());
        editTextView.setText(currentNamedUser);

        return editTextView;
    }

    @Override
    public View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        view.setContentDescription("SET_NAMED_USER");
        return view;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            String namedUser = editTextView.getText().toString();
            if (callChangeListener(namedUser)) {
                setNamedUser(namedUser);
                notifyChanged();
            }
        }
    }

    private void setNamedUser(String namedUser) {
        currentNamedUser = UAStringUtil.isEmpty(namedUser) ? null : namedUser;
        UAirship.shared().getNamedUser().setId(currentNamedUser);
    }

    @Override
    public String getSummary() {
        return currentNamedUser;
    }

    @Override
    protected boolean shouldPersist() {
        return false;
    }
}
