/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.model.ScrollLayoutModel;
import com.urbanairship.android.layout.property.Direction;
import com.urbanairship.android.layout.util.LayoutUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

public class ScrollLayoutView extends NestedScrollView implements BaseView<ScrollLayoutModel> {
    private ScrollLayoutModel model;

    public ScrollLayoutView(@NonNull Context context) {
        super(context);
        init();
    }

    public ScrollLayoutView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ScrollLayoutView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setId(generateViewId());
        setFillViewport(true);
    }

    @NonNull
    public static ScrollLayoutView create(@NonNull Context context, @NonNull ScrollLayoutModel model) {
        ScrollLayoutView view = new ScrollLayoutView(context);
        view.setModel(model);
        return view;
    }

    @Override
    public void setModel(@NonNull ScrollLayoutModel model) {
        this.model = model;
        configureScrollLayout();
    }

    private void configureScrollLayout() {
        LayoutUtils.applyBorderAndBackground(this, model);

        Direction direction = model.getDirection();
        View contentView = Thomas.view(getContext(), model.getView());

        LayoutParams layoutParams;
        if (direction == Direction.VERTICAL) {
            layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        } else {
            layoutParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        }

        contentView.setLayoutParams(layoutParams);
        addView(contentView);
    }
}
