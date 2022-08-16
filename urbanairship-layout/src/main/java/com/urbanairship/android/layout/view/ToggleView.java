/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;

import com.urbanairship.android.layout.environment.ViewEnvironment;
import com.urbanairship.android.layout.model.ToggleModel;
import com.urbanairship.android.layout.widget.CheckableView;

import androidx.annotation.NonNull;

/**
 * Checkbox or Switch view for use within a {@code FormController} or {@code NpsController}.
 */
public class ToggleView extends CheckableView<ToggleModel> {

    public ToggleView(
        @NonNull Context context,
        @NonNull ToggleModel model,
        @NonNull ViewEnvironment viewEnvironment
    ) {
        super(context, model, viewEnvironment);
    }

    @Override
    protected void configure() {
        super.configure();

        getCheckableView().setOnCheckedChangeListener(checkedChangeListener);
    }
}
