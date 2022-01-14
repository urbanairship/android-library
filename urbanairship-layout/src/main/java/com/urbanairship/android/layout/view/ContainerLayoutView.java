/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.ContainerLayoutModel;
import com.urbanairship.android.layout.util.ConstraintSetBuilder;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.android.layout.widget.ClippableConstraintLayout;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsCompat;

public class ContainerLayoutView extends ClippableConstraintLayout implements BaseView<ContainerLayoutModel> {
    private ContainerLayoutModel model;
    private Environment environment;

    private final List<View> windowInsetViews = new ArrayList<>();

    public ContainerLayoutView(@NonNull Context context) {
        super(context);
        init();
    }

    public ContainerLayoutView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ContainerLayoutView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void init() {
        setId(generateViewId());
        setClipChildren(true);
    }

    @NonNull
    public static ContainerLayoutView create(@NonNull Context context, @NonNull ContainerLayoutModel model, @NonNull Environment environment) {
        ContainerLayoutView view = new ContainerLayoutView(context);
        view.setModel(model, environment);
        return view;
    }

    @Override
    public void setModel(@NonNull ContainerLayoutModel model, @NonNull Environment environment) {
        this.model = model;
        this.environment = environment;
        configureContainer();
    }

    private void configureContainer() {
        List<ContainerLayoutModel.Item> items = model.getItems();

        ConstraintSetBuilder constraintBuilder = ConstraintSetBuilder.newBuilder(getContext());
        addItems(items, constraintBuilder);
        LayoutUtils.applyBorderAndBackground(this, model);

        constraintBuilder.build().applyTo(this);

        if (environment.isIgnoringSafeAreas()) {
            // We need to set up insets after applying constraints so that initial margins will be available.
            for (View itemView : windowInsetViews) {
                LayoutUtils.doOnApplyWindowInsets(itemView, (v, insets, margins, padding) -> {
                    LayoutUtils.updateLayoutParams(itemView, lp -> {
                        lp.topMargin = margins.getTop() + insets.top;
                        lp.bottomMargin = margins.getBottom() + insets.bottom;
                        lp.leftMargin = margins.getLeft() + insets.left;
                        lp.rightMargin = margins.getRight() + insets.right;
                    });
                    return WindowInsetsCompat.CONSUMED;
                });
            }
        }
    }

    private void addItems(@NonNull List<ContainerLayoutModel.Item> items, @NonNull ConstraintSetBuilder constraintBuilder) {
        for (ContainerLayoutModel.Item item : items) {
            addItem(constraintBuilder, item);
        }
    }

    private void addItem(@NonNull ConstraintSetBuilder constraintBuilder, @NonNull ContainerLayoutModel.Item item) {
        View itemView = Thomas.view(getContext(), item.getView(), environment);
        addView(itemView);

        int viewId = itemView.getId();
        constraintBuilder
            .position(item.getPosition(), viewId)
            .size(item.getSize(), viewId)
            .margin(item.getMargin(), viewId);

        if (!item.shouldIgnoreSafeArea()) {
            windowInsetViews.add(itemView);
        }
    }
}
