/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.Checkable;
import android.widget.LinearLayout;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.R;
import com.urbanairship.android.layout.util.ResourceUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

/**
 * Layout that manages a dynamic number of checkable dot views used to indicate the
 * position of the current page in a set of pages.
 */
public class PagingIndicatorLayout extends LinearLayout {
    public PagingIndicatorLayout(Context context) {
        super(context);
        init();
    }

    public PagingIndicatorLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PagingIndicatorLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setId(generateViewId());
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
    }

    /**
     * Sets the number of indicator dots to be displayed.
     *
     * @param count The number of dots to display.
     */
    public void setCount(int count) {
        Logger.debug("setCount: %s", count);
        if (getChildCount() > 0) {
            removeAllViews();
        }
        int width = (int) ResourceUtils.dpToPx(getContext(), 24);
        for (int i = 0; i < count; i++) {
            int position = i;
            DotView view = new DotView(getContext());
            view.setOnClickListener(v -> onIndicatorClick(position));

            addView(view, new LayoutParams(width, MATCH_PARENT, 1f));
        }
    }

    /**
     * Updates the highlighted dot view in the indicator.
     *
     * @param position The position of the dot to highlight.
     */
    public void setPosition(int position) {
        for (int i = 0; i < getChildCount(); i++) {
            ((Checkable) getChildAt(i)).setChecked(i == position);
        }
    }

    /**
     * Called when an indicator dot is clicked.
     * <p>
     * Override in subclasses to specify custom behavior.
     *
     * @param position The position of the dot that was clicked.
     */
    protected void onIndicatorClick(int position) {}

    /** Checkable dot view. */
    public static class DotView extends AppCompatImageButton implements Checkable {
        private static final int[] STATE_SET = { android.R.attr.state_checked };

        private boolean isChecked = false;

        public DotView(@NonNull Context context) {
            super(context);
            init();
        }

        public DotView(@NonNull Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
            init();
        }

        public DotView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            init();
        }

        private void init() {
            setBackgroundResource(R.drawable.ua_layout_imagebutton_ripple);
            setImageResource(R.drawable.ua_dot_indicator);
            setScaleType(ScaleType.CENTER);
        }

        @Override
        public int[] onCreateDrawableState(int extraSpace) {
           int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
           if (isChecked()) {
               mergeDrawableStates(drawableState, STATE_SET);
           }
           return drawableState;
        }

        //
        // Checkable impl
        //

        /**
         * Change the checked state of the view
         *
         * @param checked The new checked state
         */
        @Override
        public void setChecked(boolean checked) {
            if (checked != isChecked) {
                isChecked = checked;
                refreshDrawableState();
            }
        }

        /**
         * Get the current checked state of the view.
         *
         * @return {@code true} if checked, {@code false} otherwise.
         */
        @Override
        public boolean isChecked() {
            return isChecked;
        }

        /**
         * Toggle the checked state of the view.
         */
        @Override
        public void toggle() {
            setChecked(!isChecked);
        }
    }
}
