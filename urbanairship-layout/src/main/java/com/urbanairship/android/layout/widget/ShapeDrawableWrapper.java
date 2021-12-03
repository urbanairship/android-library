/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import com.urbanairship.android.layout.shape.Shape;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ShapeDrawableWrapper extends DrawableWrapper {

    private final ShapeState state;

    private final Rect tempRect = new Rect();

    public ShapeDrawableWrapper(@NonNull Context context, @NonNull Shape shape) {
        this(shape.getDrawable(context), shape.getAspectRatio(), shape.getScale());
    }

    public ShapeDrawableWrapper(@NonNull Drawable drawable, float aspectRatio, float scale) {
        this(new ShapeState(null), null);

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
            int maxDimension = Math.max(bounds.width(), bounds.height());
            width = maxDimension;
            height = maxDimension;
        } else if (state.aspectRatio > 1) {
            width = bounds.width();
            height = (int) (bounds.width() / state.aspectRatio);
        } else {
            width = (int) (bounds.height() * state.aspectRatio);
            height = bounds.height();
        }

        width *= state.scale;
        height *= state.scale;

        int widthDiff = (bounds.width() - width) / 2;
        int heightDiff = (bounds.height() - height) / 2;

        r.left += widthDiff;
        r.right -= widthDiff;
        r.top += heightDiff;
        r.bottom -= heightDiff;

        super.onBoundsChange(r);
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

        updateLocalState();
    }

    private void updateLocalState() {
        if (state != null && state.cachedDrawable != null) {
            setDrawable(state.cachedDrawable);
        }
    }
}
