/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.


Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

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
 * DialogPreference to set the alias
 *
 */
public class SetAliasPreference extends DialogPreference {

    private EditText editTextView;
    private String currentAlias;

    public SetAliasPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        currentAlias = UAirship.shared().getPushManager().getAlias();
    }

    @Override
    protected View onCreateDialogView() {
        editTextView = new EditText(getContext());
        editTextView.setText(currentAlias);

        return editTextView;
    }

    @Override
    public View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        view.setContentDescription("SET_ALIAS");
        return view;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            String alias = editTextView.getText().toString();
            if (callChangeListener(alias)) {
                setAlias(alias);
                notifyChanged();
            }
        }
    }

    private void setAlias(String alias) {
        currentAlias = UAStringUtil.isEmpty(alias) ? null : alias;
        UAirship.shared().getPushManager().setAlias(currentAlias);
    }

    @Override
    public String getSummary() {
        return currentAlias;
    }

    @Override
    protected boolean shouldPersist() {
        return false;
    }
}
