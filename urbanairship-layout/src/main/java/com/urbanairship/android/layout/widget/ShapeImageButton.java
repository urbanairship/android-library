/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.Checkable;
import android.widget.ImageView;

import com.urbanairship.android.layout.shape.Shape;

import java.util.List;

import androidx.annotation.NonNull;

public class ShapeImageButton extends ImageView implements Checkable {

    private static final int[] CHECKED_STATE_SET = { android.R.attr.state_checked };

    private boolean isChecked = false;

    public ShapeImageButton(Context context, Shape checked, Shape unchecked) {
        super(context);

        setScaleType(ScaleType.CENTER_INSIDE);
        setImageDrawable(generateDrawable(context, checked, unchecked));
    }

    public ShapeImageButton(Context context, List<Shape> checked, List<Shape> unchecked) {
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

    private static StateListDrawable generateDrawable(
        @NonNull Context context,
        @NonNull List<Shape> checkedShapes,
        @NonNull List<Shape> uncheckedShapes
    ) {
        Drawable[] checkedLayers = new Drawable[checkedShapes.size()];
        for (int i = 0; i < checkedShapes.size(); i++) {
            checkedLayers[i] = checkedShapes.get(i).getDrawable(context);
        }
        LayerDrawable checkedDrawable = new LayerDrawable(checkedLayers);

        Drawable[] uncheckedLayers = new Drawable[uncheckedShapes.size()];
        for (int i = 0; i < uncheckedShapes.size(); i++) {
            uncheckedLayers[i] = uncheckedShapes.get(i).getDrawable(context);
        }
        LayerDrawable uncheckedDrawable = new LayerDrawable(uncheckedLayers);

        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(CHECKED_STATE_SET, checkedDrawable);
        drawable.addState(EMPTY_STATE_SET, uncheckedDrawable);

        return drawable;
    }
}
