package com.urbanairship.android.layout.ui;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.AnimatorRes;
import androidx.annotation.CallSuper;
import androidx.annotation.Keep;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.view.ViewCompat;
import androidx.customview.widget.ViewDragHelper;

import com.urbanairship.android.layout.BannerPresentation;
import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.environment.ViewEnvironment;
import com.urbanairship.android.layout.model.BaseModel;
import com.urbanairship.android.layout.property.BannerPlacement;
import com.urbanairship.android.layout.property.ConstrainedSize;
import com.urbanairship.android.layout.property.Margin;
import com.urbanairship.android.layout.property.Position;
import com.urbanairship.android.layout.property.VerticalPosition;
import com.urbanairship.android.layout.util.ConstraintSetBuilder;
import com.urbanairship.android.layout.util.ResourceUtils;
import com.urbanairship.android.layout.util.Timer;
import com.urbanairship.android.layout.widget.ConstrainedFrameLayout;


public class ThomasBannerView extends ConstraintLayout {

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

    private VerticalPosition placement = VerticalPosition.BOTTOM;

    private ViewDragHelper dragHelper;
    private float minFlingVelocity;

    private BaseModel model;
    private BannerPresentation presentation;
    private ViewEnvironment environment;
    private ConstrainedFrameLayout bannerFrame;
    private View containerView;

    @NonNull
    private final Timer timer;

    @AnimatorRes
    private int animationIn;

    @AnimatorRes
    private int animationOut;

    private boolean isDismissed = false;
    private boolean isResumed = false;

    @Nullable
    private Listener listener;

    /**
     * Banner view listener.
     */
    public interface Listener {

        /**
         * Called when the banner times out.
         *
         * @param view The banner view.
         */
        @MainThread
        void onTimedOut(@NonNull ThomasBannerView view);

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

    public ThomasBannerView(@NonNull Context context,
                            @NonNull BaseModel model,
                            @NonNull BannerPresentation presentation,
                            @NonNull ViewEnvironment environment) {
        super(context);

        this.timer = new Timer(presentation.getDurationMs()) {
            @Override
            protected void onFinish() {
                dismiss(true);
                Listener listener = ThomasBannerView.this.listener;
                if (listener != null) {
                    listener.onTimedOut(ThomasBannerView.this);
                }
            }
        };

        this.model = model;
        this.presentation = presentation;
        this.environment = environment;

        initDragHelper(context);

        setId(model.getViewId());
        configureBanner();
        onResume();
    }

    private void initDragHelper(@NonNull Context context) {
        if (isInEditMode()) {
            return;
        }

        dragHelper = ViewDragHelper.create(this, new ViewDragCallback());

        ViewConfiguration vc = ViewConfiguration.get(context);
        minFlingVelocity = vc.getScaledMinimumFlingVelocity();
        overDragAmount = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DEFAULT_OVER_DRAG_DP, context.getResources().getDisplayMetrics());
    }

    public void configureBanner() {
        BannerPlacement placement = presentation.getDefaultPlacement();

        ConstrainedSize size = placement.getSize();
        Position position = placement.getPosition();
        Margin margin = placement.getMargin();
        makeFrame(size);

        containerView = Thomas.view(getContext(), model, environment);
        bannerFrame.addView(containerView);

        addView(bannerFrame);
        if (animationIn != 0) {
            Animator animator = AnimatorInflater.loadAnimator(getContext(), animationIn);
            animator.setTarget(bannerFrame);
            animator.start();
        }

        int viewId = bannerFrame.getId();
        ConstraintSet constraints = ConstraintSetBuilder.newBuilder(getContext())
                .position(position, viewId)
                .size(size, viewId)
                .margin(margin, viewId)
                .build();

        constraints.applyTo(this);

        if (environment.isIgnoringSafeAreas()) {
            ViewCompat.setOnApplyWindowInsetsListener(bannerFrame, (v, insets) ->
                    ViewCompat.dispatchApplyWindowInsets(containerView, insets)
            );
        }
    }

    public void makeFrame(ConstrainedSize size) {
        bannerFrame = new ConstrainedFrameLayout(getContext(), size);
        bannerFrame.setId(generateViewId());
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_CONSTRAINT, LayoutParams.MATCH_CONSTRAINT);
        bannerFrame.setLayoutParams(params);
        bannerFrame.setElevation(ResourceUtils.dpToPx(getContext(), 16));
    }

    /**
     * Resumes the banner's timer.
     */
    @MainThread
    @CallSuper
    public void onResume() {
        isResumed = true;
        if (!isDismissed) {
            getTimer().start();
        }
    }

    /**
     * Pauses the banner's timer.
     */
    @MainThread
    @CallSuper
    public void onPause() {
        isResumed = false;
        getTimer().stop();
    }

    /**
     * Used to dismiss the message.
     *
     * @param animate {@code true} to animate the view out, otherwise {@code false}.
     */
    @MainThread
    public void dismiss(boolean animate) {
        isDismissed = true;
        getTimer().stop();

        if (animate && bannerFrame != null && animationOut != 0) {
            clearAnimation();
            Animator animator = AnimatorInflater.loadAnimator(getContext(), animationOut);
            animator.setTarget(bannerFrame);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    removeSelf();
                }
            });

            animator.start();
        } else {
            removeSelf();
        }
    }

    /**
     * Helper method to remove the view from the parent.
     */
    @MainThread
    private void removeSelf() {
        if (this.getParent() instanceof ViewGroup) {
            final ViewGroup parent = (ViewGroup) this.getParent();
            parent.removeView(this);
            bannerFrame = null;
        }
    }

    /**
     * Sets the animation.
     *
     * @param in The animation in.
     * @param out The animation out.
     */
    public void setAnimations(@AnimatorRes int in, @AnimatorRes int out) {
        this.animationIn = in;
        this.animationOut = out;
    }

    /**
     * In-app message display timer.
     *
     * @return The in-app message display timer.
     */
    @NonNull
    protected Timer getTimer() {
        return timer;
    }

    /**
     * Sets the banner listener.
     *
     * @param listener The banner listener.
     */
    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public boolean isResumed() {
        return isResumed;
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
    public void setPlacement(@NonNull VerticalPosition placement) {
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
                case TOP:
                    return Math.round(Math.min(top, startTop + overDragAmount));

                case BOTTOM:
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
            if (VerticalPosition.TOP.equals(placement) ? startTop >= view.getTop() : startTop <= view.getTop()) {
                isDismissed = dragPercent >= IDLE_MIN_DRAG_PERCENT ||
                        absYv > minFlingVelocity ||
                        dragPercent > FLING_MIN_DRAG_PERCENT;
            }

            if (isDismissed) {
                int top = VerticalPosition.TOP.equals(placement) ? -view.getHeight() : getHeight() + view.getHeight();
                dragHelper.settleCapturedViewAt(startLeft, top);
            } else {
                dragHelper.settleCapturedViewAt(startLeft, startTop);
            }

            invalidate();
        }

    }

}
