/* Copyright Urban Airship and Contributors */

package com.urbanairship.iam.banner;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.AnimatorRes;
import android.support.annotation.CallSuper;
import android.support.annotation.LayoutRes;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.graphics.ColorUtils;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.WindowInsetsCompat;
import android.support.v4.widget.ViewDragHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.urbanairship.R;
import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.assets.Assets;
import com.urbanairship.iam.view.BackgroundDrawableBuilder;
import com.urbanairship.iam.view.BorderRadius;
import com.urbanairship.iam.view.InAppButtonLayout;
import com.urbanairship.iam.view.InAppViewUtils;
import com.urbanairship.iam.view.MediaView;

/**
 * Banner view.
 */
public class BannerView extends FrameLayout implements InAppButtonLayout.ButtonClickListener,
        View.OnClickListener,
        BannerDismissLayout.Listener {

    private static final float PRESSED_ALPHA_PERCENT = .2f;

    @Nullable
    private final Assets assets;

    @NonNull
    private BannerDisplayContent displayContent;

    @NonNull
    private Timer timer;

    @AnimatorRes
    private int animationIn;

    @AnimatorRes
    private int animationOut;

    private boolean isDismissed = false;
    private boolean isResumed = false;

    @Nullable
    private View subView;

    @Nullable
    private Listener listener;

    /**
     * Banner view listener.
     */
    public interface Listener {

        /**
         * Called when a button is clicked.
         *
         * @param view The banner view.
         * @param buttonInfo The button info.
         */
        @MainThread
        void onButtonClicked(@NonNull BannerView view, @NonNull ButtonInfo buttonInfo);

        /**
         * Called when the banner is clicked.
         *
         * @param view The banner view.
         */
        @MainThread
        void onBannerClicked(@NonNull BannerView view);

        /**
         * Called when the banner times out.
         *
         * @param view The banner view.
         */
        @MainThread
        void onTimedOut(@NonNull BannerView view);

        /**
         * Called when the banner is dismissed by the user.
         *
         * @param view The banner view.
         */
        @MainThread
        void onUserDismissed(@NonNull BannerView view);

    }

    /**
     * Default constructor.
     *
     * @param context The context.
     * @param displayContent The banner display content.
     * @param assets The in-app message assets.
     */
    public BannerView(@NonNull Context context, @NonNull BannerDisplayContent displayContent, @Nullable Assets assets) {
        super(context);
        this.displayContent = displayContent;
        this.assets = assets;

        final long duration = displayContent.getDuration();
        this.timer = new Timer(duration) {
            @Override
            protected void onFinish() {
                dismiss(true);
                Listener listener = BannerView.this.listener;
                if (listener != null) {
                    listener.onTimedOut(BannerView.this);
                }
            }
        };
    }

    /**
     * Sets the banner listener.
     *
     * @param listener The banner listener.
     */
    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /**
     * Called to inflate and attach the in-app message view.
     *
     * @param inflater The inflater.
     * @return The view.
     */
    @NonNull
    @MainThread
    protected View onCreateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        // Main view
        BannerDismissLayout view = (BannerDismissLayout) inflater.inflate(getLayout(), container, false);
        view.setPlacement(displayContent.getPlacement());
        view.setListener(this);

        // Inflate the banner content
        ViewStub bannerContent = view.findViewById(R.id.banner_content);
        bannerContent.setLayoutResource(getContentLayout());
        bannerContent.inflate();

        // Banner View
        LinearLayout bannerView = view.findViewById(R.id.banner);
        ViewCompat.setBackground(bannerView, createBannerBackground());

        if (displayContent.getBorderRadius() > 0) {
            @BorderRadius.BorderRadiusFlag
            int borderRadiusFlag = BannerDisplayContent.PLACEMENT_TOP.equals(displayContent.getPlacement()) ? BorderRadius.BOTTOM : BorderRadius.TOP;
            BorderRadius.applyBorderRadiusPadding(bannerView, displayContent.getBorderRadius(), borderRadiusFlag);
        }

        if (!displayContent.getActions().isEmpty()) {
            bannerView.setClickable(true);
            bannerView.setOnClickListener(this);
        }

        // Heading
        TextView heading = view.findViewById(R.id.heading);
        if (displayContent.getHeading() != null) {
            InAppViewUtils.applyTextInfo(heading, displayContent.getHeading());
        } else {
            heading.setVisibility(View.GONE);
        }

        // Body
        TextView body = view.findViewById(R.id.body);
        if (displayContent.getBody() != null) {
            InAppViewUtils.applyTextInfo(body, displayContent.getBody());
        } else {
            body.setVisibility(View.GONE);
        }

        // Media
        MediaView mediaView = view.findViewById(R.id.media);
        if (displayContent.getMedia() != null) {
            InAppViewUtils.loadMediaInfo(mediaView, displayContent.getMedia(), assets);
        } else {
            mediaView.setVisibility(View.GONE);
        }

        // Button Layout
        InAppButtonLayout buttonLayout = view.findViewById(R.id.buttons);
        if (displayContent.getButtons().isEmpty()) {
            buttonLayout.setVisibility(View.GONE);
        } else {
            buttonLayout.setButtons(displayContent.getButtonLayout(), displayContent.getButtons());
            buttonLayout.setButtonClickListener(this);
        }

        // Banner dismiss pull
        View bannerPull = view.findViewById(R.id.banner_pull);
        Drawable drawable = DrawableCompat.wrap(bannerPull.getBackground()).mutate();
        DrawableCompat.setTint(drawable, displayContent.getDismissButtonColor());
        ViewCompat.setBackground(bannerPull, drawable);

        // If the parent is `android.R.id.content` apply the window insets
        if (getParent() != null && ((View) getParent()).getId() == android.R.id.content) {
            applyWindowInsets(view);
        }

        return view;
    }

    /**
     * Resumes the banner's timer.
     */
    @MainThread
    @CallSuper
    protected void onResume() {
        isResumed = true;
        if (isDismissed) {
            getTimer().start();
        }
    }

    /**
     * Pauses the banner's timer.
     */
    @MainThread
    @CallSuper
    protected void onPause() {
        isResumed = false;
        getTimer().stop();
    }

    /**
     * Used to dismiss the message.
     *
     * @param animate {@code true} to animate the view out, otherwise {@code false}.
     */
    @MainThread
    protected void dismiss(boolean animate) {
        isDismissed = true;
        getTimer().stop();

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
    @MainThread
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
            if (subView == null) {
                subView = onCreateView(LayoutInflater.from(getContext()), this);
                addView(subView);
                if (animationIn != 0) {
                    Animator animator = AnimatorInflater.loadAnimator(getContext(), animationIn);
                    animator.setTarget(subView);
                    animator.start();
                }

                onResume();
            }
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

    @Override
    public void onButtonClicked(@NonNull View view, @NonNull ButtonInfo buttonInfo) {
        Listener listener = this.listener;
        if (listener != null) {
            listener.onButtonClicked(this, buttonInfo);
        }
    }

    @Override
    public void onDismissed(@NonNull View view) {
        Listener listener = this.listener;
        if (listener != null) {
            listener.onUserDismissed(this);
        }
        dismiss(false);
    }

    @Override
    public void onDragStateChanged(@NonNull View view, int state) {
        switch (state) {
            case ViewDragHelper.STATE_DRAGGING:
                getTimer().stop();
                break;
            case ViewDragHelper.STATE_IDLE:
                if (isResumed) {
                    getTimer().start();
                }
                break;
        }
    }

    @Override
    public void onClick(@NonNull View view) {
        Listener listener = this.listener;
        if (listener != null) {
            listener.onBannerClicked(this);
        }
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
     * Gets the in-app message.
     *
     * @return The in-app message.
     */
    @NonNull
    protected BannerDisplayContent getDisplayContent() {
        return displayContent;
    }

    /**
     * Applies the window insets to the view.
     *
     * @param view The fragment's view.
     */
    private void applyWindowInsets(@NonNull View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, new android.support.v4.view.OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View view, @NonNull WindowInsetsCompat src) {
                WindowInsetsCompat copy = new WindowInsetsCompat(src);

                int left, top, right, bottom;
                left = right = Math.max(src.getSystemWindowInsetLeft(), src.getSystemWindowInsetRight());
                top = src.getSystemWindowInsetTop();
                bottom = src.getSystemWindowInsetBottom();

                if (displayContent.getPlacement().equals(BannerDisplayContent.PLACEMENT_TOP)) {
                    top = isActionBarEnabled() ? 0 : top;
                } else {
                    bottom = isNavigationTranslucent() ? bottom : 0;
                }

                copy = copy.replaceSystemWindowInsets(left, top, right, bottom);
                ViewCompat.onApplyWindowInsets(view, copy);
                return src;
            }
        });

        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View view) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    view.dispatchApplyWindowInsets(view.getRootWindowInsets());
                    return;
                }

                if (displayContent.getPlacement().equals(BannerDisplayContent.PLACEMENT_TOP) && !isActionBarEnabled()) {
                    int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
                    if (resourceId > 0) {
                        int height = getResources().getDimensionPixelSize(resourceId);
                        view.setPadding(0, height, 0, 0);
                    }
                }
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View view) {
                view.removeOnAttachStateChangeListener(this);
            }
        });
    }

    /**
     * Checks if the navigation bar is configured to be translucent on the current theme.
     *
     * @return {@code true} if the theme specifies a translucent navigation bar, otherwise {@code false}.
     */
    private boolean isNavigationTranslucent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return false;
        }

        TypedArray a = getContext().obtainStyledAttributes(new int[] { android.R.attr.windowTranslucentNavigation });
        boolean isEnabled = a.getBoolean(0, false);
        a.recycle();
        return isEnabled;
    }

    /**
     * Checks if the standard or compat action bar is enabled for this activity.
     *
     * @return {@code true} if the action bar is enabled, otherwise {@code false}.
     */
    private boolean isActionBarEnabled() {
        int compatWindowActionBarAttr = getContext().getResources().getIdentifier("windowActionBar", "attr", getContext().getPackageName());
        TypedArray a = getContext().obtainStyledAttributes(new int[] { android.R.attr.windowActionBar, compatWindowActionBarAttr });
        boolean isEnabled = a.getBoolean(0, false) || a.getBoolean(1, false);
        a.recycle();

        return isEnabled;
    }

    /**
     * Gets the banner layout for the banner's placement.
     *
     * @return The banner layout.
     */
    @LayoutRes
    private int getLayout() {
        switch (displayContent.getPlacement()) {
            case BannerDisplayContent.PLACEMENT_TOP:
                return R.layout.ua_iam_banner_top;
            case BannerDisplayContent.PLACEMENT_BOTTOM:
            default:
                return R.layout.ua_iam_banner_bottom;
        }
    }

    /**
     * Creates the banner's background drawable.
     *
     * @return The banner's background drawable.
     */
    @NonNull
    private Drawable createBannerBackground() {
        int pressedColor = ColorUtils.setAlphaComponent(displayContent.getDismissButtonColor(), Math.round(Color.alpha(displayContent.getDismissButtonColor()) * PRESSED_ALPHA_PERCENT));

        @BorderRadius.BorderRadiusFlag
        int borderRadiusFlag = BannerDisplayContent.PLACEMENT_TOP.equals(displayContent.getPlacement()) ? BorderRadius.BOTTOM : BorderRadius.TOP;

        return BackgroundDrawableBuilder.newBuilder(getContext())
                                        .setBackgroundColor(displayContent.getBackgroundColor())
                                        .setPressedColor(pressedColor)
                                        .setBorderRadius(displayContent.getBorderRadius(), borderRadiusFlag)
                                        .build();
    }

    /**
     * Gets the banner content layout for the banner's template.
     *
     * @return The banner template layout.
     */
    @LayoutRes
    private int getContentLayout() {
        switch (displayContent.getTemplate()) {
            case BannerDisplayContent.TEMPLATE_RIGHT_MEDIA:
                return R.layout.ua_iam_banner_content_right_media;
            case BannerDisplayContent.TEMPLATE_LEFT_MEDIA:
            default:
                return R.layout.ua_iam_banner_content_left_media;
        }
    }

}
