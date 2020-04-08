/* Copyright Airship and Contributors */

package com.urbanairship.iam.view;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;

/**
 * Utils class to generate a border radius array.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class BorderRadius {

    @IntDef(flag = true,
            value = { TOP_LEFT,
                    TOP_RIGHT,
                    BOTTOM_RIGHT,
                    BOTTOM_LEFT,
                    ALL,
                    LEFT,
                    RIGHT,
                    BOTTOM,
                    TOP })
    @Retention(RetentionPolicy.SOURCE)
    public @interface BorderRadiusFlag {}

    /**
     * Top left border radius flag.
     */
    public static final int TOP_LEFT = 1;

    /**
     * Top right border radius flag.
     */
    public static final int TOP_RIGHT = 1 << 1;

    /**
     * Bottom Right border radius flag.
     */
    public static final int BOTTOM_RIGHT = 1 << 2;

    /**
     * Bottom left border radius flag.
     */
    public static final int BOTTOM_LEFT = 1 << 3;

    /**
     * Flag for all 4 corners.
     */
    public static final int ALL = TOP_LEFT | TOP_RIGHT | BOTTOM_RIGHT | BOTTOM_LEFT;

    /**
     * Flag for the left corners.
     */
    public static final int LEFT = TOP_LEFT | BOTTOM_LEFT;

    /**
     * Flag for the right corners.
     */
    public static final int RIGHT = TOP_RIGHT | BOTTOM_RIGHT;

    /**
     * Flag for the top corners.
     */
    public static final int TOP = TOP_LEFT | TOP_RIGHT;

    /**
     * Flag for the bottom corners.
     */
    public static final int BOTTOM = BOTTOM_LEFT | BOTTOM_RIGHT;

    /**
     * Creates the border radius array.
     *
     * @param pixels The border radius in pixels.
     * @param borderRadiusFlag THe border radius flag.
     * @return The corner radius array.
     */
    @NonNull
    public static float[] createRadiiArray(float pixels, @BorderRadiusFlag int borderRadiusFlag) {
        float[] radii = new float[8];

        // topLeftX, topLeftY, topRightX, topRightY, bottomRightX, bottomRightY, bottomLeftX, bottomLeftY

        if ((borderRadiusFlag & TOP_LEFT) == TOP_LEFT) {
            radii[0] = pixels;
            radii[1] = pixels;
        }

        if ((borderRadiusFlag & TOP_RIGHT) == TOP_RIGHT) {
            radii[2] = pixels;
            radii[3] = pixels;
        }

        if ((borderRadiusFlag & BOTTOM_RIGHT) == BOTTOM_RIGHT) {
            radii[4] = pixels;
            radii[5] = pixels;
        }

        if ((borderRadiusFlag & BOTTOM_LEFT) == BOTTOM_LEFT) {
            radii[6] = pixels;
            radii[7] = pixels;
        }

        return radii;
    }

    /**
     * Applies padding to the view to avoid from overlapping the border radius.
     *
     * @param view The view.
     * @param borderRadius The border radius.
     * @param borderRadiusFlag The border radius flags.
     */
    public static void applyBorderRadiusPadding(@NonNull View view, final float borderRadius, @BorderRadiusFlag final int borderRadiusFlag) {
        if (view.getWidth() == 0) {
            final WeakReference<View> weakReference = new WeakReference<>(view);
            view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    View view = weakReference.get();
                    if (view != null) {
                        applyBorderRadiusPadding(view, borderRadius, borderRadiusFlag);
                        view.getViewTreeObserver().removeOnPreDrawListener(this);
                    }
                    return false;
                }
            });
        }

        float borderRadiusPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, borderRadius, view.getResources().getDisplayMetrics());
        borderRadiusPixels = Math.min(borderRadiusPixels, Math.min(view.getHeight() / 2, view.getWidth() / 2));

        float x = borderRadiusPixels * (float) Math.sin(Math.toRadians(45.0));
        float y = borderRadiusPixels * (float) Math.sin(Math.toRadians(45.0));

        int borderPaddingX = Math.round(borderRadiusPixels - x);
        int borderPaddingY = Math.round(borderRadiusPixels - y);

        int paddingLeft = 0;
        int paddingRight = 0;
        int paddingTop = 0;
        int paddingBottom = 0;

        if ((borderRadiusFlag & TOP_LEFT) == TOP_LEFT) {
            paddingLeft = borderPaddingX;
            paddingTop = borderPaddingY;
        }

        if ((borderRadiusFlag & TOP_RIGHT) == TOP_RIGHT) {
            paddingRight = borderPaddingX;
            paddingTop = borderPaddingY;
        }

        if ((borderRadiusFlag & BOTTOM_RIGHT) == BOTTOM_RIGHT) {
            paddingRight = borderPaddingX;
            paddingBottom = borderPaddingY;
        }

        if ((borderRadiusFlag & BOTTOM_LEFT) == BOTTOM_LEFT) {
            paddingLeft = borderPaddingX;
            paddingBottom = borderPaddingY;
        }

        view.setPadding(view.getPaddingLeft() + paddingLeft,
                view.getPaddingTop() + paddingTop,
                view.getPaddingRight() + paddingRight,
                view.getPaddingBottom() + paddingBottom);
    }

}
