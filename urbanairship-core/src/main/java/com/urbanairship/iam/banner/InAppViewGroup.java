/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam.banner;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.support.annotation.AnimatorRes;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.app.SimpleActivityListener;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.ResolutionInfo;

/**
 * View Group for displaying an in-app message within an existing activity,
 * such as our banner in-app messages.
 */
public abstract class InAppViewGroup extends FrameLayout {

    private boolean isResumed = false;
    private boolean isDismissed = false;
    private View subView;

    @AnimatorRes
    private int animationIn;

    @AnimatorRes
    private int animationOut;

    @NonNull
    private final DisplayHandler displayHandler;

    /**
     * Default constructor.
     *
     * @param context The application context.
     */
    public InAppViewGroup(@NonNull Context context, @NonNull DisplayHandler displayHandler) {
        super(context);
        this.displayHandler = displayHandler;
    }

    /**
     * Called to inflate and attach the in-app message view.
     *
     * @param inflater The inflater.
     * @return The view.
     */
    @NonNull
    protected abstract View onCreateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container);

    /**
     * If the view is dismissed or not.
     *
     * @return {@code true} if dismissed, otherwise {@code false}.
     */
    protected boolean isDismissed() {
        return isDismissed;
    }

    /**
     * If the view's activity is resumed or not.
     *
     * @return {@code true} if resumed, otherwise {@code false}.
     */
    protected boolean isResumed() {
        return isResumed;
    }

    /**
     * Called when the activity is resumed. If the activity is already resumed
     * when the activity is displayed, it will be called immediately.
     *
     * @param activity The activity.
     */
    @CallSuper
    protected void onResume(@NonNull Activity activity) {
        isResumed = true;

        if (!displayHandler.isDisplayAllowed(activity)) {
            dismiss(true);
        }
    }

    /**
     * Called when the activity is paused.
     *
     * @param activity The activity.
     */
    protected void onPause(@NonNull Activity activity) {
        isResumed = false;
    }


    /**
     * Used to dismiss the message.
     *
     * @param animate {@code true} to animate the view out, otherwise {@code false}.
     */
    protected void dismiss(boolean animate) {
        isDismissed = true;

        if (animate && subView != null && animationOut != 0) {
            clearAnimation();
            Animator animator = AnimatorInflater.loadAnimator(getContext(), animationOut);
            animator.setTarget(subView);
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
    private void removeSelf() {
        if (this.getParent() instanceof ViewGroup) {
            final ViewGroup parent = (ViewGroup) this.getParent();
            parent.removeView(this);
            subView = null;
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        final Activity activity = (Activity) getContext();
        if (activity == null) {
            return;
        }

        if (visibility == VISIBLE && !isDismissed) {
            GlobalActivityMonitor.shared(getContext()).addActivityListener(new SimpleActivityListener() {
                @Override
                public void onActivityPaused(@NonNull Activity activity) {
                    if (getContext() == activity && !isDismissed) {
                        onPause(activity);
                    }
                }

                @Override
                public void onActivityResumed(@NonNull Activity activity) {
                    if (getContext() == activity && !isDismissed) {
                        if (!displayHandler.isDisplayAllowed(activity)) {
                            dismiss(false);
                        } else {
                            onResume(activity);
                        }
                    }
                }

                @Override
                public void onActivityStopped(@NonNull Activity activity) {
                    if (getContext() == activity && !isDismissed) {
                        isDismissed = true;
                        displayHandler.continueOnNextActivity();
                        dismiss(false);
                    }

                    GlobalActivityMonitor.shared(activity).removeActivityListener(this);
                }
            });

            if (subView == null) {
                subView = onCreateView(LayoutInflater.from(getContext()), this);
                addView(subView);
                if (animationIn != 0) {
                    Animator animator = AnimatorInflater.loadAnimator(getContext(), animationIn);
                    animator.setTarget(subView);
                    animator.start();
                }
            }

            onResume(activity);
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
     * Dismisses the message.
     *
     * @param animate {@code true} if the view should animate out, otherwise {@code false}.
     * @param resolutionInfo The resolution info.
     */
    protected void dismiss(boolean animate, @NonNull ResolutionInfo resolutionInfo) {
        if (isDismissed) {
            return;
        }
        displayHandler.finished(resolutionInfo);
        dismiss(animate);
    }

    /**
     * Gets the display handler.
     *
     * @return The display handler
     */
    @NonNull
    protected DisplayHandler getDisplayHandler() {
        return displayHandler;
    }
}
