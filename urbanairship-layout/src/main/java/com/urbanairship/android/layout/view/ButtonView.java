/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;

import com.google.android.material.button.MaterialButton;
import com.urbanairship.android.layout.R;
import com.urbanairship.android.layout.model.ButtonModel;
import com.urbanairship.android.layout.util.LayoutUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ButtonView extends MaterialButton implements BaseView<ButtonModel> {
    private ButtonModel model;

    public ButtonView(@NonNull Context context) {
        super(context, null, R.attr.materialButtonStyle);
        init();
    }

    public ButtonView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ButtonView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setId(generateViewId());
    }

    @NonNull
    public static ButtonView create(@NonNull Context context, @NonNull ButtonModel model) {
        ButtonView view = new ButtonView(context);
        view.setModel(model);
        return view;
    }

    public void setModel(@NonNull ButtonModel model) {
        this.model = model;
        configureButton();
    }

    public void configureButton() {
        LayoutUtils.applyButtonModel(this, model);

        setOnClickListener(v -> model.onClick());
    }
}
