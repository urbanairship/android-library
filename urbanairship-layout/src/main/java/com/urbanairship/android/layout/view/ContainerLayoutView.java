/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.ContainerLayoutModel;
import com.urbanairship.android.layout.property.Margin;
import com.urbanairship.android.layout.util.ConstraintSetBuilder;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.android.layout.widget.ClippableConstraintLayout;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static androidx.core.view.WindowInsetsCompat.Type.systemBars;

public class ContainerLayoutView extends ClippableConstraintLayout implements BaseView<ContainerLayoutModel> {
    private ContainerLayoutModel model;
    private Environment environment;

    private final SparseBooleanArray frameShouldIgnoreSafeArea = new SparseBooleanArray();
    private final SparseArray<Margin> frameMargins = new SparseArray<>();

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

        setId(model.getViewId());
        configureContainer();
    }

    private void configureContainer() {
        List<ContainerLayoutModel.Item> items = model.getItems();

        ConstraintSetBuilder constraintBuilder = ConstraintSetBuilder.newBuilder(getContext());
        addItems(items, constraintBuilder);
        LayoutUtils.applyBorderAndBackground(this, model);

        constraintBuilder.build().applyTo(this);

        ViewCompat.setOnApplyWindowInsetsListener(this, new WindowInsetsListener(constraintBuilder));
    }

    private void addItems(@NonNull List<ContainerLayoutModel.Item> items, @NonNull ConstraintSetBuilder constraintBuilder) {
        for (ContainerLayoutModel.Item item : items) {
            addItem(constraintBuilder, item);
        }
    }

    private void addItem(@NonNull ConstraintSetBuilder constraintBuilder, @NonNull ContainerLayoutModel.Item item) {
        View itemView = Thomas.view(getContext(), item.getView(), environment);

        ViewGroup frame = new FrameLayout(getContext());
        int frameId = generateViewId();
        frame.setId(frameId);
        frame.addView(itemView, MATCH_PARENT, MATCH_PARENT);
        addView(frame);

        constraintBuilder
            .position(item.getPosition(), frameId)
            .size(item.getSize(), frameId)
            .margin(item.getMargin(), frameId);

        frameShouldIgnoreSafeArea.put(frameId, item.shouldIgnoreSafeArea());
        frameMargins.put(frameId, item.getMargin() != null ? item.getMargin() : Margin.NONE);
    }

    private class WindowInsetsListener implements androidx.core.view.OnApplyWindowInsetsListener {
        private final ConstraintSetBuilder constraintBuilder;

        public WindowInsetsListener(@NonNull ConstraintSetBuilder constraintBuilder) {
            this.constraintBuilder = constraintBuilder;
        }

        @Override
        public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat windowInsets) {
            WindowInsetsCompat applied = ViewCompat.onApplyWindowInsets(v, windowInsets);
            Insets insets = applied.getInsets(systemBars());
            if (applied.isConsumed() || insets.equals(Insets.NONE)) {
                return WindowInsetsCompat.CONSUMED;
            }

            boolean constraintsChanged = false;
            for (int i = 0; i < getChildCount(); i++) {
                ViewGroup child = (ViewGroup) getChildAt(i);
                boolean shouldIgnoreSafeArea = frameShouldIgnoreSafeArea.get(child.getId(), false);

                if (shouldIgnoreSafeArea) {
                    ViewCompat.dispatchApplyWindowInsets(child, applied);
                } else {
                    ViewCompat.dispatchApplyWindowInsets(child, applied.inset(insets));
                    // Handle insets by adding onto the child frame's margins.
                    Margin margin = frameMargins.get(child.getId());
                    constraintBuilder.margin(margin, insets, child.getId());
                    constraintsChanged = true;
                }
            }

            if (constraintsChanged) {
                constraintBuilder.build().applyTo(ContainerLayoutView.this);
            }

            return applied.inset(insets);
        }
    };
}
