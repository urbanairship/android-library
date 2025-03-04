/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;

import com.urbanairship.android.layout.property.ConstrainedSize;
import com.urbanairship.android.layout.property.ConstrainedSize.ConstrainedDimension;

import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.urbanairship.android.layout.util.ResourceUtils.dpToPx;

public class ConstrainedViewDelegate {

    private final WeakReference<View> weakView;

    private final ConstrainedSize size;

    /**
     * Default constructor.
     */
    ConstrainedViewDelegate(@NonNull View view, @NonNull ConstrainedSize size) {
        weakView = new WeakReference<>(view);
        this.size = size;
    }

    @FunctionalInterface
    interface ChildMeasurer {
        void measureChild(@NonNull View child, int widthMeasureSpec, int heightMeasureSpec);
    }

    @FunctionalInterface
    interface Measurable {
        void onMeasure(int widthMeasureSpec, int heightMeasureSpec);
    }

    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec, @NonNull ChildMeasurer childMeasurer, @NonNull Measurable superMeasurer) {
        View view = weakView.get();
        if (view == null) {
            return;
        }

        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int maxWidth = 0;
        int maxHeight = 0;

        boolean wrapContentWidth = view.getLayoutParams().width == WRAP_CONTENT;
        boolean wrapContentHeight = view.getLayoutParams().height == WRAP_CONTENT;

        if (!wrapContentWidth) {
            maxWidth = widthSize;
        }

        if (!wrapContentHeight) {
            maxHeight = heightSize;
        }

        if (wrapContentWidth || wrapContentHeight) {
            if (view instanceof ViewGroup) {
                ViewGroup viewGroup = (ViewGroup) view;
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    View child = viewGroup.getChildAt(i);
                    childMeasurer.measureChild(child, widthMeasureSpec, heightMeasureSpec);
                    MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                    if (wrapContentWidth) {
                        maxWidth = Math.max(maxWidth, child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
                    }
                    if (wrapContentHeight) {
                        maxHeight = Math.max(maxHeight, child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
                    }
                }
            }

            int constrainedWidth = constrainDimension(size.getMinWidth(), size.getMaxWidth(), widthSize, maxWidth);
            int constrainedHeight = constrainDimension(size.getMinHeight(), size.getMaxHeight(), heightSize, maxHeight);

            widthMeasureSpec = MeasureSpec.makeMeasureSpec(constrainedWidth, MeasureSpec.EXACTLY);
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(constrainedHeight, MeasureSpec.EXACTLY);
        }

        superMeasurer.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }


    private int constrainDimension(@Nullable ConstrainedDimension min, @Nullable ConstrainedDimension max, int specSize, int measuredMaxSize) {
        View view = weakView.get();
        if (view == null) {
            return measuredMaxSize;
        }

        int constrainedDimension = measuredMaxSize;
        if (min != null) {
            int minSize = Integer.MIN_VALUE;
            switch (min.getType()) {
                case PERCENT:
                    minSize = specSize > 0 ? (int) (specSize * min.getFloat()) : minSize;
                    break;
                case ABSOLUTE:
                    minSize = (int) dpToPx(view.getContext(), min.getInt());
                    break;
            }
            if (constrainedDimension < minSize) {
                constrainedDimension = minSize;
            }
        }
        if (max != null) {
            int maxSize = Integer.MAX_VALUE;
            switch (max.getType()) {
                case PERCENT:
                    maxSize = specSize > 0 ? (int) (specSize * max.getFloat()) : maxSize;
                    break;
                case ABSOLUTE:
                    maxSize = (int) dpToPx(view.getContext(), max.getInt());
                    break;
            }
            if (constrainedDimension > maxSize) {
                constrainedDimension = maxSize;
            }
        }
        return constrainedDimension;
    }
}
