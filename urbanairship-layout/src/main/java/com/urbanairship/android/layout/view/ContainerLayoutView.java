/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.environment.ViewEnvironment;
import com.urbanairship.android.layout.info.ContainerItemInfo;
import com.urbanairship.android.layout.model.ContainerLayoutModel;
import com.urbanairship.android.layout.property.Margin;
import com.urbanairship.android.layout.util.ConstraintSetBuilder;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.android.layout.widget.ClippableConstraintLayout;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static androidx.core.view.WindowInsetsCompat.Type.systemBars;

public class ContainerLayoutView extends ClippableConstraintLayout implements BaseView {
    private final ContainerLayoutModel model;
    private final ViewEnvironment viewEnvironment;

    private final SparseBooleanArray frameShouldIgnoreSafeArea = new SparseBooleanArray();
    private final SparseArray<Margin> frameMargins = new SparseArray<>();

    public ContainerLayoutView(
            @NonNull Context context,
            @NonNull ContainerLayoutModel model,
            @NonNull ViewEnvironment viewEnvironment
    ) {
        super(context);
        this.model = model;
        this.viewEnvironment = viewEnvironment;

        setId(model.getViewId());

        configure();
    }

    private void configure() {
        setClipChildren(true);
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
        View itemView = Thomas.view(getContext(), item.getModel(), viewEnvironment);

        ViewGroup frame = new FrameLayout(getContext());
        int frameId = generateViewId();
        frame.setId(frameId);
        frame.addView(itemView, MATCH_PARENT, MATCH_PARENT);
        addView(frame);

        ContainerItemInfo info = item.getInfo();
        constraintBuilder
            .position(info.getPosition(), frameId)
            .size(info.getSize(), frameId)
            .margin(info.getMargin(), frameId);

        frameShouldIgnoreSafeArea.put(frameId, info.getIgnoreSafeArea());
        frameMargins.put(frameId, info.getMargin() != null ? info.getMargin() : Margin.NONE);
    }

    private class WindowInsetsListener implements androidx.core.view.OnApplyWindowInsetsListener {
        private final ConstraintSetBuilder constraintBuilder;

        public WindowInsetsListener(@NonNull ConstraintSetBuilder constraintBuilder) {
            this.constraintBuilder = constraintBuilder;
        }

        @NonNull
        @Override
        public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
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
