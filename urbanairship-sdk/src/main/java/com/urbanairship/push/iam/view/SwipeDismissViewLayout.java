/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.iam.view;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import com.urbanairship.Logger;

/**
 * The SwipeDismissViewLayout allows its children to be dismissed from a horizontal swipe or drag. The
 * layout will notify the listener when a child view is being dismissed or dragged. A dismissed view
 * will be settled at the edge of the SwipeDismissViewLayout view. After the view is settled, it
 * will call the listener's {@link SwipeDismissViewLayout.Listener#onDismissed}
 * and be removed from the SwipeDismissViewLayout.
 */
public class SwipeDismissViewLayout extends FrameLayout {

    /**
     * The percent of a view's width it must be dragged before its considered dismissible when the velocity
     * is less then the {@link #getMinFlingVelocity()}.
     */
    private static final float IDLE_MIN_DRAG_PERCENT = .75f;

    /**
     * The percent of a view's width it must be dragged before its considered dismissible when the velocity
     * is greater then the {@link #getMinFlingVelocity()}.
     */
    private static final float FLING_MIN_DRAG_PERCENT = .1f;

    /**
     * Interface to listen for dismissing the message view.
     */
    public interface Listener {

        /**
         * Called when a child view was dismissed from a swipe. It is up to the listener to remove
         * or hide the view from the parent.
         */
        void onDismissed(View view);

        /**
         * Called when a child view's drag state changes.
         *
         * @param state The drag state will be either {@code ViewDragHelper.STATE_IDLE},
         * {@code ViewDragHelper.STATE_DRAGGING}, or {@code ViewDragHelper.STATE_SETTLING}.
         */
        void onDragStateChanged(View view, int state);
    }

    private ViewDragHelper dragHelper;
    private float minFlingVelocity;
    private Listener listener;

    /**
     * SwipeDismissViewLayout Constructor
     *
     * @param context A Context object used to access application assets.
     */
    public SwipeDismissViewLayout(@NonNull Context context) {
        super(context);
        init(context);
    }

    /**
     * SwipeDismissViewLayout Constructor
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     */
    public SwipeDismissViewLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    /**
     * SwipeDismissViewLayout Constructor
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     */
    public SwipeDismissViewLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    /**
     * SwipeDismissViewLayout Constructor
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     * @param defResStyle A resource identifier of a style resource that supplies default values for
     * the view, used only if defStyle is 0 or can not be found in the theme. Can be 0 to not
     * look for defaults.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SwipeDismissViewLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle, int defResStyle) {
        super(context, attrs, defStyle, defResStyle);
        init(context);
    }

    /**
     * Performs any initialization steps.
     *
     * @param context The application context.
     */
    private void init(@NonNull Context context) {
        if (isInEditMode()) {
            return;
        }

        ViewConfiguration vc = ViewConfiguration.get(context);
        minFlingVelocity = vc.getScaledMinimumFlingVelocity();

        dragHelper = ViewDragHelper.create(this, new ViewDragCallback());
    }

    /**
     * Sets the minimum velocity needed to initiate a fling, as measured in pixels per second.
     * <p/>
     * The default value is loaded from the {@code ViewConfiguration.getScaledMinimumFlingVelocity()}.
     *
     * @param minFlingVelocity The minimum fling velocity in pixels per second.
     */
    public void setMinFlingVelocity(float minFlingVelocity) {
        this.minFlingVelocity = minFlingVelocity;
    }

    /**
     * Gets the minimum velocity needed to initiate a fling, as measured in pixels per second.
     * <p/>
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
    public void setListener(Listener listener) {
        synchronized (this) {
            this.listener = listener;
        }
    }


    /**
     * Gets the view's y translation as a fraction of its height.
     * <p/>
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
     * <p/>
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
     * <p/>
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
     * <p/>
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
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (dragHelper.shouldInterceptTouchEvent(event) || super.onInterceptTouchEvent(event)) {
            Logger.error("onInterceptTouchEvent " + event);
            return true;
        } else if (dragHelper.getViewDragState() == ViewDragHelper.STATE_IDLE && MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_MOVE) {

            /*
             * Check if the touch exceeded the touch slop. If so, check if we can drag and interrupt
             * any children. This breaks any children that are horizontally scrollable, unless they
             * prevent the parent view from intercepting the event.
             */
            if (dragHelper.checkTouchSlop(ViewDragHelper.DIRECTION_HORIZONTAL)) {
                View child = dragHelper.findTopChildUnder((int) event.getX(), (int) event.getY());
                if (child != null && !ViewCompat.canScrollHorizontally(child, dragHelper.getTouchSlop())) {
                    dragHelper.captureChildView(child, event.getPointerId(0));
                    return dragHelper.getViewDragState() == ViewDragHelper.STATE_DRAGGING;
                }
            }
        }

        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        dragHelper.processTouchEvent(event);
        return dragHelper.getCapturedView() != null;
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
        public boolean tryCaptureView(View view, int i) {
            return capturedView == null;
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            return left;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            return child.getTop();
        }

        @Override
        public void onViewCaptured(View view, int activePointerId) {
            capturedView = view;
            startTop = view.getTop();
            startLeft = view.getLeft();
            dragPercent = 0;
            isDismissed = false;
        }

        @Override
        @SuppressLint("NewApi")
        public void onViewPositionChanged(View view, int left, int top, int dx, int dy) {
            int range = getWidth() / 2;
            int moved = Math.abs(left - startLeft);

            if (range > 0) {
                dragPercent = moved / (float) range;
            }

            view.setAlpha(1 - dragPercent);
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
        public void onViewReleased(final View view, float xv, float yv) {
            boolean isSwipeLeft;
            float absXv = Math.abs(xv);

            if (absXv > minFlingVelocity) {
                isSwipeLeft = xv > 0;
            } else {
                isSwipeLeft =  startLeft < view.getLeft();
            }

            isDismissed = (dragPercent >= IDLE_MIN_DRAG_PERCENT) ||
                    (Math.abs(xv) > minFlingVelocity && dragPercent > FLING_MIN_DRAG_PERCENT);

            if (isDismissed) {
                int offSet = isSwipeLeft ? -view.getWidth() : view.getWidth();
                dragHelper.settleCapturedViewAt(startLeft - offSet, startTop);
            } else {
                dragHelper.settleCapturedViewAt(startLeft, startTop);
            }

            invalidate();
        }
    }
}
