/* Copyright Airship and Contributors */

package com.urbanairship.iam.banner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.view.ViewCompat;
import androidx.customview.widget.ViewDragHelper;

/**
 * The BannerDismissLayout allows dismissing a banner with a vertical swipe gesture.
 */
public class BannerDismissLayout extends FrameLayout {

    /**
     * The percent of a view's width it must be dragged before it is considered dismissible when the velocity
     * is less then the {@link #getMinFlingVelocity()}.
     */
    private static final float IDLE_MIN_DRAG_PERCENT = .4f;

    /**
     * The percent of a view's width it must be dragged before it is considered dismissible when the velocity
     * is greater then the {@link #getMinFlingVelocity()}.
     */
    private static final float FLING_MIN_DRAG_PERCENT = .1f;

    private static final int DEFAULT_OVER_DRAG_DP = 24;

    private float overDragAmount;

    @BannerDisplayContent.Placement
    private String placement = BannerDisplayContent.PLACEMENT_BOTTOM;

    /**
     * Interface to listen for dismissing the message view.
     */
    public interface Listener {

        /**
         * Called when a child view was dismissed from a swipe. It is up to the listener to remove
         * or hide the view from the parent.
         */
        void onDismissed(@NonNull View view);

        /**
         * Called when a child view's drag state changes.
         *
         * @param state The drag state will be either {@code ViewDragHelper.STATE_IDLE},
         * {@code ViewDragHelper.STATE_DRAGGING}, or {@code ViewDragHelper.STATE_SETTLING}.
         */
        void onDragStateChanged(@NonNull View view, int state);

    }

    private ViewDragHelper dragHelper;
    private float minFlingVelocity;
    private Listener listener;

    /**
     * BannerDismissLayout Constructor
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     */
    public BannerDismissLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * BannerDismissLayout Constructor
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to the parent.
     * @param defStyle The default style resource ID.
     */
    public BannerDismissLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    /**
     * BannerDismissLayout Constructor
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to the parent.
     * @param defStyle The default style resource ID.
     * @param defResStyle A resource identifier of a style resource that supplies default values for
     * the view, used only if defStyle is 0 or cannot be found in the theme. Can be 0 to not
     * look for defaults.
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public BannerDismissLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle, int defResStyle) {
        super(context, attrs, defStyle, defResStyle);
        init(context);
    }

    private void init(@NonNull Context context) {
        if (isInEditMode()) {
            return;
        }

        dragHelper = ViewDragHelper.create(this, new ViewDragCallback());

        ViewConfiguration vc = ViewConfiguration.get(context);
        minFlingVelocity = vc.getScaledMinimumFlingVelocity();
        overDragAmount = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DEFAULT_OVER_DRAG_DP, context.getResources().getDisplayMetrics());
    }

    /**
     * Sets the minimum velocity needed to initiate a fling, as measured in pixels per second.
     * <p>
     * The default value is loaded from the {@code ViewConfiguration.getScaledMinimumFlingVelocity()}.
     *
     * @param minFlingVelocity The minimum fling velocity in pixels per second.
     */
    public void setMinFlingVelocity(float minFlingVelocity) {
        this.minFlingVelocity = minFlingVelocity;
    }

    /**
     * Gets the minimum velocity needed to initiate a fling, as measured in pixels per second.
     * <p>
     * The default value is loaded from the {@code ViewConfiguration.getScaledMinimumFlingVelocity()}.
     *
     * @return The minimum fling velocity in pixels per second.
     */
    public float getMinFlingVelocity() {
        return this.minFlingVelocity;
    }

    /**
     * Sets the dismiss listener.
     *
     * @param listener The dismiss listener.
     */
    public void setListener(@Nullable Listener listener) {
        synchronized (this) {
            this.listener = listener;
        }
    }

    /**
     * Gets the view's y translation as a fraction of its height.
     * <p>
     * Used to animate a view into the screen with a ObjectAnimator.
     *
     * @return The view's y translation as a fraction of its height.
     */
    @Keep
    public float getYFraction() {
        final int height = getHeight();
        if (height == 0) {
            return 0;
        }

        return getTranslationY() / height;
    }

    /**
     * Sets the view's y translation as a fraction of its height.
     * <p>
     * Used to animate a view into the screen with a ObjectAnimator.
     */
    @Keep
    public void setYFraction(final float yFraction) {

        // The view's height is only populated after the view has been laid out. We can workaround
        // this by adding a OnPreDrawListener and set the yFraction.
        if (getVisibility() == View.VISIBLE && getHeight() == 0) {
            final ViewTreeObserver.OnPreDrawListener preDrawListener = new ViewTreeObserver.OnPreDrawListener() {

                @Override
                public boolean onPreDraw() {
                    setYFraction(yFraction);
                    getViewTreeObserver().removeOnPreDrawListener(this);
                    return true;
                }
            };

            getViewTreeObserver().addOnPreDrawListener(preDrawListener);
        } else {
            setTranslationY(yFraction * getHeight());
        }
    }

    /**
     * Gets the view's x translation as a fraction of its width.
     * <p>
     * Used to animate a view into the screen with a ObjectAnimator.
     *
     * @return The view's x translation as a fraction of its width.
     */
    @Keep
    public float getXFraction() {
        final int width = getWidth();
        if (width == 0) {
            return 0;
        }

        return getTranslationX() / width;
    }

