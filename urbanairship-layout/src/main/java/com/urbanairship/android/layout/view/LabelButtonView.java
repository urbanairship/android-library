/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;

import com.google.android.material.button.MaterialButton;
import com.urbanairship.android.layout.R;
import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.ButtonModel;
import com.urbanairship.android.layout.model.LabelButtonModel;
import com.urbanairship.android.layout.util.LayoutUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LabelButtonView extends MaterialButton implements BaseView<LabelButtonModel> {
    private LabelButtonModel model;

    public LabelButtonView(@NonNull Context context) {
        super(context, null, R.attr.materialButtonStyle);
        init();
    }

    public LabelButtonView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LabelButtonView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setId(generateViewId());
        setAllCaps(false);
    }

    @NonNull
    public static LabelButtonView create(@NonNull Context context, @NonNull LabelButtonModel model, @NonNull Environment environment) {
        LabelButtonView view = new LabelButtonView(context);
        view.setModel(model, environment);
        return view;
    }

    public void setModel(@NonNull LabelButtonModel model, @NonNull Environment environment) {
        this.model = model;
        configureButton();
    }

    private void configureButton() {
        LayoutUtils.applyButtonModel(this, model);
        model.setViewListener(modelListener);

        setOnClickListener(v -> model.onClick());
    }

    private final ButtonModel.Listener modelListener = new ButtonModel.Listener() {
        @Override
        public void setEnabled(boolean isEnabled) {
            LabelButtonView.this.setEnabled(isEnabled);
        }
    };
}
