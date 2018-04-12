/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.support.annotation.MainThread;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;

import com.urbanairship.R;

/**
 * LinearLayout that supports max width.
 *
 * @hide
 */
public class BoundedLinearLayout extends LinearLayout {

    private int maxWidth;
    private int maxHeight;

    // Used for fallback clipping
    private float borderRadius;
    private RectF rect;
    private Path clipPath;

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     */
    public BoundedLinearLayout(Context context) {
        this(context, null);
    }

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     */
    public BoundedLinearLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     */
    public BoundedLinearLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle, 0);
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
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public BoundedLinearLayout(Context context, AttributeSet attrs, int defStyle, int defResStyle) {
        super(context, attrs, defStyle, defResStyle);
        init(context, attrs, defStyle, defResStyle);
    }

    /**
     * Initializes the view.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     * @param defResStyle A resource identifier of a style resource that supplies default values for
     * the view, used only if defStyle is 0 or cannot be found in the theme. Can be 0 to not
     * look for defaults.
     */
    private void init(Context context, AttributeSet attrs, int defStyle, int defResStyle) {
        if (attrs != null) {
            TypedArray attributes = context.getTheme().obtainStyledAttributes(attrs, R.styleable.UrbanAirshipLayout, defStyle, defResStyle);
            maxWidth = attributes.getDimensionPixelSize(R.styleable.UrbanAirshipLayout_urbanAirshipMaxWidth, 0);
            maxHeight = attributes.getDimensionPixelSize(R.styleable.UrbanAirshipLayout_urbanAirshipMaxHeight, 0);
            attributes.recycle();
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            clipPath = new Path();
            rect = new RectF();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        if (maxWidth > 0 && maxWidth < width) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.getMode(widthMeasureSpec));
        }

        if (maxHeight > 0 && maxHeight < height) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.getMode(heightMeasureSpec));
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (changed && borderRadius > 0 && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            clipPath.reset();
            rect.set(0, 0, (r - l), (b - t));
            float[] radii = { borderRadius, borderRadius, borderRadius, borderRadius, borderRadius, borderRadius, borderRadius, borderRadius };
            clipPath.addRoundRect(rect, radii, Path.Direction.CW);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && borderRadius != 0) {
            canvas.clipPath(this.clipPath);
        }

        super.onDraw(canvas);
    }

    /**
     * Clips the view to the border radius.
     *
     * @param borderRadius The border radius.
     */
    @MainThread
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void setClipPathBorderRadius(final float borderRadius) {
        final float borderRadiusPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, borderRadius, getResources().getDisplayMetrics());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (borderRadiusPixels == 0) {
                this.setClipToOutline(false);
                this.setOutlineProvider(ViewOutlineProvider.BOUNDS);
            } else {
                this.setClipToOutline(true);
                setOutlineProvider(new ViewOutlineProvider() {
                    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public void getOutline(View view, Outline outline) {
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
