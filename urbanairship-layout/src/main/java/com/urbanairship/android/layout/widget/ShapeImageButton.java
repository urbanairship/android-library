/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.widget.Checkable;
import android.widget.ImageView;

import com.urbanairship.android.layout.shape.Shape;

import androidx.annotation.NonNull;

public class ShapeImageButton extends ImageView implements Checkable {

    private static final int[] CHECKED_STATE_SET = { android.R.attr.state_checked };

    private boolean isChecked = false;

    public ShapeImageButton(Context context, Shape checked, Shape unchecked) {
        super(context);

        setScaleType(ScaleType.CENTER_INSIDE);
        setImageDrawable(generateDrawable(context, checked, unchecked));
    }

    @Override
    public void setChecked(boolean checked) {
        if (checked != isChecked) {
            isChecked = checked;
            refreshDrawableState();
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

    private static StateListDrawable generateDrawable(
        @NonNull Context context,
        @NonNull Shape checkedShape,
        @NonNull Shape uncheckedShape
    ) {
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(CHECKED_STATE_SET, checkedShape.getDrawable(context));
        drawable.addState(EMPTY_STATE_SET, uncheckedShape.getDrawable(context));
        return drawable;
    }

}
