/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.CheckableModel;
import com.urbanairship.android.layout.property.CheckboxStyle;
import com.urbanairship.android.layout.property.SwitchStyle;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.android.layout.util.ResourceUtils;
import com.urbanairship.android.layout.view.BaseView;
import com.urbanairship.util.UAStringUtil;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public abstract class CheckableView<M extends CheckableModel> extends FrameLayout implements BaseView<M> {
    @Dimension(unit = Dimension.DP)
    private static final int CHECKBOX_MIN_SIZE = 24;
    private static final int NO_MIN_SIZE = -1;

    private M model;
    private Environment environment;
    private CheckableViewAdapter<?> view = null;

    public CheckableView(@NonNull Context context) {
        super(context);
        init();
    }

    public CheckableView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CheckableView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setId(generateViewId());
    }

    protected int getMinimumSize() {
        switch (model.getToggleType()) {
            case CHECKBOX:
                return CHECKBOX_MIN_SIZE;
            default:
                return NO_MIN_SIZE;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int minSizeDp = getMinimumSize();
        if (minSizeDp == NO_MIN_SIZE) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            Logger.debug("onMeasure: (w/ min size) w = %s, h = %s", MeasureSpec.toString(widthMeasureSpec), MeasureSpec.toString(heightMeasureSpec));
            int minSize = (int) ResourceUtils.dpToPx(getContext(), minSizeDp);

            int widthSpec = widthMeasureSpec;
            int heightSpec = heightMeasureSpec;

            if (MeasureSpec.getMode(widthMeasureSpec) != EXACTLY) {
                widthSpec = MeasureSpec.makeMeasureSpec(minSize, EXACTLY);
            }
            if (MeasureSpec.getMode(heightMeasureSpec) != EXACTLY) {
                heightSpec = MeasureSpec.makeMeasureSpec(minSize, EXACTLY);
            }

            super.onMeasure(widthSpec, heightSpec);
        }
    }

    @Override
    public void setModel(@NonNull M model, @NonNull Environment environment) {
        this.model = model;
        this.environment = environment;
        configure();
    }

    protected M getModel() {
        return model;
    }

    protected Environment environment() {
        return environment;
    }

    public CheckableViewAdapter<?> getCheckableView() {
        return view;
    }

    protected void configure() {
        switch(model.getToggleType()) {
            case SWITCH:
                configureSwitch((SwitchStyle) model.getStyle());
                break;
            case CHECKBOX:
                LayoutUtils.applyBorderAndBackground(this, model);
                configureCheckbox((CheckboxStyle) model.getStyle());
                break;
        }

        if (!UAStringUtil.isEmpty(model.getContentDescription())) {
            view.setContentDescription(model.getContentDescription());
        }

        model.onInit();
    }

    protected void configureSwitch(SwitchStyle style) {
        SwitchCompat switchView = createSwitchView(style);
        LayoutUtils.applySwitchStyle(switchView, style);

        this.view = new CheckableViewAdapter.Switch(switchView);
        addView(switchView, MATCH_PARENT, MATCH_PARENT);
    }

    protected void configureCheckbox(CheckboxStyle style) {
        ShapeButton checkboxView = createCheckboxView(style);
        LayoutUtils.applyBorderAndBackground(checkboxView, model);

        view = new CheckableViewAdapter.Checkbox(checkboxView);
        addView(checkboxView, MATCH_PARENT, MATCH_PARENT);
    }

    @NonNull
    protected SwitchCompat createSwitchView(SwitchStyle style) {
        return new SwitchCompat(getContext());
    }

    @NonNull
    protected ShapeButton createCheckboxView(CheckboxStyle style) {
        CheckboxStyle.Binding checked = style.getBindings().getSelected();
        CheckboxStyle.Binding unchecked = style.getBindings().getUnselected();
        return new ShapeButton(getContext(), checked.getShapes(), unchecked.getShapes(), checked.getIcon(), unchecked.getIcon());
    }

    protected void setCheckedInternal(boolean isChecked) {
        view.setOnCheckedChangeListener(null);
        view.setChecked(isChecked);
        view.setOnCheckedChangeListener(checkedChangeListener);
    }

    protected final CheckableViewAdapter.OnCheckedChangeListener checkedChangeListener =
        (v, isChecked) -> model.onCheckedChange(isChecked);
}
