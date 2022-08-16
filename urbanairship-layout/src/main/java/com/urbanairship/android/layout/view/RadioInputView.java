/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;

import com.urbanairship.android.layout.environment.ViewEnvironment;
import com.urbanairship.android.layout.model.RadioInputModel;
import com.urbanairship.android.layout.widget.CheckableView;

import androidx.annotation.NonNull;

public class RadioInputView extends CheckableView<RadioInputModel> {

    public RadioInputView(
        @NonNull Context context,
        @NonNull RadioInputModel model,
        @NonNull ViewEnvironment viewEnvironment
    ) {
        super(context, model, viewEnvironment);
    }

    @Override
    protected void configure() {
        super.configure();

        getModel().setListener(this::setCheckedInternal);
        getCheckableView().setOnCheckedChangeListener(checkedChangeListener);
    }
}
