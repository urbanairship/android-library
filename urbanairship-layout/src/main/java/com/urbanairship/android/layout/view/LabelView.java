/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;

import com.google.android.material.textview.MaterialTextView;
import com.urbanairship.android.layout.model.LabelModel;
import com.urbanairship.android.layout.util.LayoutUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LabelView extends MaterialTextView implements BaseView<LabelModel> {
    private LabelModel model;

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
        setId(generateViewId());
    }

    @NonNull
    public static LabelView create(@NonNull Context context, @NonNull LabelModel model) {
        LabelView view = new LabelView(context);
        view.setModel(model);
        return view;
    }

    @Override
    public void setModel(@NonNull LabelModel model) {
        this.model = model;
        LayoutUtils.applyLabelModel(this, model);
    }
}
