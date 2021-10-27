/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.util;

import android.content.Context;
import android.util.Log;

import com.urbanairship.android.layout.property.Direction;
import com.urbanairship.android.layout.property.Margin;
import com.urbanairship.android.layout.property.Position;
import com.urbanairship.android.layout.property.Size;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintSet;

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
    public static ConstraintSetBuilder newBuilder(@NonNull Context context, @NonNull ConstraintSet constraints) {
        return new ConstraintSetBuilder(context, constraints);
    }

    @NonNull
    public ConstraintSetBuilder constrainWithinParent(int viewId) {
        constraints.addToHorizontalChain(viewId, PARENT_ID, PARENT_ID);
        constraints.addToVerticalChain(viewId, PARENT_ID, PARENT_ID);
        return this;
    }

    @NonNull
    public ConstraintSetBuilder chainVertically(@NonNull int[] viewIds, @NonNull Margin[] margins) {
        for (int i = 0; i < viewIds.length; i++) {
            int viewId = viewIds[i];
            Margin margin = margins[i];

            int topId = i == 0 ? PARENT_ID : viewIds[i - 1];
            int bottomId = i == viewIds.length - 1 ? PARENT_ID : viewIds[i + 1];
            addToVerticalChain(viewId, topId, bottomId, margin.getTop(), margin.getBottom());
            addToHorizontalChain(viewId, PARENT_ID, PARENT_ID, margin.getStart(), margin.getEnd());
        }
        return this;
    }

    @NonNull
    public ConstraintSetBuilder chainHorizontally(@NonNull int[] viewIds, @NonNull Margin[] margins) {
        for (int i = 0; i < viewIds.length; i++) {
            int viewId = viewIds[i];
            Margin margin = margins[i];

            int leftId = i == 0 ? PARENT_ID : viewIds[i - 1];
            int rightId = i == viewIds.length - 1 ? PARENT_ID : viewIds[i + 1];
            addToHorizontalChain(viewId, leftId, rightId, margin.getStart(), margin.getEnd());
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
        constraints.connect(viewId, TOP, topId, (topId == PARENT_ID) ? TOP : BOTTOM, marginTop);
        constraints.connect(viewId, BOTTOM, bottomId, (bottomId == PARENT_ID) ? BOTTOM : TOP, marginBottom);
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
        constraints.connect(viewId, LEFT, leftId, (leftId == PARENT_ID) ? LEFT : RIGHT, leftMargin);
        constraints.connect(viewId, RIGHT, rightId, (rightId == PARENT_ID) ? RIGHT : LEFT, rightMargin);
        if (leftId != PARENT_ID) {
            constraints.connect(leftId, RIGHT, viewId, LEFT, 0);
        }
        if (rightId != PARENT_ID) {
            constraints.connect(rightId, LEFT, viewId, RIGHT, 0);
        }

        return this;
    }

    @NonNull
    public ConstraintSetBuilder setVerticalChainStyle(int viewId, int chainStyle) {
        constraints.setVerticalChainStyle(viewId, chainStyle);
        return this;
    }

    @NonNull
    public ConstraintSetBuilder setVerticalBias(int viewId, float bias) {
        constraints.setVerticalBias(viewId, bias);
        return this;
    }

    @NonNull
    public ConstraintSetBuilder setHorizontalChainStyle(int viewId, int chainStyle) {
        constraints.setHorizontalChainStyle(viewId, chainStyle);
        return this;
    }

    @NonNull
    public ConstraintSetBuilder setHorizontalBias(int viewId, float bias) {
        constraints.setHorizontalBias(viewId, bias);
        return this;
    }

    @NonNull
    public ConstraintSetBuilder size(@Nullable Size size, @IdRes int viewId) {
        return size(size, viewId, ConstraintSet.WRAP_CONTENT);
    }

    @NonNull
    public ConstraintSetBuilder size(@Nullable Size size, @IdRes int viewId, int autoValue) {
        if (size != null) {
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

    // TODO: not sure if this is actually needed / in the spec?
    @NonNull
    public ConstraintSetBuilder maxSize(@Nullable Size size, @IdRes int viewId) {
        if (size == null) {
            return this;
        }

        Log.v(getClass().getSimpleName(), "Constraining max size: " + size);

        Size.Dimension width = size.getWidth();
        switch (width.getType()) {
            case AUTO:
                // TODO: make this work? throw an error?
                // constraints.constrainMaxWidth(viewId, ConstraintSet.WRAP_CONTENT);
                break;
            case PERCENT:
                // TODO: make this work? throw an error?
                // constraints.constrainPercentWidth(viewId, width.getFloat());
                break;
            case ABSOLUTE:
                constraints.constrainMaxWidth(viewId, (int) dpToPx(context, width.getInt()));
                break;
        }

        Size.Dimension height = size.getHeight();
        switch (height.getType()) {
            case AUTO:
                // TODO: make this work? throw an error?
                // constraints.constrainMaxHeight(viewId, ConstraintSet.WRAP_CONTENT);
                break;
            case PERCENT:
                // TODO: make this work? throw an error?
                // constraints.constrainPercentHeight(viewId, height.getFloat());
                break;
            case ABSOLUTE:
                constraints.constrainMaxHeight(viewId, (int) dpToPx(context, height.getInt()));
                break;
        }

        return this;
    }

    @NonNull
    public ConstraintSetBuilder weight(@Nullable Float weight, @Nullable Direction direction, @IdRes int viewId) {
        if (direction != null && weight != null) {
            if (direction == Direction.VERTICAL) {
                constraints.setVerticalWeight(viewId, weight);
            } else {
                constraints.setHorizontalWeight(viewId, weight);
            }
        }
        return this;
    }

    @NonNull
    public ConstraintSetBuilder position(@Nullable Position position, @IdRes int viewId) {
        if (position != null) {
            connectAllSidesToParent(viewId);

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
    public ConstraintSetBuilder connectAllSidesToParent(int viewId) {
        constraints.connect(viewId, TOP, ConstraintSet.PARENT_ID, TOP);
        constraints.connect(viewId, BOTTOM, ConstraintSet.PARENT_ID, BOTTOM);
        constraints.connect(viewId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
        constraints.connect(viewId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);

        return this;
    }

    @NonNull
    public ConstraintSet build() {
        return constraints;
    }
}
