/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.text.TextUtils;

import com.google.android.material.button.MaterialButton;
import com.urbanairship.android.layout.R;
import com.urbanairship.android.layout.environment.ViewEnvironment;
import com.urbanairship.android.layout.model.ButtonModel;
import com.urbanairship.android.layout.model.LabelButtonModel;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.util.UAStringUtil;

import androidx.annotation.NonNull;

import static com.urbanairship.android.layout.util.ResourceUtils.dpToPx;

public class LabelButtonView extends MaterialButton implements BaseView {
    private final LabelButtonModel model;

    public LabelButtonView(
        @NonNull Context context,
        @NonNull LabelButtonModel model,
        @NonNull ViewEnvironment viewEnvironment
    ) {
        super(context, null, R.attr.borderlessButtonStyle);
        this.model = model;

        setId(model.getViewId());

        configure();
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

    private void configure() {
        setAllCaps(false);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);

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
