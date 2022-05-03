/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.google.android.material.button.MaterialButton;
import com.urbanairship.android.layout.R;
import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.ButtonModel;
import com.urbanairship.android.layout.model.LabelButtonModel;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.util.UAStringUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.urbanairship.android.layout.util.ResourceUtils.dpToPx;

public class LabelButtonView extends MaterialButton implements BaseView<LabelButtonModel> {
    private LabelButtonModel model;

    public LabelButtonView(@NonNull Context context) {
        super(context, null, R.attr.borderlessButtonStyle);
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
        setAllCaps(false);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
    }

    @NonNull
    public static LabelButtonView create(@NonNull Context context, @NonNull LabelButtonModel model, @NonNull Environment environment) {
        LabelButtonView view = new LabelButtonView(context);
        view.setModel(model, environment);
        return view;
    }

    public void setModel(@NonNull LabelButtonModel model, @NonNull Environment environment) {
        this.model = model;

        setId(model.getViewId());
        configureButton();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        boolean autoHeight = MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY;
        boolean autoWidth = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY;
        if (autoHeight || autoWidth) {
            int twelveDp = (int) dpToPx(getContext(), 12);
            int horizontal = autoWidth ? twelveDp : 0;
            int vertical = autoHeight ? twelveDp : 0;
            setPadding(horizontal, vertical, horizontal, vertical);
        } else {
            setPadding(0, 0, 0, 0);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void configureButton() {
        LayoutUtils.applyButtonModel(this, model);
        model.setViewListener(modelListener);

        if (!UAStringUtil.isEmpty(model.getContentDescription())) {
            setContentDescription(model.getContentDescription());
        }

        setOnClickListener(v -> model.onClick());
        setMinHeight(0);
        setMinimumHeight(0);
        setInsetTop(0);
        setInsetBottom(0);
    }

    private final ButtonModel.Listener modelListener = new ButtonModel.Listener() {
        @Override
        public void setEnabled(boolean isEnabled) {
            LabelButtonView.this.setEnabled(isEnabled);
        }
    };
}
