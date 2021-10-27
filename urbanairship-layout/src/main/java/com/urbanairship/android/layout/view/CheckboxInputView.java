/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.urbanairship.android.layout.model.CheckboxInputModel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CheckboxInputView extends MaterialCheckBox implements BaseView<CheckboxInputModel> {
    private CheckboxInputModel model;

    public CheckboxInputView(@NonNull Context context) {
        super(context);
        init();
    }

    public CheckboxInputView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CheckboxInputView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setId(generateViewId());
    }

    @NonNull
    public static CheckboxInputView create(@NonNull Context context, @NonNull CheckboxInputModel model) {
        CheckboxInputView view = new CheckboxInputView(context);
        view.setModel(model);
        return view;
    }

    @Override
    public void setModel(@NonNull CheckboxInputModel model) {
        this.model = model;
        //LayoutUtils.applyLabelModel(this, model);
    }
}
