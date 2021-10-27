/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.View;

import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.urbanairship.android.layout.Layout;
import com.urbanairship.android.layout.model.ContainerLayoutModel;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.util.ConstraintSetBuilder;
import com.urbanairship.android.layout.util.ResourceUtils;

import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

public class ContainerLayoutView extends ConstraintLayout implements BaseView<ContainerLayoutModel> {
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
        Border border = model.getBorder();
        // TODO: this isn't in the spec, but made it easier to debug sections of layouts... probably remove me!
        @ColorInt Integer backgroundColor = model.getBackgroundColor();

        ConstraintSetBuilder constraintBuilder = ConstraintSetBuilder.newBuilder(getContext());

        addItems(items, constraintBuilder);
        drawBorder(border, backgroundColor);

        constraintBuilder.build().applyTo(this);
    }

    private void addItems(@NonNull List<ContainerLayoutModel.Item> items, @NonNull ConstraintSetBuilder constraintBuilder) {
        for (ContainerLayoutModel.Item item : items) {
            addItem(constraintBuilder, item);
        }
    }

    private void addItem(@NonNull ConstraintSetBuilder constraintBuilder, @NonNull ContainerLayoutModel.Item item) {
        View itemView = Layout.view(getContext(), item.getView());
        addView(itemView);

        int viewId = itemView.getId();
        constraintBuilder
            .position(item.getPosition(), viewId)
            .size(item.getSize(), viewId)
            .maxSize(item.getMaxSize(), viewId)
            .margin(item.getMargin(), viewId);
    }

    private void drawBorder(@Nullable Border border, @Nullable @ColorInt Integer backgroundColor) {
        if (border != null) {
            float cornerRadius = border.getRadius() == null ? 0 : ResourceUtils.dpToPx(getContext(), border.getRadius());
            ShapeAppearanceModel shapeModel = ShapeAppearanceModel.builder()
                                                                  .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
                                                                  .build();
            MaterialShapeDrawable shapeDrawable = new MaterialShapeDrawable(shapeModel);

            if (border.getStrokeWidth() != null) {
                float strokeWidth = ResourceUtils.dpToPx(getContext(), border.getStrokeWidth());
                shapeDrawable.setStrokeWidth(strokeWidth);
            }

            if (border.getStrokeColor() != null) {
                shapeDrawable.setStrokeColor(ColorStateList.valueOf(border.getStrokeColor()));
            }

            @ColorInt int fillColor = backgroundColor != null
                ? backgroundColor
                : ResourceUtils.getColorAttr(getContext(), android.R.attr.colorBackground);

            shapeDrawable.setFillColor(ColorStateList.valueOf(fillColor));

            setBackground(shapeDrawable);
        } else if (backgroundColor != null) {
            setBackgroundColor(backgroundColor);
        }
    }
}
