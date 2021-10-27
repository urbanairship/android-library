/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;

import com.google.android.material.radiobutton.MaterialRadioButton;
import com.urbanairship.android.layout.model.RadioInputModel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class RadioInputView extends MaterialRadioButton implements BaseView<RadioInputModel> {
    private RadioInputModel model;

    public RadioInputView(@NonNull Context context) {
        super(context);
        init();
    }

    public RadioInputView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RadioInputView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setId(generateViewId());
    }

    @NonNull
    public static RadioInputView create(@NonNull Context context, @NonNull RadioInputModel model) {
        RadioInputView view = new RadioInputView(context);
        view.setModel(model);
        return view;
    }

    @Override
    public void setModel(@NonNull RadioInputModel model) {
        this.model = model;
        //LayoutUtils.applyLabelModel(this, model);
    }
}
