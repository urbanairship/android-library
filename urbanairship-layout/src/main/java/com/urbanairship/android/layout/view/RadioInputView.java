/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.urbanairship.android.layout.model.RadioInputModel;
import com.urbanairship.android.layout.util.LayoutUtils;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class RadioInputView extends MaterialRadioButton implements BaseView<RadioInputModel> {
    private static final int[][] ENABLED_CHECKED_STATES =
        new int[][] {
            new int[] {android.R.attr.state_enabled, android.R.attr.state_checked}, // [0]
            new int[] {android.R.attr.state_enabled, -android.R.attr.state_checked}, // [1]
            new int[] {-android.R.attr.state_enabled, android.R.attr.state_checked}, // [2]
            new int[] {-android.R.attr.state_enabled, -android.R.attr.state_checked} // [3]
        };

    private RadioInputModel model;

    public RadioInputView(@NonNull Context context) {
        super(context);
        init();
    }

    public RadioInputView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RadioInputView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setId(generateViewId());
    }

    @NonNull
    public static RadioInputView create(@NonNull Context context, @NonNull RadioInputModel model) {
        RadioInputView view = new RadioInputView(context);
        view.setModel(model);
        return view;
    }

    @Override
    public void setModel(@NonNull RadioInputModel model) {
        this.model = model;
        configure();
    }

    private void configure() {
        LayoutUtils.applyBorderAndBackground(this, model.getBorder(), Color.TRANSPARENT);

        ColorStateList tintList = getTintList(
            model.getForegroundColor(),
            model.getBackgroundColor() != null ? model.getBackgroundColor() : Color.TRANSPARENT
        );

        setButtonTintList(tintList);
    }

    private ColorStateList getTintList(@ColorInt int foregroundColor, @ColorInt int backgroundColor) {
        int[] radioButtonColorList = new int[ENABLED_CHECKED_STATES.length];
        radioButtonColorList[0] =
            MaterialColors.layer(backgroundColor, foregroundColor, MaterialColors.ALPHA_FULL);
        radioButtonColorList[1] =
            MaterialColors.layer(backgroundColor, backgroundColor, MaterialColors.ALPHA_MEDIUM);
        radioButtonColorList[2] =
            MaterialColors.layer(backgroundColor, backgroundColor, MaterialColors.ALPHA_DISABLED);
        radioButtonColorList[3] =
            MaterialColors.layer(backgroundColor, backgroundColor, MaterialColors.ALPHA_DISABLED);

        return new ColorStateList(ENABLED_CHECKED_STATES, radioButtonColorList);
    }
}
