/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.util;

import android.content.Context;

import com.urbanairship.android.layout.property.ConstrainedSize;
import com.urbanairship.android.layout.property.Margin;
import com.urbanairship.android.layout.property.Position;
import com.urbanairship.android.layout.property.Size;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.graphics.Insets;

import static androidx.constraintlayout.widget.ConstraintSet.BOTTOM;
import static androidx.constraintlayout.widget.ConstraintSet.LEFT;
import static androidx.constraintlayout.widget.ConstraintSet.PARENT_ID;
import static androidx.constraintlayout.widget.ConstraintSet.RIGHT;
import static androidx.constraintlayout.widget.ConstraintSet.TOP;
import static com.urbanairship.android.layout.util.ResourceUtils.dpToPx;

@SuppressWarnings("UnusedReturnValue")
public final class ConstraintSetBuilder {
    @NonNull
    public final ConstraintSet constraints;
    @NonNull
    private final Context context;

    private ConstraintSetBuilder(@NonNull Context context) {
        this(context, null);
    }

    private ConstraintSetBuilder(@NonNull Context context, @Nullable ConstraintSet constraints) {
        this.context = context;
        this.constraints = new ConstraintSet();
        if (constraints != null) {
            this.constraints.clone(constraints);
        }
    }

    @NonNull
    public static ConstraintSetBuilder newBuilder(@NonNull Context context) {
        return new ConstraintSetBuilder(context);
    }

    @NonNull
    public ConstraintSetBuilder constrainWithinParent(int viewId) {
        return constrainWithinParent(viewId, null);
    }

    @NonNull
    public ConstraintSetBuilder constrainWithinParent(int viewId, @Nullable Margin margin) {
        if (margin == null) {
            constraints.addToHorizontalChain(viewId, PARENT_ID, PARENT_ID);
            constraints.addToVerticalChain(viewId, PARENT_ID, PARENT_ID);
        } else {
            addToHorizontalChain(viewId, PARENT_ID, PARENT_ID, margin.getStart(), margin.getEnd());
            addToVerticalChain(viewId, PARENT_ID, PARENT_ID, margin.getTop(), margin.getBottom());
        }
        return this;
    }

    /**
     * Adds a view to a vertical chain.
     *
     * @param viewId view to add to a vertical chain
     * @param topId view above.
     * @param bottomId view below
     */
    @NonNull
    public ConstraintSetBuilder addToVerticalChain(int viewId, int topId, int bottomId, int marginTop, int marginBottom) {
        constraints.connect(viewId, TOP, topId, (topId == PARENT_ID) ? TOP : BOTTOM, (int) dpToPx(context, marginTop));
        constraints.connect(viewId, BOTTOM, bottomId, (bottomId == PARENT_ID) ? BOTTOM : TOP, (int) dpToPx(context, marginBottom));
        if (topId != PARENT_ID) {
            constraints.connect(topId, BOTTOM, viewId, TOP, 0);
        }
        if (bottomId != PARENT_ID) {
            constraints.connect(bottomId, TOP, viewId, BOTTOM, 0);
        }

        return this;
    }

    /**
     * Adds a view to a horizontal chain.
     *
     * @param viewId view to add
     * @param leftId view in chain to the left
     * @param rightId view in chain to the right
     */
    @NonNull
    public ConstraintSetBuilder addToHorizontalChain(int viewId, int leftId, int rightId, int leftMargin, int rightMargin) {
        constraints.connect(viewId, LEFT, leftId, (leftId == PARENT_ID) ? LEFT : RIGHT, (int) dpToPx(context, leftMargin));
        constraints.connect(viewId, RIGHT, rightId, (rightId == PARENT_ID) ? RIGHT : LEFT, (int) dpToPx(context, rightMargin));
        if (leftId != PARENT_ID) {
            constraints.connect(leftId, RIGHT, viewId, LEFT, 0);
        }
        if (rightId != PARENT_ID) {
            constraints.connect(rightId, LEFT, viewId, RIGHT, 0);
        }
        return this;
    }

    @NonNull
    public ConstraintSetBuilder setHorizontalChainStyle(@NonNull int[] viewIds, int chainStyle) {
        for (int i = 0; i < viewIds.length; i++) {
            constraints.setHorizontalChainStyle(viewIds[i], chainStyle);
        }
        return this;
    }