    /**
     * Sets the view's x translation as a fraction of its width.
     * <p>
     * Used to animate a view into the screen with a ObjectAnimator.
     */
    @Keep
    public void setXFraction(final float xFraction) {

        // The view's width is only populated after the view has been laid out. We can workaround
        // this by adding a OnPreDrawListener and set the xFraction.
        if (getVisibility() == View.VISIBLE && getHeight() == 0) {
            final ViewTreeObserver.OnPreDrawListener preDrawListener = new ViewTreeObserver.OnPreDrawListener() {

                @Override
                public boolean onPreDraw() {
                    setXFraction(xFraction);
                    getViewTreeObserver().removeOnPreDrawListener(this);
                    return true;
                }
            };
            getViewTreeObserver().addOnPreDrawListener(preDrawListener);

        } else {
            setTranslationX(xFraction * getWidth());
        }
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (dragHelper != null && dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull MotionEvent event) {
        if (dragHelper.shouldInterceptTouchEvent(event) || super.onInterceptTouchEvent(event)) {
            return true;
        }

        if (dragHelper.getViewDragState() == ViewDragHelper.STATE_IDLE && event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            /*
             * Check if the touch exceeded the touch slop. If so, check if we can drag and interrupt
             * any children. This breaks any children that are horizontally scrollable, unless they
             * prevent the parent view from intercepting the event.
             */
            if (dragHelper.checkTouchSlop(ViewDragHelper.DIRECTION_VERTICAL)) {
                View child = dragHelper.findTopChildUnder((int) event.getX(), (int) event.getY());
                if (child != null && !child.canScrollVertically(dragHelper.getTouchSlop())) {
                    dragHelper.captureChildView(child, event.getPointerId(0));
                    return dragHelper.getViewDragState() == ViewDragHelper.STATE_DRAGGING;
                }
            }
        }

        return false;
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        dragHelper.processTouchEvent(event);

        if (dragHelper.getCapturedView() == null) {
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (dragHelper.checkTouchSlop(ViewDragHelper.DIRECTION_VERTICAL)) {
                    View child = dragHelper.findTopChildUnder((int) event.getX(), (int) event.getY());
                    if (child != null && !child.canScrollVertically(dragHelper.getTouchSlop())) {
                        dragHelper.captureChildView(child, event.getPointerId(0));
                    }
                }
            }
        }

        return dragHelper.getCapturedView() != null;
    }

    /**
     * Sets the banner placement.
     *
     * @param placement The placement.
     */
    public void setPlacement(@BannerDisplayContent.Placement @NonNull String placement) {
        this.placement = placement;
    }

    /**
     * Helper class to handle the the callbacks from the ViewDragHelper.
     */
    private class ViewDragCallback extends ViewDragHelper.Callback {

        private int startTop;
        private int startLeft;
        private float dragPercent = 0;
        private View capturedView;
        private boolean isDismissed = false;

        @Override
        public boolean tryCaptureView(@NonNull View view, int i) {
            return capturedView == null;
        }

        @Override
        public int clampViewPositionHorizontal(@NonNull View child, int left, int dx) {
            return child.getLeft();
        }

        @Override
        public int clampViewPositionVertical(@NonNull View child, int top, int dy) {
            switch (placement) {
                case BannerDisplayContent.PLACEMENT_TOP:
                    return Math.round(Math.min(top, startTop + overDragAmount));

                case BannerDisplayContent.PLACEMENT_BOTTOM:
                default:
                    return Math.round(Math.max(top, startTop - overDragAmount));
            }
        }

        @Override
        public void onViewCaptured(@NonNull View view, int activePointerId) {
            capturedView = view;
            startTop = view.getTop();
            startLeft = view.getLeft();
            dragPercent = 0;
            isDismissed = false;
        }

        @Override
        @SuppressLint("NewApi")
        public void onViewPositionChanged(@NonNull View view, int left, int top, int dx, int dy) {
            int range = getHeight();
            int moved = Math.abs(top - startTop);

            if (range > 0) {
                dragPercent = moved / (float) range;
            }

            invalidate();
        }

        @Override
        public void onViewDragStateChanged(int state) {
            if (capturedView == null) {
                return;
            }

            synchronized (this) {
                if (listener != null) {
                    listener.onDragStateChanged(capturedView, state);
                }

                if (state == ViewDragHelper.STATE_IDLE) {
                    if (isDismissed) {
                        if (listener != null) {
                            listener.onDismissed(capturedView);
                        }

                        removeView(capturedView);
                    }

                    capturedView = null;
                }
            }
        }

        @Override
        public void onViewReleased(@NonNull final View view, float xv, float yv) {

            float absYv = Math.abs(yv);
            if (BannerDisplayContent.PLACEMENT_TOP.equals(placement) ? startTop >= view.getTop() : startTop <= view.getTop()) {
                isDismissed = dragPercent >= IDLE_MIN_DRAG_PERCENT ||
                        absYv > minFlingVelocity ||
                        dragPercent > FLING_MIN_DRAG_PERCENT;
            }

            if (isDismissed) {
                int top = BannerDisplayContent.PLACEMENT_TOP.equals(placement) ? -view.getHeight() : getHeight() + view.getHeight();
                dragHelper.settleCapturedViewAt(startLeft, top);
            } else {
                dragHelper.settleCapturedViewAt(startLeft, startTop);
            }

            invalidate();
        }

    }

}
