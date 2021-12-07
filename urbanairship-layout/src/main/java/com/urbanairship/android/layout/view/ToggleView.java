/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;

import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.ToggleModel;
import com.urbanairship.android.layout.widget.CheckableView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Checkbox or Switch view for use within a {@code FormController} or {@code NpsController}.
 */
public class ToggleView extends CheckableView<ToggleModel> {

    public ToggleView(@NonNull Context context) {
        super(context);
    }

    public ToggleView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ToggleView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @NonNull
    public static ToggleView create(
        @NonNull Context context,
        @NonNull ToggleModel model,
        @NonNull Environment environment
    ) {
        ToggleView view = new ToggleView(context);
        view.setModel(model, environment);
        return view;
    }

    @Override
    protected void configure() {
        super.configure();

        getCheckableView().setOnCheckedChangeListener(checkedChangeListener);
    }
}
