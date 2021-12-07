/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;

import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.RadioInputModel;
import com.urbanairship.android.layout.widget.CheckableView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class RadioInputView extends CheckableView<RadioInputModel> {

    public RadioInputView(@NonNull Context context) {
        super(context);
    }

    public RadioInputView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public RadioInputView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @NonNull
    public static RadioInputView create(
        @NonNull Context context,
        @NonNull RadioInputModel model,
        @NonNull Environment environment
    ) {
        RadioInputView view = new RadioInputView(context);
        view.setModel(model, environment);
        return view;
    }

    @Override
    protected void configure() {
        super.configure();

        getModel().setListener(this::setCheckedInternal);
        getCheckableView().setOnCheckedChangeListener(checkedChangeListener);
    }
}