    @NonNull
    public ConstraintSetBuilder createHorizontalChainInParent(int[] viewIds, int verticalSpacing, int horizontalSpacing) {
        for (int i = 0; i < viewIds.length; i++) {
            int viewId = viewIds[i];
            if (i == 0) {
                addToHorizontalChain(viewId, PARENT_ID, viewIds[i +1], 0, horizontalSpacing);
            } else if (i == viewIds.length - 1) {
                addToHorizontalChain(viewId, viewIds[i - 1], PARENT_ID, horizontalSpacing, 0);
            } else {
                addToHorizontalChain(viewId, viewIds[i - 1], viewIds[i + 1], horizontalSpacing, horizontalSpacing);
            }

            addToVerticalChain(viewId, PARENT_ID, PARENT_ID, verticalSpacing, verticalSpacing);
        }

        return this;
    }

    @NonNull
    public ConstraintSetBuilder squareAspectRatio(int viewId) {
        constraints.setDimensionRatio(viewId, "1:1");
        return this;
    }

    @NonNull
    public ConstraintSetBuilder minWidth(int viewId, int minWidth) {
        constraints.constrainMinWidth(viewId, (int) dpToPx(context, minWidth));
        return this;
    }

    @NonNull
    public ConstraintSetBuilder maxWidth(int viewId, int maxWidth) {
        constraints.constrainMaxWidth(viewId, (int) dpToPx(context, maxWidth));
        return this;
    }

    @NonNull
    public ConstraintSetBuilder minHeight(int viewId, int minHeight) {
        constraints.constrainMinHeight(viewId, (int) dpToPx(context, minHeight));
        return this;
    }

    @NonNull
    public ConstraintSetBuilder maxHeight(int viewId, int maxHeight) {
        constraints.constrainMaxHeight(viewId, (int) dpToPx(context, maxHeight));
        return this;
    }

    @NonNull
    public ConstraintSetBuilder size(@Nullable Size size, @IdRes int viewId) {
        return size(size, false, viewId);
    }

    @NonNull
    public ConstraintSetBuilder size(@Nullable Size size, boolean ignoreSafeArea, @IdRes int viewId) {
        return size(size, ignoreSafeArea, viewId, ConstraintSet.WRAP_CONTENT);
    }

