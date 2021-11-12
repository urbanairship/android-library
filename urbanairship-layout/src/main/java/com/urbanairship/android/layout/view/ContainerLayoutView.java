/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.model.ContainerLayoutModel;
import com.urbanairship.android.layout.util.ConstraintSetBuilder;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.android.layout.widget.ClippableConstraintLayout;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ContainerLayoutView extends ClippableConstraintLayout implements BaseView<ContainerLayoutModel> {
    private ContainerLayoutModel model;

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
    }

    @NonNull
    public static ContainerLayoutView create(@NonNull Context context, @NonNull ContainerLayoutModel model) {
        ContainerLayoutView view = new ContainerLayoutView(context);
        view.setModel(model);
        return view;
    }

    @Override
    public void setModel(@NonNull ContainerLayoutModel model) {
        this.model = model;
        configureContainer();
    }

    private void configureContainer() {
        List<ContainerLayoutModel.Item> items = model.getItems();

        ConstraintSetBuilder constraintBuilder = ConstraintSetBuilder.newBuilder(getContext());

        addItems(items, constraintBuilder);
        LayoutUtils.applyBorderAndBackground(this, model);

        constraintBuilder.build().applyTo(this);
    }

    private void addItems(@NonNull List<ContainerLayoutModel.Item> items, @NonNull ConstraintSetBuilder constraintBuilder) {
        for (ContainerLayoutModel.Item item : items) {
            addItem(constraintBuilder, item);
        }
    }

    private void addItem(@NonNull ConstraintSetBuilder constraintBuilder, @NonNull ContainerLayoutModel.Item item) {
        View itemView = Thomas.view(getContext(), item.getView());
        addView(itemView);

        int viewId = itemView.getId();
        constraintBuilder
            .position(item.getPosition(), viewId)
            .size(item.getSize(), viewId)
            .margin(item.getMargin(), viewId);
    }
}
