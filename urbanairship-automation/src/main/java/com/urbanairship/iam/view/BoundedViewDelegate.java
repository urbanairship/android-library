/* Copyright Airship and Contributors */

package com.urbanairship.iam.view;

import android.content.Context;
import android.content.res.TypedArray;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import com.urbanairship.R;

/**
 * View delegate to support bounded views.
 */
class BoundedViewDelegate {

    private int maxWidth;
    private int maxHeight;

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
    BoundedViewDelegate(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle, int defResStyle) {
        if (attrs != null) {
            TypedArray attributes = context.getTheme().obtainStyledAttributes(attrs, R.styleable.UrbanAirshipLayout, defStyle, defResStyle);
            maxWidth = attributes.getDimensionPixelSize(R.styleable.UrbanAirshipLayout_urbanAirshipMaxWidth, 0);
            maxHeight = attributes.getDimensionPixelSize(R.styleable.UrbanAirshipLayout_urbanAirshipMaxHeight, 0);
            attributes.recycle();
        }
    }

    /**
     * Gets the measured width spec.
     *
     * @param widthMeasureSpec The view's measure width spec.
     * @return The measured width spec.
     */
    int getWidthMeasureSpec(int widthMeasureSpec) {
        int width = View.MeasureSpec.getSize(widthMeasureSpec);

        if (maxWidth > 0 && maxWidth < width) {
            widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.getMode(widthMeasureSpec));
        }

        return widthMeasureSpec;

    }

    /**
     * Gets the measured height spec.
     *
     * @param heightMeasureSpec The view's measure height spec.
     * @return The measured width spec.
     */
    int getHeightMeasureSpec(int heightMeasureSpec) {
        int height = View.MeasureSpec.getSize(heightMeasureSpec);

        if (maxHeight > 0 && maxHeight < height) {
            heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.getMode(heightMeasureSpec));
        }

        return heightMeasureSpec;
    }

}
