/* Copyright Airship and Contributors */

package com.urbanairship.iam.view;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * FrameLayout that supports max width.
 *
 * @hide
 */
public class BoundedFrameLayout extends FrameLayout {

    private final BoundedViewDelegate boundedViewDelegate;
    private final ClippableViewDelegate clippableViewDelegate;

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     */
    public BoundedFrameLayout(@NonNull Context context) {
        this(context, null);
    }

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     */
    public BoundedFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     */
    public BoundedFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.boundedViewDelegate = new BoundedViewDelegate(context, attrs, defStyle, 0);
        this.clippableViewDelegate = new ClippableViewDelegate();
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
    public BoundedFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle, int defResStyle) {
        super(context, attrs, defStyle, defResStyle);
        this.boundedViewDelegate = new BoundedViewDelegate(context, attrs, defStyle, defResStyle);
        this.clippableViewDelegate = new ClippableViewDelegate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(boundedViewDelegate.getWidthMeasureSpec(widthMeasureSpec), boundedViewDelegate.getHeightMeasureSpec(heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        clippableViewDelegate.onLayout(changed, l, t, r, b);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        clippableViewDelegate.onDraw(canvas);
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
        clippableViewDelegate.setClipPathBorderRadius(this, borderRadius);
    }

}
