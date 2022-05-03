/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;

import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.LabelModel;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.util.UAStringUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

public class LabelView extends AppCompatTextView implements BaseView<LabelModel> {
    public LabelView(@NonNull Context context) {
        super(context);
        init();
    }

    public LabelView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LabelView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
    }

    @NonNull
    public static LabelView create(@NonNull Context context, @NonNull LabelModel model, @NonNull Environment environment) {
        LabelView view = new LabelView(context);
        view.setModel(model, environment);
        return view;
    }

    @Override
    public void setModel(@NonNull LabelModel model, @NonNull Environment environment) {
        setId(model.getViewId());

        LayoutUtils.applyLabelModel(this, model);
        LayoutUtils.applyBorderAndBackground(this, model);

        if (!UAStringUtil.isEmpty(model.getContentDescription())) {
            setContentDescription(model.getContentDescription());
        }
    }
}
