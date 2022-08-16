/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;

import com.urbanairship.android.layout.environment.ViewEnvironment;
import com.urbanairship.android.layout.model.LabelModel;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.util.UAStringUtil;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;

public class LabelView extends AppCompatTextView implements BaseView {
    private final LabelModel model;

    public LabelView(@NonNull Context context, @NonNull LabelModel model, @NonNull ViewEnvironment viewEnvironment) {
        super(context);
        this.model = model;

        setId(model.getViewId());

        configure();
    }

    private void configure() {
        LayoutUtils.applyLabelModel(this, model);
        LayoutUtils.applyBorderAndBackground(this, model);

        if (!UAStringUtil.isEmpty(model.getContentDescription())) {
            setContentDescription(model.getContentDescription());
        }
    }
}
