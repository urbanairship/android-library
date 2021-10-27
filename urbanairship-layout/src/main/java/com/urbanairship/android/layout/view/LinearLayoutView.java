/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.urbanairship.android.layout.Layout;
import com.urbanairship.android.layout.model.LinearLayoutModel;
import com.urbanairship.android.layout.property.Direction;
import com.urbanairship.android.layout.property.Margin;
import com.urbanairship.android.layout.util.ConstraintSetBuilder;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

public class LinearLayoutView extends ConstraintLayout implements BaseView<LinearLayoutModel> {
    private LinearLayoutModel model;

    public LinearLayoutView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public LinearLayoutView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public LinearLayoutView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        setId(generateViewId());
    }

    @NonNull
    public static LinearLayoutView create(@NonNull Context context, @NonNull LinearLayoutModel model) {
        LinearLayoutView view = new LinearLayoutView(context);
        view.setModel(model);
        return view;
    }

    @Override
    public void setModel(@NonNull LinearLayoutModel model) {
        this.model = model;
        configureLinearLayout();
    }

    private void configureLinearLayout() {
        Direction direction = model.getDirection();
        List<LinearLayoutModel.Item> items = model.getItems();

        ConstraintSetBuilder constraintBuilder = ConstraintSetBuilder.newBuilder(getContext());

        addItems(items, direction, constraintBuilder);

        constraintBuilder.build().applyTo(this);

        // TODO: this isn't ideal, but top and bottom margins between items and parent don't work without it
        // see if there's a better way?
        post(this::requestLayout);
    }

    private void addItems(@NonNull List<LinearLayoutModel.Item> items, @NonNull Direction direction, @NonNull ConstraintSetBuilder constraintBuilder) {
        int[] viewIds = new int[items.size()];
        Margin[] margins = new Margin[items.size()];
        for (int i = 0; i < items.size(); i++) {
            LinearLayoutModel.Item item = items.get(i);
            viewIds[i] = addItemView(item, direction, constraintBuilder);
            margins[i] = item.getMargin() != null ? item.getMargin() : Margin.NONE;
        }

        if (direction == Direction.VERTICAL) {
            constraintBuilder.chainVertically(viewIds, margins);
        } else {
            constraintBuilder.chainHorizontally(viewIds, margins);
        }
    }

    private int addItemView(@NonNull LinearLayoutModel.Item item, @NonNull Direction direction, @NonNull ConstraintSetBuilder constraintBuilder) {
        View itemView = Layout.view(getContext(), item.getView());
        addView(itemView);

        int viewId = itemView.getId();
        constraintBuilder
            .size(item.getSize(), viewId)
            .margin(item.getMargin(), viewId)
            .weight(item.getWeight(), direction, viewId);

        if (direction == Direction.VERTICAL) {
            constraintBuilder
                .setVerticalChainStyle(viewId, ConstraintSet.CHAIN_PACKED)
                .setVerticalBias(viewId, 0);
        } else {
            constraintBuilder
                .setHorizontalChainStyle(viewId, ConstraintSet.CHAIN_PACKED)
                .setHorizontalBias(viewId, 0);
        }

        return itemView.getId();
    }
}
