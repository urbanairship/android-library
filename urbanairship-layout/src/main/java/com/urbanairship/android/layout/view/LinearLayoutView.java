/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;

import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.LinearLayoutModel;
import com.urbanairship.android.layout.property.Direction;
import com.urbanairship.android.layout.property.Margin;
import com.urbanairship.android.layout.property.Size;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.android.layout.widget.WeightlessLinearLayout;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import static com.urbanairship.android.layout.util.ResourceUtils.dpToPx;

public class LinearLayoutView extends WeightlessLinearLayout implements BaseView<LinearLayoutModel> {

    private LinearLayoutModel model;
    private Environment environment;

    public LinearLayoutView(@NonNull Context context) {
        super(context);
        init();
    }

    public LinearLayoutView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LinearLayoutView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClipChildren(false);
    }

    @NonNull
    public static LinearLayoutView create(@NonNull Context context, @NonNull LinearLayoutModel model, @NonNull Environment environment) {
        LinearLayoutView view = new LinearLayoutView(context);
        view.setModel(model, environment);
        return view;
    }

    @Override
    public void setModel(@NonNull LinearLayoutModel model, @NonNull Environment environment) {
        this.model = model;
        this.environment = environment;

        setId(model.getViewId());
        configureLinearLayout();
    }

    private void configureLinearLayout() {
        LayoutUtils.applyBorderAndBackground(this, model);

        setOrientation(model.getDirection() == Direction.VERTICAL ? VERTICAL : HORIZONTAL);
        setGravity(model.getDirection() == Direction.VERTICAL ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);

        addItems(model.getItems());

        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            WindowInsetsCompat noInsets = new WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
                    .build();

            for (int i = 0; i < getChildCount(); i++) {
                ViewCompat.dispatchApplyWindowInsets(getChildAt(i), noInsets);
            }

            return noInsets;
        });
    }

    private void addItems(List<LinearLayoutModel.Item> items) {
        for (int i = 0; i < items.size(); i++) {
            LinearLayoutModel.Item item = items.get(i);
            LayoutParams lp = generateItemLayoutParams(item);

            View itemView = Thomas.view(getContext(), item.getView(), environment);
            itemView.setLayoutParams(lp);
            // Add view after any existing children, without requesting a layout pass on the child.
            addViewInLayout(itemView, -1, lp, true);
        }
    }

    private WeightlessLinearLayout.LayoutParams generateItemLayoutParams(@NonNull LinearLayoutModel.Item item) {
        int width = 0;
        int height = 0;
        float maxWidthPercent = 0;
        float maxHeightPercent = 0;

        Size size = item.getSize();
        Size.Dimension w = size.getWidth();
        switch (w.getType()) {
            case AUTO:
                width = LayoutParams.WRAP_CONTENT;
                maxWidthPercent = 0;
                break;
            case ABSOLUTE:
                width = (int) dpToPx(getContext(), w.getInt());
                maxWidthPercent = 0;
                break;
            case PERCENT:
                width = 0;
                maxWidthPercent = w.getFloat();
                break;
        }

        Size.Dimension h = size.getHeight();
        switch (h.getType()) {
            case AUTO:
                height = LayoutParams.WRAP_CONTENT;
                maxHeightPercent = 0;
                break;
            case ABSOLUTE:
                height = (int) dpToPx(getContext(), h.getInt());
                maxHeightPercent = 0;
                break;
            case PERCENT:
                height = 0;
                maxHeightPercent = h.getFloat();
                break;
        }

        LayoutParams lp = new LayoutParams(width, height, maxWidthPercent, maxHeightPercent);

        Margin margin = item.getMargin();
        if (margin != null) {
            lp.topMargin = (int) dpToPx(getContext(), margin.getTop());
            lp.bottomMargin = (int) dpToPx(getContext(), margin.getBottom());
            lp.setMarginStart((int) dpToPx(getContext(), margin.getStart()));
            lp.setMarginEnd((int) dpToPx(getContext(), margin.getEnd()));
        }

        return lp;
    }
}
