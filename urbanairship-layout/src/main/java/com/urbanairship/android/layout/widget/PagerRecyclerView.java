/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import android.content.Context;
import android.view.View;

import com.urbanairship.android.layout.environment.ViewEnvironment;
import com.urbanairship.android.layout.model.PagerModel;
import com.urbanairship.android.layout.util.ViewExtensionsKt;
import com.urbanairship.android.layout.view.PagerView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.OrientationHelper;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PagerRecyclerView extends RecyclerView {
    private final PagerModel model;
    private final ViewEnvironment viewEnvironment;
    private PagerAdapter adapter;
    private LinearLayoutManager layoutManager;
    private PagerSnapHelper snapHelper;

    private boolean isInternalScroll = false;

    @Nullable
    private PagerView.OnScrollListener listener = null;

    public PagerRecyclerView(@NonNull Context context, @NonNull PagerModel model, @NonNull ViewEnvironment viewEnvironment) {
        super(context);
        this.model = model;
        this.viewEnvironment = viewEnvironment;

        setId(model.getRecyclerViewId());

        configure();
    }

    public void configure() {
        setHorizontalScrollBarEnabled(false);

        snapHelper = new SnapHelper();
        snapHelper.attachToRecyclerView(this);

        if (model.getPages().size() <= 1 || model.isSwipeDisabled()) {
            layoutManager = new SwipeDisabledLinearLayoutManager(
                    getContext(),
                    LinearLayoutManager.HORIZONTAL,
                    ViewExtensionsKt.isLayoutRtl(this)
            );
        } else {
            layoutManager = new ThomasLinearLayoutManager(
                    getContext(),
                    LinearLayoutManager.HORIZONTAL,
                    ViewExtensionsKt.isLayoutRtl(this)
            );
        }

        setLayoutManager(layoutManager);

        addOnScrollListener(recyclerScrollListener);

        adapter = new PagerAdapter(model, viewEnvironment);
        adapter.setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        adapter.setItems(model.getPages());
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

    public void scrollTo(int position) {
        // Set the internal scroll flag to prevent page swipe events from being reported.
        // The flag will be cleared when the smooth scroll animation is completed.
        isInternalScroll = true;
        smoothScrollToPosition(position);
    }

    public void setPagerScrollListener(@Nullable PagerView.OnScrollListener listener) {
        this.listener = listener;
    }

    private final RecyclerView.OnScrollListener recyclerScrollListener = new RecyclerView.OnScrollListener() {
        private int previousPosition = 0;

        @Override
        public void onScrollStateChanged(@NonNull RecyclerView v, int state) {
            int position = getDisplayedItemPosition();
            if (position != NO_POSITION && position != previousPosition) {
                int step = position > previousPosition ? 1 : -1;
                int distance = Math.abs(position - previousPosition);
                for (int i = 0; i < distance; i++) {
                    int calculated = previousPosition + (step * (i + 1));
                    if (listener != null) {
                        listener.onScrollTo(calculated, isInternalScroll);
                    }
                }
            }
            previousPosition = position;

            // If the scroll state is idle, scrolling has stopped and we can reset the internal scroll flag.
            if (state == RecyclerView.SCROLL_STATE_IDLE) {
                isInternalScroll = false;
            }
        }
    };

    private static class ThomasLinearLayoutManager extends LinearLayoutManager {
        public ThomasLinearLayoutManager(Context context, int orientation, boolean reverseLayout) {
            super(context, orientation, reverseLayout);
            // Disable prefetch so that we won't get display events from items that aren't yet visible.
            // TODO: revisit this now that we have a better way for models to determine if they
            //   are displayed in the current pager page.
            setItemPrefetchEnabled(false);
        }

        @Override
        public LayoutParams generateDefaultLayoutParams() {
            return new RecyclerView.LayoutParams(MATCH_PARENT, MATCH_PARENT);
        }
    }

    /**
     * Custom {@code LinearLayoutManager} that disables scrolling via touch, but can still be scrolled programmatically.
     */
    private static class SwipeDisabledLinearLayoutManager extends ThomasLinearLayoutManager {
        public SwipeDisabledLinearLayoutManager(Context context, int orientation, boolean reverseLayout) {
            super(context, orientation, reverseLayout);
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
