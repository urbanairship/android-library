package com.urbanairship.iam.view;
/* Copyright Airship and Contributors */

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.core.graphics.ColorUtils;
import android.util.StateSet;
import android.util.TypedValue;

/**
 * Generates a background with an optional pressed state.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class BackgroundDrawableBuilder {

    private int backgroundColor = Color.TRANSPARENT;
    private Integer strokeColor;
    private Integer pressedColor;
    private int strokeWidthDps = 0;
    private float borderRadiusDps = 0;
    @BorderRadius.BorderRadiusFlag
    private int borderRadiusFlag;
    private final Context context;

    /**
     * Create a new builder.
     *
     * @param context The application context.
     * @return The builder instance.
     */
    @NonNull
    public static BackgroundDrawableBuilder newBuilder(@NonNull Context context) {
        return new BackgroundDrawableBuilder(context);
    }

    /**
     * Default constructor.
     *
     * @param context The application context.
     */
    private BackgroundDrawableBuilder(@NonNull Context context) {
        this.context = context;
    }

    /**
     * Sets the pressed color.
     *
     * @param color The pressed color.
     * @return The builder instance.
     */
    @NonNull
    public BackgroundDrawableBuilder setPressedColor(@ColorInt int color) {
        this.pressedColor = color;
        return this;
    }

    /**
     * Sets the background color.
     *
     * @param color The background color.
     * @return The builder instance.
     */
    @NonNull
    public BackgroundDrawableBuilder setBackgroundColor(@ColorInt int color) {
        this.backgroundColor = color;
        return this;
    }

    /**
     * Sets the border radius.
     *
     * @param dps The border radius in DPs.
     * @param borderRadiusFlag Border radius flag.
     * @return The builder instance.
     */
    @NonNull
    public BackgroundDrawableBuilder setBorderRadius(float dps, @BorderRadius.BorderRadiusFlag int borderRadiusFlag) {
        this.borderRadiusFlag = borderRadiusFlag;
        this.borderRadiusDps = dps;
        return this;
    }

    /**
     * Sets the stroke width.
     *
     * @param dps The width in DPs.
     * @return The builder instance.
     */
    @NonNull
    public BackgroundDrawableBuilder setStrokeWidth(@Dimension int dps) {
        this.strokeWidthDps = dps;
        return this;
    }

    /**
     * Sets the stroke color. Defaults to the background color.
     *
     * @param strokeColor The stroke color.
     * @return The builder instance.
     */
    @NonNull
    public BackgroundDrawableBuilder setStrokeColor(@ColorInt int strokeColor) {
        this.strokeColor = strokeColor;
        return this;
    }

    /**
     * Builds the drawable.
     *
     * @return The background drawable.
     */
    @NonNull
    public Drawable build() {
        int strokeWidthPixels = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.strokeWidthDps, context.getResources().getDisplayMetrics()));
        int strokeColor = this.strokeColor == null ? this.backgroundColor : this.strokeColor;

        float borderRadiusPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.borderRadiusDps, context.getResources().getDisplayMetrics());
        float[] borderRadii = BorderRadius.createRadiiArray(borderRadiusPixels, this.borderRadiusFlag);

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadii(borderRadii);
        background.setColor(this.backgroundColor);
        background.setStroke(strokeWidthPixels, strokeColor);

        if (pressedColor == null) {
            return background;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ColorStateList list = ColorStateList.valueOf(pressedColor);
            RoundRectShape rectShape = new RoundRectShape(borderRadii, null, null);
            ShapeDrawable mask = new ShapeDrawable(rectShape);
            return new RippleDrawable(list, background, mask);
        } else {
            GradientDrawable foreground = new GradientDrawable();
            foreground.setShape(GradientDrawable.RECTANGLE);
            foreground.setCornerRadii(borderRadii);
            foreground.setColor(ColorUtils.compositeColors(pressedColor, backgroundColor));
            foreground.setStroke(strokeWidthPixels, ColorUtils.compositeColors(pressedColor, strokeColor));

            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[] { android.R.attr.state_pressed }, foreground);
            stateListDrawable.addState(StateSet.WILD_CARD, background);
            return stateListDrawable;
        }
    }

}
