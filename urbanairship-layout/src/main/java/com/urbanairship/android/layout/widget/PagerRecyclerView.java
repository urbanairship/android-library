/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import android.content.Context;
import android.view.View;

import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.PagerModel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.util.Consumer;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.OrientationHelper;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PagerRecyclerView extends RecyclerView {
    private PagerModel model;
    private Environment environment;
    private PagerAdapter adapter;
    private LinearLayoutManager layoutManager;
    private PagerSnapHelper snapHelper;

    private boolean isInternalScroll = false;

    public PagerRecyclerView(@NonNull Context context) {
        super(context);
        init();
    }

    private void init() {
        snapHelper = new SnapHelper();
        snapHelper.attachToRecyclerView(this);

        setHorizontalScrollBarEnabled(false);
    }

    public void configure(@NonNull PagerModel model, @NonNull Environment environment) {
        this.model = model;
        this.environment = environment;

        setId(model.getRecyclerViewId());

        final Consumer<Boolean> onLayoutComplete = (isInitialLayout) -> {
            if (!isInitialLayout) { return; }
            model.onConfigured(getDisplayedItemPosition(), environment.displayTimer().getTime());
        };

        if (model.getChildren().size() <= 1 || model.isSwipeDisabled()) {
            layoutManager = new SwipeDisabledLinearLayoutManager(
                getContext(), LinearLayoutManager.HORIZONTAL, onLayoutComplete);
        } else {
            layoutManager = new ThomasLinearLayoutManager(
                getContext(), HORIZONTAL, onLayoutComplete);
        }

        // Disable prefetch so we won't get display events from items that aren't yet visible.
        layoutManager.setItemPrefetchEnabled(false);
        setLayoutManager(layoutManager);

        addOnScrollListener(recyclerScrollListener);

        adapter = new PagerAdapter(model, environment);
        adapter.setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        adapter.setItems(model.getChildren());
        setAdapter(adapter);

        // Pass along any calls to apply insets to the view.
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            for (int i = 0, count = getChildCount(); i < count; i++) {
                ViewCompat.dispatchApplyWindowInsets(getChildAt(i), insets);
            }
            return insets;
        });
    }

    public int getDisplayedItemPosition() {
        View snapView = snapHelper.findSnapView(layoutManager);
        return snapView != null ? getChildAdapterPosition(snapView) : 0;
    }

    public int getAdapterItemCount() {
        return adapter.getItemCount();
    }

    public void scrollTo(int position) {
        isInternalScroll = true;
        smoothScrollToPosition(position);
    }

    private final RecyclerView.OnScrollListener recyclerScrollListener = new RecyclerView.OnScrollListener() {
        private int previousPosition = 0;

        @Override
        public void onScrollStateChanged(@NonNull RecyclerView v, int state) {
            // Ignore callbacks if we're still in the process of scrolling, since we only
            // want to update the model (which bubbles the scroll and page view events) once
            // the scroll has finished.
            if (state != SCROLL_STATE_IDLE) { return; }

            int position = getDisplayedItemPosition();
            if (position != NO_POSITION && position != previousPosition) {
                int step = position > previousPosition ? 1 : -1;
                int distance = Math.abs(position - previousPosition);
                for (int i = 0; i < distance; i++) {
                    int calculated = previousPosition + (step * (i + 1));
                    model.onScrollTo(calculated, isInternalScroll, environment.displayTimer().getTime());
                }
            }
            previousPosition = position;
            isInternalScroll = false;
        }
    };

    private static class ThomasLinearLayoutManager extends LinearLayoutManager {
        private final Consumer<Boolean> layoutCompletedCallback;
        private boolean isInitialLayout = true;

        public ThomasLinearLayoutManager(
            Context context,
            int orientation,
            Consumer<Boolean> completedCallback
        ) {
            super(context, orientation, false);
            this.layoutCompletedCallback = completedCallback;
        }

        @Override
        public void onLayoutCompleted(State state) {
            super.onLayoutCompleted(state);
            layoutCompletedCallback.accept(isInitialLayout);
            isInitialLayout = false;
        }
    }

    /**
     * Custom {@code LinearLayoutManager} that disables scrolling via touch, but can still be scrolled programmatically.
     */
    private static class SwipeDisabledLinearLayoutManager extends ThomasLinearLayoutManager {
        public SwipeDisabledLinearLayoutManager(
            Context context,
            int orientation,
            Consumer<Boolean> completedCallback
        ) {
            super(context, orientation, completedCallback);
        }

        @Override
        public boolean canScrollHorizontally() {
            return false;
        }

        @Override
        public void smoothScrollToPosition(RecyclerView recyclerView, State state, int position) {
            LinearSmoothScroller smoothScroller = new SwipeDisabledSmoothScroller(recyclerView.getContext());
            smoothScroller.setTargetPosition(position);
            startSmoothScroll(smoothScroller);
        }

        /** Custom {@code LinearSmoothScroller} with overrides to remain functional when touch swipes are disabled. */
        private static class SwipeDisabledSmoothScroller extends LinearSmoothScroller {
            public SwipeDisabledSmoothScroller(Context context) {
                super(context);
            }

            @Override
            public int calculateDxToMakeVisible(View view, int snapPreference) {
                final RecyclerView.LayoutManager layoutManager = getLayoutManager();
                if (layoutManager == null) {
                    return 0;
                }
                RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
                final int left = layoutManager.getDecoratedLeft(view) - params.leftMargin;
                final int right = layoutManager.getDecoratedRight(view) + params.rightMargin;
                final int start = layoutManager.getPaddingLeft();
                final int end = layoutManager.getWidth() - layoutManager.getPaddingRight();
                return calculateDtToFit(left, right, start, end, snapPreference);
            }
        }
    }

    /** Custom {@code PagerSnapHelper} with overrides to remain functional when touch swipes are disabled. */
    private static class SnapHelper extends PagerSnapHelper {
        @Nullable
        private OrientationHelper verticalHelper;
        @Nullable
        private OrientationHelper horizontalHelper;

        @Nullable
        @Override
        public View findSnapView(LayoutManager layoutManager) {
            if (layoutManager.getLayoutDirection() == VERTICAL) {
                return findCenterView(layoutManager, getVerticalHelper(layoutManager));
            } else {
                return findCenterView(layoutManager, getHorizontalHelper(layoutManager));
            }
        }

        @Nullable
        private View findCenterView(RecyclerView.LayoutManager layoutManager, OrientationHelper helper) {
            int childCount = layoutManager.getChildCount();
            if (childCount == 0) {
                return null;
            }

            View closestChild = null;
            final int center = helper.getStartAfterPadding() + helper.getTotalSpace() / 2;
            int absClosest = Integer.MAX_VALUE;

            for (int i = 0; i < childCount; i++) {
                final View child = layoutManager.getChildAt(i);
                int childCenter = helper.getDecoratedStart(child) + (helper.getDecoratedMeasurement(child) / 2);
                int absDistance = Math.abs(childCenter - center);

                /* if child center is closer than previous closest, set it as closest  */
                if (absDistance < absClosest) {
                    absClosest = absDistance;
                    closestChild = child;
                }
            }
            return closestChild;
        }

        @NonNull
        private OrientationHelper getVerticalHelper(@NonNull RecyclerView.LayoutManager layoutManager) {
            if (verticalHelper == null || verticalHelper.getLayoutManager() != layoutManager) {
                verticalHelper = OrientationHelper.createVerticalHelper(layoutManager);
            }
            return verticalHelper;
        }

        @NonNull
        private OrientationHelper getHorizontalHelper(
            @NonNull RecyclerView.LayoutManager layoutManager) {
            if (horizontalHelper == null || horizontalHelper.getLayoutManager() != layoutManager) {
                horizontalHelper = OrientationHelper.createHorizontalHelper(layoutManager);
            }
            return horizontalHelper;
        }
    }
}
