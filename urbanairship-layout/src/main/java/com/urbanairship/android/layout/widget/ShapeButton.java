/*
 Copyright Airship and Contributors
 */

package com.urbanairship.android.layout.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.SoundEffectConstants;
import android.widget.Checkable;

import com.urbanairship.android.layout.R;
import com.urbanairship.android.layout.property.TextAppearance;
import com.urbanairship.android.layout.shape.Shape;
import com.urbanairship.android.layout.util.LayoutUtils;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;

public class ShapeButton extends AppCompatButton implements Checkable {

    private static final int[] CHECKED_STATE_SET = { android.R.attr.state_checked };

    @NonNull
    private final TextAppearance checkedTextAppearance;
    @NonNull
    private final TextAppearance uncheckedTextAppearance;
    private final String text;

    private boolean isChecked = false;

    public ShapeButton(
        @NonNull Context context,
        @NonNull String text,
        @NonNull List<Shape> checkedShapes,
        @NonNull List<Shape> uncheckedShapes,
        @NonNull TextAppearance checkedTextAppearance,
        @NonNull TextAppearance uncheckedTextAppearance
    ) {
        super(context);

        setId(generateViewId());

        this.checkedTextAppearance = checkedTextAppearance;
        this.uncheckedTextAppearance = uncheckedTextAppearance;
        this.text = text;

        Drawable background = Shape.buildStateListDrawable(context, checkedShapes, uncheckedShapes);
        setBackground(background);
        setForeground(ContextCompat.getDrawable(context, R.drawable.ua_layout_imagebutton_ripple));

        setText(text);
        updateText();

        setPadding(0, 0, 0, 0);
        setGravity(Gravity.CENTER);
    }

    @Override
    public void setChecked(boolean checked) {
        if (checked != isChecked) {
            isChecked = checked;
            refreshDrawableState();
            updateText();
        }
    }

    @Override
    public boolean isChecked() {
        return isChecked;
    }

    @Override
    public void toggle() {
        setChecked(!isChecked);
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (isChecked()) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET);
        }
        return drawableState;
    }

    @Override
    public boolean performClick() {
        toggle();

        boolean handled = super.performClick();
        if (!handled) {
            // View only makes a sound effect if the onClickListener was
            // called, so we'll need to make one here instead.
            playSoundEffect(SoundEffectConstants.CLICK);
        }

        return handled;
    }

    private void updateText() {
        LayoutUtils.applyTextAppearance(this, isChecked() ? checkedTextAppearance : uncheckedTextAppearance);
    }
}
