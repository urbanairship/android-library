/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.urbanairship.android.layout.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

/**
 * Variant of {@code LinearLayout} that replaces weight with max percentage sizes.
 * <p>
 * If any children specify max percents, any remaining space in the layout will be allocated evenly, up to the max size.
 */
@RestrictTo(LIBRARY_GROUP)
public class WeightlessLinearLayout extends ViewGroup {
    @IntDef({HORIZONTAL, VERTICAL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface OrientationMode {}
    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;

    private int orientation;

    private int gravity = GravityCompat.START | Gravity.TOP;

    private int totalLength;

    private static final String ACCESSIBILITY_CLASS_NAME = "com.urbanairship.android.layout.widget.WeightlessLinearLayout";

    public WeightlessLinearLayout(@NonNull Context context) {
        this(context, null);
    }

    public WeightlessLinearLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WeightlessLinearLayout(
        @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.WeightlessLinearLayout, defStyleAttr, 0);
        ViewCompat.saveAttributeDataForStyleable(this, context, R.styleable.WeightlessLinearLayout, attrs, typedArray, defStyleAttr, 0);

        int index = typedArray.getInt(R.styleable.WeightlessLinearLayout_android_orientation, -1);
        if (index >= 0) {
            setOrientation(index);
        }

        index = typedArray.getInt(R.styleable.WeightlessLinearLayout_android_gravity, -1);
        if (index >= 0) {
            setGravity(index);
        }

        typedArray.recycle();
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    /**
     * Should the layout be a column or a row.
     * @param orientation Pass {@link #HORIZONTAL} or {@link #VERTICAL}. Default
     * value is {@link #HORIZONTAL}.
     */
    public void setOrientation(@OrientationMode int orientation) {
        if (this.orientation != orientation) {
            this.orientation = orientation;
            requestLayout();
        }
    }

    /**
     * Returns the current orientation.
     *
     * @return either {@link #HORIZONTAL} or {@link #VERTICAL}
     */
    @OrientationMode
    public int getOrientation() {
        return orientation;
    }

    /**
     * Describes how the child views are positioned. Defaults to GRAVITY_TOP. If
     * this layout has a VERTICAL orientation, this controls where all the child
     * views are placed if there is extra vertical space. If this layout has a
     * HORIZONTAL orientation, this controls the alignment of the children.
     *
     * @param gravity See {@link android.view.Gravity}
     */
    public void setGravity(int gravity) {
        if (this.gravity != gravity) {
            if ((gravity & GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK) == 0) {
                gravity |= GravityCompat.START;
            }

            if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == 0) {
                gravity |= Gravity.TOP;
            }

            this.gravity = gravity;
            requestLayout();
        }
    }

    /**
     * Returns the current gravity. See {@link android.view.Gravity}
     *
     * @return the current gravity.
     * @see #setGravity
     */
    public int getGravity() {
        return gravity;
    }

    public void setHorizontalGravity(int horizontalGravity) {
        int gravity = horizontalGravity & GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK;
        if ((this.gravity & GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK) != gravity) {
            this.gravity = (this.gravity & ~GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK) | gravity;
            requestLayout();
        }
    }

    public void setVerticalGravity(int verticalGravity) {
        int gravity = verticalGravity & Gravity.VERTICAL_GRAVITY_MASK;
        if ((this.gravity & Gravity.VERTICAL_GRAVITY_MASK) != gravity) {
            this.gravity = (this.gravity & ~Gravity.VERTICAL_GRAVITY_MASK) | gravity;
            requestLayout();
        }
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    /**
     * Returns a set of layout parameters with a width of
     * {@link android.view.ViewGroup.LayoutParams#MATCH_PARENT}
     * and a height of {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT}
     * when the layout's orientation is {@link #VERTICAL}. When the orientation is
     * {@link #HORIZONTAL}, the width is set to {@link LayoutParams#WRAP_CONTENT}
     * and the height to {@link LayoutParams#WRAP_CONTENT}.
     */
    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        if (orientation == HORIZONTAL) {
            return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        } else if (orientation == VERTICAL) {
            return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        }
        return null;
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(ACCESSIBILITY_CLASS_NAME);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(ACCESSIBILITY_CLASS_NAME);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (orientation == VERTICAL) {
            measureVertical(widthMeasureSpec, heightMeasureSpec);
        } else {
            measureHorizontal(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (orientation == VERTICAL) {
            layoutVertical(l, t, r, b);
        } else {
            layoutHorizontal(l, t, r, b);
        }
    }

    /**
     * Helper for measuring children when in {@code VERTICAL} orientation.
     *
     * @param widthMeasureSpec width spec from parent view.
     * @param heightMeasureSpec height spec from parent view.
     */
    private void measureVertical(int widthMeasureSpec, int heightMeasureSpec) {
        totalLength = 0;
        int maxWidth = 0;
        int childState = 0;
        int alternativeMaxWidth = 0;
        int percentMaxWidth = 0;
        boolean allFillParent = true;

        int count = getChildCount();
        List<View> childrenWithMaxPercent = new ArrayList<>();

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        boolean matchWidth = false;
        boolean skippedMeasure = false;

        // See how tall everyone is. Also remember max width.
        for (int i = 0; i < count; ++i) {
            View child = getChildAt(i);

            if (child == null || child.getVisibility() == View.GONE) {
                continue;
            }

            LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if (lp.maxHeightPercent > 0) {
                childrenWithMaxPercent.add(child);
            }

            if (heightMode == MeasureSpec.EXACTLY && lp.height == 0 && lp.maxHeightPercent > 0) {
                // Optimization: don't bother measuring children who are going to use leftover space. These views will
                // get measured again down below if there is any leftover space.
                int totalLength = this.totalLength;
                this.totalLength = Math.max(totalLength, totalLength + lp.topMargin + lp.bottomMargin);
                skippedMeasure = true;
            } else {
                int oldHeight = Integer.MIN_VALUE;
                if (lp.height == 0 && lp.maxHeightPercent > 0) {
                    // heightMode is either UNSPECIFIED or AT_MOST, and this child wanted to stretch to fill available
                    // space. Translate that to WRAP_CONTENT so that it does not end up with a height of 0.
                    oldHeight = 0;
                    lp.height = LayoutParams.WRAP_CONTENT;
                }
                int childHorizontalMargins = lp.getMarginStart() + lp.getMarginEnd();
                int oldWidth = Integer.MIN_VALUE;
                if (lp.width == 0 && lp.maxWidthPercent > 0) {
                    oldWidth = 0;
                    lp.width = (int)(widthSize * lp.maxWidthPercent) - childHorizontalMargins;
                }

                // Determine how big this child would like to be.
                measureChildWithMargins(child,
                    widthMeasureSpec, childHorizontalMargins,
                    heightMeasureSpec, !childrenWithMaxPercent.isEmpty() ? totalLength : 0);

                if (oldHeight != Integer.MIN_VALUE) {
                    lp.height = oldHeight;
                }

                if (oldWidth != Integer.MIN_VALUE) {
                    lp.width = oldWidth;
                }

                int childHeight = child.getMeasuredHeight();
                int totalLength = this.totalLength;
                this.totalLength = Math.max(totalLength, totalLength + childHeight + lp.topMargin + lp.bottomMargin);
            }

            boolean matchWidthLocally = false;
            if (widthMode != MeasureSpec.EXACTLY && lp.width == LayoutParams.MATCH_PARENT) {
                // The width of the linear layout will scale, and at least one child said it wanted to match our width.
                // Set a flag indicating that we need to remeasure at least that view when we know our width.
                matchWidth = true;
                matchWidthLocally = true;
            }

            int margin = lp.getMarginStart() + lp.getMarginEnd();
            int measuredWidth = child.getMeasuredWidth() + margin;
            maxWidth = Math.max(maxWidth, measuredWidth);
            childState = View.combineMeasuredStates(childState, child.getMeasuredState());

            allFillParent = allFillParent && lp.width == LayoutParams.MATCH_PARENT;
            if (lp.maxHeightPercent > 0) {
                // Widths of max percentage Views are bogus if we end up remeasuring, so keep them separate.
                percentMaxWidth = Math.max(percentMaxWidth, matchWidthLocally ? margin : measuredWidth);
            } else {
                alternativeMaxWidth = Math.max(alternativeMaxWidth, matchWidthLocally ? margin : measuredWidth);
            }
        }

        // Add in our padding
        totalLength += getPaddingTop() + getPaddingBottom();

        // Check against our minimum height
        int height = totalLength;
        height = Math.max(height, getSuggestedMinimumHeight());

        // Reconcile our calculated size with the heightMeasureSpec
        int heightSizeAndState = View.resolveSizeAndState(height, heightMeasureSpec, 0);
        height = heightSizeAndState & View.MEASURED_SIZE_MASK;

        // Either expand children with percentage dimensions to take up available space or shrink them if they extend
        // beyond our current bounds.
        int delta = height - totalLength;
        if (skippedMeasure || (delta != 0 && !childrenWithMaxPercent.isEmpty())) {
            Collections.sort(childrenWithMaxPercent, (v1, v2) -> {
                float p1 = ((LayoutParams) v1.getLayoutParams()).maxHeightPercent;
                float p2 = ((LayoutParams) v2.getLayoutParams()).maxHeightPercent;
                return Float.compare(p1, p2);
            });

            int maxPercentCount = childrenWithMaxPercent.size();

            int lastChildIndex = maxPercentCount - 1;
            for (int i = 0; i < maxPercentCount; i++) {
                View child = childrenWithMaxPercent.get(i);
                LayoutParams lp = (LayoutParams) child.getLayoutParams();

                if (heightMode != MeasureSpec.UNSPECIFIED) {
                    float actualPercent;

                    if (delta >= (height * lp.maxHeightPercent) * (maxPercentCount - i)) {
                        actualPercent = lp.maxHeightPercent;
                    } else {
                        actualPercent = ((float) delta) / ((float)(childrenWithMaxPercent.size() - i)) / ((float) height);
                    }

                    int childHeight = (int) (actualPercent * height);
                    if (i == lastChildIndex) {
                        childHeight = Math.min(childHeight, delta);
                    }

                    delta -= childHeight;

                    int widthSpec;
                    if (lp.width == 0 && lp.maxWidthPercent > 0) {
                        int childWidth;
                        if (widthSize == 0 && widthMode == MeasureSpec.UNSPECIFIED) {
                            childWidth = LayoutParams.WRAP_CONTENT;
                            widthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.UNSPECIFIED);
                        } else {
                            childWidth = (int) (widthSize * lp.maxWidthPercent) - (lp.getMarginStart() + lp.getMarginEnd());
                            widthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY);
                        }
                    } else {
                        widthSpec = getChildMeasureSpec(widthMeasureSpec, getPaddingStart() + getPaddingEnd() + lp.getMarginStart() + lp.getMarginEnd(), lp.width);
                    }

                    int heightSpec = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY);
                    child.measure(widthSpec, heightSpec);

                    // Child may now not fit in vertical dimension.
                    childState = View.combineMeasuredStates(childState, child.getMeasuredState() & (View.MEASURED_STATE_MASK >> View.MEASURED_HEIGHT_STATE_SHIFT));
                }
            }

            // Determine width now that all views have been measured.
            for (int i = 0; i < count; i++) {
                View child = getChildAt(i);
                if (child == null || child.getVisibility() == View.GONE) {
                    continue;
                }

                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                int margin =  lp.getMarginStart() + lp.getMarginEnd();
                int measuredWidth = child.getMeasuredWidth() + margin;
                maxWidth = Math.max(maxWidth, measuredWidth);

                boolean matchWidthLocally = widthMode != MeasureSpec.EXACTLY && lp.width == LayoutParams.MATCH_PARENT;

                alternativeMaxWidth = Math.max(alternativeMaxWidth, matchWidthLocally ? margin : measuredWidth);

                allFillParent = allFillParent && lp.width == LayoutParams.MATCH_PARENT;

                int totalLength = this.totalLength;
                this.totalLength = Math.max(totalLength, totalLength + child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
            }

            totalLength += getPaddingTop() + getPaddingBottom();
        } else {
            alternativeMaxWidth = Math.max(alternativeMaxWidth, percentMaxWidth);
        }

        if (!allFillParent && widthMode != MeasureSpec.EXACTLY) {
            maxWidth = alternativeMaxWidth;
        }

        // Check against our minimum width
        maxWidth += getPaddingStart() + getPaddingEnd();
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());

        setMeasuredDimension(View.resolveSizeAndState(maxWidth, widthMeasureSpec, childState), heightSizeAndState);

        if (matchWidth) {
            forceUniformWidth(count, heightMeasureSpec);
        }
    }

