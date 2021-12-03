/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import android.content.Context;
import android.widget.Checkable;
import android.widget.ImageView;

import com.urbanairship.android.layout.shape.Shape;

import java.util.List;

public class ShapeView extends ImageView implements Checkable {

    private static final int[] CHECKED_STATE_SET = { android.R.attr.state_checked };

    private boolean isChecked = false;

    public ShapeView(Context context, List<Shape> checked, List<Shape> unchecked) {
        super(context);

        setId(generateViewId());

        setScaleType(ScaleType.CENTER_INSIDE);
        setImageDrawable(Shape.buildStateListDrawable(context, checked, unchecked));
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
}
