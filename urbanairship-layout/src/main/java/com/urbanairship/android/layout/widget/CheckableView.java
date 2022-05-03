/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

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
    private static final int CHECKBOX_MIN_DIMENSION = 24;
    @Dimension(unit = Dimension.DP)
    private static final int SWITCH_MIN_HEIGHT = 24;
    @Dimension(unit = Dimension.DP)
    private static final int SWITCH_MIN_WIDTH = 48;
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
    }

    protected int getMinWidth() {
        switch (model.getToggleType()) {
            case CHECKBOX:
                return CHECKBOX_MIN_DIMENSION;
            case SWITCH:
                return SWITCH_MIN_WIDTH;
            default:
                return NO_MIN_SIZE;
        }
    }

    protected int getMinHeight() {
        switch (model.getToggleType()) {
            case CHECKBOX:
                return CHECKBOX_MIN_DIMENSION;
            case SWITCH:
                return SWITCH_MIN_HEIGHT;
            default:
                return NO_MIN_SIZE;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int minWidthDp = getMinWidth();
        int minHeightDp = getMinHeight();
        if (minWidthDp == NO_MIN_SIZE && minHeightDp == NO_MIN_SIZE) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            int widthSpec = widthMeasureSpec;
            int heightSpec = heightMeasureSpec;

            if (minWidthDp != NO_MIN_SIZE) {
                int minWidth = (int) ResourceUtils.dpToPx(getContext(), minWidthDp);
                if (MeasureSpec.getMode(widthMeasureSpec) != EXACTLY) {
                    widthSpec = MeasureSpec.makeMeasureSpec(minWidth, EXACTLY);
                }
            }

            if (minHeightDp != NO_MIN_SIZE) {
                int minHeight = (int) ResourceUtils.dpToPx(getContext(), minHeightDp);
                if (MeasureSpec.getMode(heightMeasureSpec) != EXACTLY) {
                    heightSpec = MeasureSpec.makeMeasureSpec(minHeight, EXACTLY);
                }
            }

            super.onMeasure(widthSpec, heightSpec);
        }
    }

    @Override
    public void setModel(@NonNull M model, @NonNull Environment environment) {
        this.model = model;
        this.environment = environment;

        setId(model.getViewId());
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
                configureCheckbox((CheckboxStyle) model.getStyle());
                break;
        }
        LayoutUtils.applyBorderAndBackground(this, model);

        if (!UAStringUtil.isEmpty(model.getContentDescription())) {
            view.setContentDescription(model.getContentDescription());
        }

        model.onConfigured();
        LayoutUtils.doOnAttachToWindow(this, model::onAttachedToWindow);
    }

    protected void configureSwitch(SwitchStyle style) {
        SwitchCompat switchView = createSwitchView(style);
        switchView.setId(model.getCheckableViewId());

        LayoutUtils.applySwitchStyle(switchView, style);

        view = new CheckableViewAdapter.Switch(switchView);
        FrameLayout.LayoutParams lp = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
        lp.topMargin = -3;
        addView(switchView, lp);
    }

    protected void configureCheckbox(CheckboxStyle style) {
        ShapeButton checkboxView = createCheckboxView(style);
        checkboxView.setId(model.getCheckableViewId());
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
