/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.view;

import android.annotation.TargetApi;
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
import android.support.annotation.IntDef;
import android.support.v4.graphics.ColorUtils;
import android.util.AttributeSet;
import android.util.StateSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.PointerIcon;

import com.urbanairship.iam.ButtonInfo;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * In-app message button view.
 */
public class InAppButton extends InAppTextView {

    private static final float PRESSED_ALPHA_PERCENT = .2f;


    @IntDef(flag = true,
            value = { BORDER_RADIUS_TOP_LEFT,
                      BORDER_RADIUS_TOP_RIGHT,
                      BORDER_RADIUS_BOTTOM_RIGHT,
                      BORDER_RADIUS_BOTTOM_LEFT,
                      BORDER_RADIUS_ALL })
    @Retention(RetentionPolicy.SOURCE)
    public @interface BorderRadiusFlag {}

    /**
     * Top left border radius flag.
     */
    public static final int BORDER_RADIUS_TOP_LEFT = 1;

    /**
     * Top right border radius flag.
     */
    public static final int BORDER_RADIUS_TOP_RIGHT = 1 << 1;

    /**
     * Bottom Right border radius flag.
     */
    public static final int BORDER_RADIUS_BOTTOM_RIGHT = 1 << 2;

    /**
     * Bottom left border radius flag.
     */
    public static final int BORDER_RADIUS_BOTTOM_LEFT = 1 << 3;

    /**
     * Flag for all 4 corners.
     */
    public static final int BORDER_RADIUS_ALL = BORDER_RADIUS_TOP_LEFT | BORDER_RADIUS_TOP_RIGHT | BORDER_RADIUS_BOTTOM_RIGHT | BORDER_RADIUS_BOTTOM_LEFT;

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     */
    public InAppButton(Context context) {
        super(context);
    }

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     */
    public InAppButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     */
    public InAppButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     * @param defResStyle A resource identifier of a style resource that supplies default values for
     * the view, used only if defStyle is 0 or cannot be found in the theme. Can be 0 to not
     * look for defaults.
     */
    public InAppButton(Context context, AttributeSet attrs, int defStyle, int defResStyle) {
        super(context, attrs, defStyle, defResStyle);
    }

    /**
     * Sets the button info.
     *
     * @param buttonInfo The button info.
     * @param borderRadiusFlag The border radius flag.
     */
    public void setButtonInfo(final ButtonInfo buttonInfo, @BorderRadiusFlag int borderRadiusFlag) {
        setTextInfo(buttonInfo.getLabel());
        setBackground(createBackground(buttonInfo, borderRadiusFlag));


    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return InAppButton.class.getName();
    }

    @Override
    @TargetApi(Build.VERSION_CODES.N)
    public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex) {
        if (getPointerIcon() == null && isClickable() && isEnabled()) {
            return PointerIcon.getSystemIcon(getContext(), PointerIcon.TYPE_HAND);
        }
        return super.onResolvePointerIcon(event, pointerIndex);
    }

    /**
     * Creates the background drawable.
     *
     * @param buttonInfo The button info.
     * @param borderRadiusFlag The border radius flag.
     * @return The button's background drawable.
     */
    private Drawable createBackground(ButtonInfo buttonInfo, @BorderRadiusFlag int borderRadiusFlag) {
        int textColor = buttonInfo.getLabel().getColor();
        int pressedColor = ColorUtils.setAlphaComponent(textColor, Math.round(Color.alpha(textColor) * PRESSED_ALPHA_PERCENT));
        float cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, buttonInfo.getBorderRadius(), getResources().getDisplayMetrics());
        int strokeRadius = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics()));
        int borderColor = buttonInfo.getBorderColor() == Color.TRANSPARENT ? buttonInfo.getBackgroundColor() : buttonInfo.getBorderColor();

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadii(createRadiiArray(cornerRadius, borderRadiusFlag));
        background.setColor(buttonInfo.getBackgroundColor());
        background.setStroke(strokeRadius, borderColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ColorStateList list = ColorStateList.valueOf(pressedColor);
            RoundRectShape rectShape = new RoundRectShape(createRadiiArray(cornerRadius, borderRadiusFlag), null, null);
            ShapeDrawable mask = new ShapeDrawable(rectShape);
            return new RippleDrawable(list, background, mask);
        } else {
            GradientDrawable foreground = new GradientDrawable();
            foreground.setShape(GradientDrawable.RECTANGLE);
            foreground.setCornerRadii(createRadiiArray(cornerRadius, borderRadiusFlag));
            foreground.setColor(ColorUtils.compositeColors(pressedColor, buttonInfo.getBackgroundColor()));
            foreground.setStroke(strokeRadius, ColorUtils.compositeColors(pressedColor, borderColor));

            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[] { android.R.attr.state_pressed }, foreground);
            stateListDrawable.addState(StateSet.WILD_CARD, background);
            return stateListDrawable;
        }
    }

    /**
     * Creates the corner radius array.
     *
     * @param cornerRadius The corner radius.
     * @param borderRadiusFlag THe border radius flag.
     * @return The corner radius array.
     */
    private float[] createRadiiArray(float cornerRadius, @BorderRadiusFlag int borderRadiusFlag) {
        float[] radii = new float[8];

        // topLeftX, topLeftY, topRightX, topRightY, bottomRightX, bottomRightY, bottomLeftX, bottomLeftY

        if ((borderRadiusFlag & BORDER_RADIUS_TOP_LEFT) == BORDER_RADIUS_TOP_LEFT) {
            radii[0] = cornerRadius;
            radii[1] = cornerRadius;
        }

        if ((borderRadiusFlag & BORDER_RADIUS_TOP_RIGHT) == BORDER_RADIUS_TOP_RIGHT) {
            radii[2] = cornerRadius;
            radii[3] = cornerRadius;
        }

        if ((borderRadiusFlag & BORDER_RADIUS_BOTTOM_RIGHT) == BORDER_RADIUS_BOTTOM_RIGHT) {
            radii[4] = cornerRadius;
            radii[5] = cornerRadius;
        }

        if ((borderRadiusFlag & BORDER_RADIUS_BOTTOM_LEFT) == BORDER_RADIUS_BOTTOM_LEFT) {
            radii[6] = cornerRadius;
            radii[7] = cornerRadius;
        }

        return radii;
    }

}
