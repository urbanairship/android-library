/* Copyright Airship and Contributors */

package com.urbanairship.iam.view;

import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * View delegate that supports clipping a view to a border radius.
 */
class ClippableViewDelegate {

    private float borderRadius;

    // Used for fallback clipping
    private RectF rect;
    private Path clipPath;

    ClippableViewDelegate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            clipPath = new Path();
            rect = new RectF();
        }
    }

    /**
     * Must be called in the view {@link View#onLayout(boolean, int, int, int, int)}.
     *
     * @param changed This is a new size or position for this view
     * @param left Left position, relative to parent
     * @param top Top position, relative to parent
     * @param right Right position, relative to parent
     * @param bottom Bottom position, relative to parent
     */
    void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (changed && borderRadius > 0 && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            clipPath.reset();
            rect.set(0, 0, (right - left), (bottom - top));
            float[] radii = { borderRadius, borderRadius, borderRadius, borderRadius, borderRadius, borderRadius, borderRadius, borderRadius };
            clipPath.addRoundRect(rect, radii, Path.Direction.CW);
        }
    }

    /**
     * Must be called in the view {@link View#onDraw(Canvas)}.
     *
     * @param canvas The view's canvas.
     */
    void onDraw(@NonNull Canvas canvas) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && borderRadius != 0) {
            canvas.clipPath(this.clipPath);
        }
    }

    /**
     * Clips the view to the border radius.
     *
     * @param borderRadius The border radius.
     */
    @MainThread
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    void setClipPathBorderRadius(@NonNull View view, final float borderRadius) {
        final float borderRadiusPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, borderRadius, view.getResources().getDisplayMetrics());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (borderRadiusPixels == 0) {
                view.setClipToOutline(false);
                view.setOutlineProvider(ViewOutlineProvider.BOUNDS);
            } else {
                view.setClipToOutline(true);
                view.setOutlineProvider(new ViewOutlineProvider() {
                    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public void getOutline(@NonNull View view, @NonNull Outline outline) {
                        outline.setRoundRect(0,
                                0,
                                view.getRight() - view.getLeft(),
                                view.getBottom() - view.getTop(),
                                borderRadiusPixels);
                    }
                });
            }
        } else {
            this.borderRadius = borderRadiusPixels;
        }
    }

}