    private void forceUniformWidth(int count, int heightMeasureSpec) {
        // Pretend that the linear layout has an exact size.
        int uniformMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY);
        for (int i = 0; i< count; ++i) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                LayoutParams lp = (LayoutParams) child.getLayoutParams();

                if (lp.width == LayoutParams.MATCH_PARENT) {
                    // Temporarily force children to reuse their old measured height
                    int oldHeight = lp.height;
                    lp.height = child.getMeasuredHeight();

                    // Remeasure with new dimensions
                    measureChildWithMargins(child, uniformMeasureSpec, 0, heightMeasureSpec, 0);
                    lp.height = oldHeight;
                }
            }
        }
    }

    /**
     * Helper for measuring children when in {@code HORIZONTAL} orientation.
     *
     * @param widthMeasureSpec width spec from parent view.
     * @param heightMeasureSpec height spec from parent view.
     */
    private void measureHorizontal(int widthMeasureSpec, int heightMeasureSpec) {
        totalLength = 0;
        int maxHeight = 0;
        int childState = 0;
        int alternativeMaxHeight = 0;
        int percentMaxHeight = 0;
        boolean allFillParent = true;

        int count = getChildCount();
        List<View> childrenWithMaxPercent = new ArrayList<>();

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);

        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        boolean matchHeight = false;
        boolean skippedMeasure = false;

        // See how wide everyone is. Also remember max height.
        for (int i = 0; i < count; ++i) {
            View child = getChildAt(i);

            if (child == null || child.getVisibility() == View.GONE) {
                continue;
            }

            LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if (lp.maxWidthPercent > 0) {
                childrenWithMaxPercent.add(child);
            }

            if (widthMode == MeasureSpec.EXACTLY && lp.width == 0 && lp.maxWidthPercent > 0) {
                // Optimization: don't bother measuring children who are going to use leftover space. These views will
                // get measured again down below if there is any leftover space.
                int totalLength = this.totalLength;
                this.totalLength = Math.max(totalLength, totalLength + lp.getMarginStart() + lp.getMarginEnd());
                skippedMeasure = true;
            } else {
                int oldWidth = Integer.MIN_VALUE;
                if (lp.width == 0 && lp.maxWidthPercent > 0) {
                    // widthMode is either UNSPECIFIED or AT_MOST, and this child wanted to stretch to fill available
                    // space. Translate that to WRAP_CONTENT so that it does not end up with a width of 0.
                    oldWidth = 0;
                    lp.width = LayoutParams.WRAP_CONTENT;
                }
                int childVerticalMargins = lp.topMargin + lp.bottomMargin;
                int oldHeight = Integer.MIN_VALUE;
                if (lp.height == 0 && lp.maxHeightPercent > 0) {
                    oldHeight = 0;
                    lp.height = (int)(heightSize * lp.maxHeightPercent) - childVerticalMargins;
                }

                // Determine how big this child would like to be.
                measureChildWithMargins(child,
                    widthMeasureSpec, !childrenWithMaxPercent.isEmpty() ? totalLength : 0,
                    heightMeasureSpec, childVerticalMargins);

                if (oldWidth != Integer.MIN_VALUE) {
                    lp.width = oldWidth;
                }

                if (oldHeight != Integer.MIN_VALUE) {
                    lp.height = oldHeight;
                }

                int childWidth = child.getMeasuredWidth();
                int totalLength = this.totalLength;
                this.totalLength = Math.max(totalLength, totalLength + childWidth + lp.getMarginStart() + lp.getMarginEnd());
            }

            boolean matchHeightLocally = false;
            if (heightMode != MeasureSpec.EXACTLY && lp.height == LayoutParams.MATCH_PARENT) {
                // The height of the linear layout will scale, and at least one child said it wanted to match our
                // height. Set a flag indicating that we need to remeasure at least that view when we know our height.
                matchHeight = true;
                matchHeightLocally = true;
            }

            int margin = lp.topMargin + lp.bottomMargin;
            int measuredHeight = child.getMeasuredHeight() + margin;
            maxHeight = Math.max(maxHeight, measuredHeight);
            childState = View.combineMeasuredStates(childState, child.getMeasuredState());

            allFillParent = allFillParent && lp.height == LayoutParams.MATCH_PARENT;
            if (lp.maxWidthPercent > 0) {
                // Heights of max percentage Views are bogus if we end up remeasuring, so keep them separate.
                percentMaxHeight = Math.max(percentMaxHeight, matchHeightLocally ? margin : measuredHeight);
            } else {
                alternativeMaxHeight = Math.max(alternativeMaxHeight, matchHeightLocally ? margin : measuredHeight);
            }
        }

        // Add in our padding
        totalLength += getPaddingStart() + getPaddingEnd();

        // Check against our minimum width
        int width = totalLength;
        width = Math.max(width, getSuggestedMinimumWidth());

        // Reconcile our calculated size with the heightMeasureSpec
        int widthSizeAndState = View.resolveSizeAndState(width, widthMeasureSpec, 0);
        width = widthSizeAndState & View.MEASURED_SIZE_MASK;

        // Either expand children with percentage dimensions to take up available space or shrink them if they extend
        // beyond our current bounds.
        int delta = width - totalLength;
        if (skippedMeasure || (delta != 0 && !childrenWithMaxPercent.isEmpty())) {
            Collections.sort(childrenWithMaxPercent, (v1, v2) -> {
                float p1 = ((LayoutParams) v1.getLayoutParams()).maxWidthPercent;
                float p2 = ((LayoutParams) v2.getLayoutParams()).maxWidthPercent;
                return Float.compare(p1, p2);
            });

            int maxPercentCount = childrenWithMaxPercent.size();

            int lastChildIndex = maxPercentCount - 1;
            for (int i = 0; i < maxPercentCount; i++) {
                View child = childrenWithMaxPercent.get(i);
                LayoutParams lp = (LayoutParams) child.getLayoutParams();

                if (widthMode != MeasureSpec.UNSPECIFIED) {
                    float actualPercent;

                    if (delta >= (width * lp.maxWidthPercent) * (maxPercentCount - i)) {
                        actualPercent = lp.maxWidthPercent;
                    } else {
                        actualPercent = ((float) delta) / ((float)(childrenWithMaxPercent.size() - i)) / ((float) width);
                    }

                    int childWidth = (int) (actualPercent * width);
                    if (i == lastChildIndex) {
                        childWidth = Math.min(childWidth, delta);
                    }

                    delta -= childWidth;

                    int heightSpec;
                    if (lp.height == 0 && lp.maxHeightPercent > 0) {
                        int childHeight;
                        if (heightSize == 0 && heightMode == MeasureSpec.UNSPECIFIED) {
                            childHeight = LayoutParams.WRAP_CONTENT;
                            heightSpec = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.UNSPECIFIED);
                        } else {
                            childHeight = (int)(heightSize * lp.maxHeightPercent) - (lp.topMargin + lp.bottomMargin);
                            heightSpec = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY);
                        }
                    } else {
                        heightSpec = getChildMeasureSpec(heightMeasureSpec, getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin, lp.height);
                    }

                    int widthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY);
                    child.measure(widthSpec, heightSpec);

                    // Child may now not fit in horizontal dimension.
                    childState = View.combineMeasuredStates(childState, child.getMeasuredState() & View.MEASURED_STATE_MASK);
                }
            }

            // Determine height now that all views have been measured.
            for (int i = 0; i < count; i++) {
                View child = getChildAt(i);
                if (child == null || child.getVisibility() == View.GONE) {
                    continue;
                }

                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                int margin =  lp.topMargin + lp.bottomMargin;
                int measuredHeight = child.getMeasuredHeight() + margin;
                maxHeight = Math.max(maxHeight, measuredHeight);

                boolean matchHeightLocally = widthMode != MeasureSpec.EXACTLY && lp.height == LayoutParams.MATCH_PARENT;

                alternativeMaxHeight = Math.max(alternativeMaxHeight, matchHeightLocally ? margin : measuredHeight);

                allFillParent = allFillParent && lp.height == LayoutParams.MATCH_PARENT;

                int totalLength = this.totalLength;
                this.totalLength = Math.max(totalLength, totalLength + child.getMeasuredWidth() + lp.getMarginStart() + lp.getMarginEnd());
            }

            totalLength += getPaddingStart() + getPaddingEnd();
        } else {
            alternativeMaxHeight = Math.max(alternativeMaxHeight, percentMaxHeight);
        }

        if (!allFillParent && heightMode != MeasureSpec.EXACTLY) {
            maxHeight = alternativeMaxHeight;
        }

        // Check against our minimum height
        maxHeight += getPaddingTop() + getPaddingBottom();
        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());

        setMeasuredDimension(widthSizeAndState, View.resolveSizeAndState(maxHeight, heightMeasureSpec, childState));

        if (matchHeight) {
            forceUniformHeight(count, widthMeasureSpec);
        }
    }

    private void forceUniformHeight(int count, int widthMeasureSpec) {
        // Pretend that the linear layout has an exact size. This is the measured height of
        // ourselves. The measured height should be the max height of the children, changed
        // to accommodate the heightMeasureSpec from the parent
        int uniformMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY);
        for (int i = 0; i < count; ++i) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                LayoutParams lp = (LayoutParams) child.getLayoutParams();

                if (lp.height == LayoutParams.MATCH_PARENT) {
                    // Temporarily force children to reuse their old measured width
                    int oldWidth = lp.width;
                    lp.width = child.getMeasuredWidth();

                    // Remeasure with new dimensions
                    measureChildWithMargins(child, widthMeasureSpec, 0, uniformMeasureSpec, 0);
                    lp.width = oldWidth;
                }
            }
        }
    }

    /**
     * Position the children during a layout pass if the orientation is set to {@link #VERTICAL}.
     *
     * @see #getOrientation()
     * @see #setOrientation(int)
     * @see #onLayout(boolean, int, int, int, int)
     */
    private void layoutVertical(int left, int top, int right, int bottom) {
        int paddingLeft = getPaddingLeft();

        int childTop;
        int childLeft;

        // Where right end of child should go
        int width = right - left;
        int childRight = width - getPaddingRight();

        // Space available for child
        int childSpace = width - paddingLeft - getPaddingRight();

        int count = getChildCount();

        int majorGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;
        int minorGravity = gravity & GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK;

        switch (majorGravity) {
            case Gravity.BOTTOM:
                // mTotalLength contains the padding already
                childTop = getPaddingTop() + bottom - top - totalLength;
                break;

            // mTotalLength contains the padding already
            case Gravity.CENTER_VERTICAL:
                childTop = getPaddingTop() + (bottom - top - totalLength) / 2;
                break;

            case Gravity.TOP:
            default:
                childTop = getPaddingTop();
                break;
        }

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                int childWidth = child.getMeasuredWidth();
                int childHeight = child.getMeasuredHeight();

                LayoutParams lp = (LayoutParams) child.getLayoutParams();

                int gravity = lp.gravity;
                if (gravity < 0) {
                    gravity = minorGravity;
                }
                int layoutDirection = ViewCompat.getLayoutDirection(this);
                int absoluteGravity = GravityCompat.getAbsoluteGravity(gravity, layoutDirection);
                switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.CENTER_HORIZONTAL:
                        childLeft = paddingLeft + ((childSpace - childWidth) / 2) + lp.leftMargin - lp.rightMargin;
                        break;

                    case Gravity.RIGHT:
                        childLeft = childRight - childWidth - lp.rightMargin;
                        break;

                    case Gravity.LEFT:
                    default:
                        childLeft = paddingLeft + lp.leftMargin;
                        break;
                }

                childTop += lp.topMargin;
                setChildFrame(child, childLeft, childTop, childWidth, childHeight);
                childTop += childHeight + lp.bottomMargin;
            }
        }
    }

    /**
     * Position the children during a layout pass if the orientation is set to {@link #HORIZONTAL}.
     *
     * @see #getOrientation()
     * @see #setOrientation(int)
     * @see #onLayout(boolean, int, int, int, int)
     */
    private void layoutHorizontal(int left, int top, int right, int bottom) {
        boolean isLayoutRtl = ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL;
        int paddingTop = getPaddingTop();

        int childTop;
        int childLeft;

        // Where bottom of child should go
        int height = bottom - top;
        int childBottom = height - getPaddingBottom();

        // Space available for child
        int childSpace = height - paddingTop - getPaddingBottom();

        int count = getChildCount();

        int majorGravity = gravity & GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK;
        int minorGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

        int layoutDirection = ViewCompat.getLayoutDirection(this);
        switch (GravityCompat.getAbsoluteGravity(majorGravity, layoutDirection)) {
            case Gravity.RIGHT:
                // mTotalLength contains the padding already
                childLeft = getPaddingLeft() + right - left - totalLength;
                break;

            case Gravity.CENTER_HORIZONTAL:

                // mTotalLength contains the padding already
                childLeft = getPaddingLeft() + (right - left - totalLength) / 2;
                break;

            case Gravity.LEFT:
            default:
                childLeft = getPaddingLeft();
                break;
        }

        int start = 0;
        int dir = 1;
        //In case of RTL, start drawing from the last child.
        if (isLayoutRtl) {
            start = count - 1;
            dir = -1;
        }

        for (int i = 0; i < count; i++) {
            int childIndex = start + dir * i;
            View child = getChildAt(childIndex);

            if (child.getVisibility() != GONE) {
                int childWidth = child.getMeasuredWidth();
                int childHeight = child.getMeasuredHeight();

                LayoutParams lp = (LayoutParams) child.getLayoutParams();

                int gravity = lp.gravity;
                if (gravity < 0) {
                    gravity = minorGravity;
                }

                switch (gravity & Gravity.VERTICAL_GRAVITY_MASK) {
                    case Gravity.TOP:
                        childTop = paddingTop + lp.topMargin;
                        break;

                    case Gravity.CENTER_VERTICAL:
                        childTop = paddingTop + ((childSpace - childHeight) / 2) + lp.topMargin - lp.bottomMargin;
                        break;

                    case Gravity.BOTTOM:
                        childTop = childBottom - childHeight - lp.bottomMargin;
                        break;
                    default:
                        childTop = paddingTop;
                        break;
                }

                childLeft += lp.leftMargin;
                setChildFrame(child, childLeft, childTop, childWidth, childHeight);
                childLeft += childWidth + lp.rightMargin;
            }
        }
    }

    private void setChildFrame(View child, int left, int top, int width, int height) {
        child.layout(left, top, left + width, top + height);
    }

    /**
     * Per-child layout information associated with WeightlessLinearLayout.
     */
    public static class LayoutParams extends ViewGroup.MarginLayoutParams {

        public float maxWidthPercent = 0;
        public float maxHeightPercent = 0;

        /**
         * Gravity for the view associated with these LayoutParams.
         *
         * @see android.view.Gravity
         */
        public int gravity = -1;

        /**
         * {@inheritDoc}
         */
        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.WeightlessLinearLayout_Layout);

            maxWidthPercent = a.getFloat(R.styleable.WeightlessLinearLayout_Layout_maxPercentWidth, 0);
            maxHeightPercent = a.getFloat(R.styleable.WeightlessLinearLayout_Layout_maxPercentHeight, 0);
            gravity = a.getInt(R.styleable.WeightlessLinearLayout_Layout_android_layout_gravity, -1);

            a.recycle();
        }

        /**
         * {@inheritDoc}
         */
        public LayoutParams(int width, int height) {
            super(width, height);
        }

        /**
         * Creates a new set of layout parameters with the specified width, height and max width/height percentages.
         *
         * @param width the width, either {@link #MATCH_PARENT}, {@link #WRAP_CONTENT} or a fixed size in pixels
         * @param height the height, either {@link #MATCH_PARENT}, {@link #WRAP_CONTENT} or a fixed size in pixels
         */
        public LayoutParams(int width, int height, float maxWidthPercent, float maxHeightPercent) {
            super(width, height);
            this.maxWidthPercent = maxWidthPercent;
            this.maxHeightPercent = maxHeightPercent;
        }

        /**
         * {@inheritDoc}
         */
        public LayoutParams(ViewGroup.LayoutParams p) {
            super(p);
        }

        /**
         * {@inheritDoc}
         */
        public LayoutParams(ViewGroup.MarginLayoutParams source) {
            super(source);
        }

        @NonNull
        @Override
        public String toString() {
            return String.format("LayoutParams{ width = %d, height = %d, maxWidth = %.2f, maxHeight = %.2f }",
                width, height, maxWidthPercent, maxHeightPercent);
        }
    }
}