    @NonNull
    public ConstraintSetBuilder size(@Nullable Size size, boolean ignoreSafeArea, @IdRes int viewId, int autoValue) {
        if (size != null) {
            if (size instanceof ConstrainedSize) {
                ConstrainedSize constrainedSize = (ConstrainedSize) size;

                ConstrainedSize.ConstrainedDimension minWidth = constrainedSize.getMinWidth();
                if (minWidth != null) {
                    switch (minWidth.getType()) {
                        case PERCENT:
                            float minPixelsWidth = minWidth.getFloat() * ResourceUtils.getWindowWidthPixels(context, ignoreSafeArea);
                            constraints.constrainMinWidth(viewId, (int) minPixelsWidth);
                            break;
                        case ABSOLUTE:
                            constraints.constrainMinWidth(viewId, (int) dpToPx(context, minWidth.getInt()));
                            break;
                    }
                }

                ConstrainedSize.ConstrainedDimension maxWidth = constrainedSize.getMaxWidth();
                if (maxWidth != null) {
                    switch (maxWidth.getType()) {
                        case PERCENT:
                            float maxPixelsWidth = maxWidth.getFloat() * ResourceUtils.getWindowWidthPixels(context, ignoreSafeArea);
                            constraints.constrainMaxWidth(viewId, (int) maxPixelsWidth);
                            break;
                        case ABSOLUTE:
                            constraints.constrainMaxWidth(viewId, (int) dpToPx(context, maxWidth.getInt()));
                            break;
                    }
                }

                ConstrainedSize.ConstrainedDimension minHeight = constrainedSize.getMinHeight();
                if (minHeight != null) {
                    switch (minHeight.getType()) {
                        case PERCENT:
                            float minPixelsHeight = minHeight.getFloat() * ResourceUtils.getWindowHeightPixels(context, ignoreSafeArea);
                            constraints.constrainMinHeight(viewId, (int) minPixelsHeight);
                            break;
                        case ABSOLUTE:
                            constraints.constrainMinHeight(viewId, (int) dpToPx(context, minHeight.getInt()));
                            break;
                    }
                }

                ConstrainedSize.ConstrainedDimension maxHeight = constrainedSize.getMaxHeight();
                if (maxHeight != null) {
                    switch (maxHeight.getType()) {
                        case PERCENT:
                            float maxPixelsHeight = maxHeight.getFloat() * ResourceUtils.getWindowHeightPixels(context, ignoreSafeArea);
                            constraints.constrainMaxHeight(viewId, (int) maxPixelsHeight);
                            break;
                        case ABSOLUTE:
                            constraints.constrainMaxHeight(viewId, (int) dpToPx(context, maxHeight.getInt()));
                            break;
                    }
                }
            }

            Size.Dimension width = size.getWidth();
            switch (width.getType()) {
                case AUTO:
                    constraints.constrainWidth(viewId, autoValue);
                    break;
                case PERCENT:
                    if (width.getFloat() == 1f) {
                        constraints.constrainWidth(viewId, ConstraintSet.MATCH_CONSTRAINT);
                    } else {
                        constraints.constrainPercentWidth(viewId, width.getFloat());
                    }
                    break;
                case ABSOLUTE:
                    constraints.constrainWidth(viewId, (int) dpToPx(context, width.getInt()));
                    break;
            }

            Size.Dimension height = size.getHeight();
            switch (height.getType()) {
                case AUTO:
                    constraints.constrainHeight(viewId, autoValue);
                    break;
                case PERCENT:
                    if (height.getFloat() == 1f) {
                        constraints.constrainHeight(viewId, ConstraintSet.MATCH_CONSTRAINT);
                    } else {
                        constraints.constrainPercentHeight(viewId, height.getFloat());
                    }
                    break;
                case ABSOLUTE:
                    constraints.constrainHeight(viewId, (int) dpToPx(context, height.getInt()));
                    break;
            }
        }
        return this;
    }

    @NonNull
    public ConstraintSetBuilder position(@Nullable Position position, @IdRes int viewId) {
        if (position != null) {
            constrainWithinParent(viewId);

            switch (position.getHorizontal()) {
                case START:
                    constraints.setHorizontalBias(viewId, 0.0f);
                    break;
                case END:
                    constraints.setHorizontalBias(viewId, 1.0f);
                    break;
                case CENTER:
                    constraints.setHorizontalBias(viewId, 0.5f);
                    break;
            }

            switch (position.getVertical()) {
                case TOP:
                    constraints.setVerticalBias(viewId, 0.0f);
                    break;
                case BOTTOM:
                    constraints.setVerticalBias(viewId, 1.0f);
                    break;
                case CENTER:
                    constraints.setVerticalBias(viewId, 0.5f);
                    break;
            }
        }
        return this;
    }

    @NonNull
    public ConstraintSetBuilder margin(@Nullable Margin margin, @IdRes int viewId) {
        if (margin != null) {
            constraints.setMargin(viewId, TOP, (int) dpToPx(context, margin.getTop()));
            constraints.setMargin(viewId, BOTTOM, (int) dpToPx(context, margin.getBottom()));
            constraints.setMargin(viewId, ConstraintSet.START, (int) dpToPx(context, margin.getStart()));
            constraints.setMargin(viewId, ConstraintSet.END, (int) dpToPx(context, margin.getEnd()));
        }
        return this;
    }

    @NonNull
    public ConstraintSetBuilder margin(@Nullable Margin margin, @NonNull Insets insets, @IdRes int viewId) {
        if (margin == null) {
            margin = new Margin(0, 0, 0, 0);
        }
        constraints.setMargin(viewId, TOP, (int) dpToPx(context, margin.getTop()) + insets.top);
        constraints.setMargin(viewId, BOTTOM, (int) dpToPx(context, margin.getBottom()) + insets.bottom);
        constraints.setMargin(viewId, ConstraintSet.START, (int) dpToPx(context, margin.getStart()) + insets.left);
        constraints.setMargin(viewId, ConstraintSet.END, (int) dpToPx(context, margin.getEnd()) + insets.right);

        return this;
    }

    @NonNull
    public ConstraintSet build() {
        return constraints;
    }
}
