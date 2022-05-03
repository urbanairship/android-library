/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.ScrollLayoutModel;
import com.urbanairship.android.layout.property.Direction;
import com.urbanairship.android.layout.util.LayoutUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;

public class ScrollLayoutView extends NestedScrollView implements BaseView<ScrollLayoutModel> {
    private ScrollLayoutModel model;
    private Environment environment;

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
        setFillViewport(false);
    }

    @NonNull
    public static ScrollLayoutView create(
        @NonNull Context context,
        @NonNull ScrollLayoutModel model,
        @NonNull Environment environment
    ) {
        ScrollLayoutView view = new ScrollLayoutView(context);
        view.setModel(model, environment);
        return view;
    }

    @Override
    public void setModel(@NonNull ScrollLayoutModel model, @NonNull Environment environment) {
        this.model = model;
        this.environment = environment;

        setId(model.getViewId());
        configureScrollLayout();
    }

    private void configureScrollLayout() {
        LayoutUtils.applyBorderAndBackground(this, model);

        Direction direction = model.getDirection();
        View contentView = Thomas.view(getContext(), model.getView(), environment);

        LayoutParams layoutParams;
        if (direction == Direction.VERTICAL) {
            layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        } else {
            layoutParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        }

        setClipToOutline(true);
        contentView.setLayoutParams(layoutParams);
        addView(contentView);

        // Pass along any calls to apply insets to the view.
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) ->
            ViewCompat.dispatchApplyWindowInsets(contentView, insets)
        );
    }
}
