/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.Checkable;
import android.widget.ImageView;

import com.urbanairship.android.layout.property.Image;
import com.urbanairship.android.layout.shape.Shape;

import java.util.List;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ShapeView extends ImageView implements Checkable, Clippable {

    private static final int[] CHECKED_STATE_SET = { android.R.attr.state_checked };

    private final ClippableViewDelegate clippableViewDelegate = new ClippableViewDelegate();

    private boolean isChecked = false;

    public ShapeView(Context context, List<Shape> checkedShapes, List<Shape> uncheckedShapes) {
        this(context, checkedShapes, uncheckedShapes, null, null);
    }

    public ShapeView(
        Context context,
        @NonNull List<Shape> checkedShapes,
        @NonNull List<Shape> uncheckedShapes,
        @Nullable Image.Icon checkedIcon,
        @Nullable Image.Icon uncheckedIcon
    ) {
        super(context);

        setId(generateViewId());
        setScaleType(ScaleType.CENTER_INSIDE);

        Drawable drawable = Shape.buildStateListDrawable(context, checkedShapes, uncheckedShapes, checkedIcon, uncheckedIcon);
        setImageDrawable(drawable);
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

    @Override
    public void setClipPathBorderRadius(float borderRadius) {
        clippableViewDelegate.setClipPathBorderRadius(this, borderRadius);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @MainThread
    public void setClipPathBorderRadius(float[] borderRadii) {
        clippableViewDelegate.setClipPathBorderRadii(this, borderRadii);
    }
}
