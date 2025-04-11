/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import com.urbanairship.android.layout.property.HorizontalPosition;
import com.urbanairship.android.layout.shape.Shape;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ShapeDrawableWrapper extends DrawableWrapper {

    private final ShapeState state;

    private final Rect tempRect = new Rect();

    private HorizontalPosition gravityPosition;

    public ShapeDrawableWrapper(@NonNull Context context, @NonNull Shape shape) {
        this(shape.getDrawable(context), shape.getAspectRatio(), shape.getScale(), null);
    }

    public ShapeDrawableWrapper(@NonNull Drawable drawable, float aspectRatio, float scale, @Nullable HorizontalPosition gravityPosition) {
        this(new ShapeState(null), null);

        if (gravityPosition != null) {
            this.gravityPosition = gravityPosition;
        } else {
            this.gravityPosition = HorizontalPosition.CENTER;
        }

        state.aspectRatio = aspectRatio;
        state.scale = scale;

        setDrawable(drawable);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        Rect r = tempRect;
        r.set(bounds);

        int width;
        int height;
        if (state.aspectRatio == 1) {
            int minDimension = Math.min(bounds.width(), bounds.height());
            width = minDimension;
            height = minDimension;
        } else if (state.aspectRatio > 1) {
            width = bounds.width();
            height = (int) (bounds.height() / state.aspectRatio);
        } else {
            width = (int) (bounds.width() * state.aspectRatio);
            height = bounds.height();
        }

        width *= state.scale;
        height *= state.scale;

        int widthDiff = (bounds.width() - width) / 2;
        int heightDiff = (bounds.height() - height) / 2;

        switch (gravityPosition) {
            case CENTER:
                r.left += widthDiff;
                r.right -= widthDiff;
                break;
            case START:
                r.right -= widthDiff * 2;
                break;
            case END:
                r.left += widthDiff * 2;
                break;
        }
        r.top += heightDiff;
        r.bottom -= heightDiff;

        super.onBoundsChange(r);
    }

    @Override
    public int getIntrinsicWidth() {
        // Override intrinsic width so icons can be rendered full size within our bounds.
        return -1;
    }

    @Override
    public int getIntrinsicHeight() {
        // Override intrinsic height so icons can be rendered full size within our bounds.
        return -1;
    }

    @Override
    public final ConstantState getConstantState() {
        state.isChangingConfigurations |= getChangingConfigurations();
        return state;
    }

    static final class ShapeState extends DrawableWrapperState {
        int isChangingConfigurations;
        Drawable.ConstantState drawableState;
        Drawable cachedDrawable;
        float scale;
        float aspectRatio;

        ShapeState(@Nullable ShapeState orig) {
            super(orig, null);

            if (orig != null) {
                isChangingConfigurations = orig.isChangingConfigurations;
                drawableState = orig.drawableState;
                cachedDrawable = orig.cachedDrawable;
                scale = orig.scale;
                aspectRatio = orig.aspectRatio;
            }
        }

        @NonNull
        @Override
        public Drawable newDrawable(@Nullable Resources res) {
            return new ShapeDrawableWrapper(this, res);
        }
    }

    private ShapeDrawableWrapper(@Nullable ShapeState state, @Nullable Resources res) {
        super(state, res);

        this.state = state;
        this.gravityPosition = HorizontalPosition.CENTER;

        updateLocalState();
    }

    private void updateLocalState() {
        if (state != null && state.cachedDrawable != null) {
            setDrawable(state.cachedDrawable);
        }
    }
}
