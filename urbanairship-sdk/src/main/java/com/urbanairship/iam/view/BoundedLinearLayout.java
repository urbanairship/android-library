package com.urbanairship.iam.view;
/* Copyright 2017 Urban Airship and Contributors */


import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.urbanairship.R;

/**
 * LinearLayout that supports max width.
 */
public class BoundedLinearLayout extends LinearLayout {

    private int maxWidth;
    private int maxHeight;

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
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        if(maxWidth > 0 && maxWidth < width) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.getMode(widthMeasureSpec));
        }

        if(maxHeight > 0 && maxHeight < height) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.getMode(heightMeasureSpec));
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
