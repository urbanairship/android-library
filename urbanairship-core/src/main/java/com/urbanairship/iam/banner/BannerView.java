package com.urbanairship.iam.banner;
/* Copyright 2018 Urban Airship and Contributors */

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.LayoutRes;
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
import android.widget.LinearLayout;
import android.widget.TextView;

import com.urbanairship.R;
import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.InAppActionUtils;
import com.urbanairship.iam.InAppMessageCache;
import com.urbanairship.iam.ResolutionInfo;
import com.urbanairship.iam.view.BackgroundDrawableBuilder;
import com.urbanairship.iam.view.BorderRadius;
import com.urbanairship.iam.view.InAppButtonLayout;
import com.urbanairship.iam.view.InAppViewUtils;
import com.urbanairship.iam.view.MediaView;

/**
 * Banner view.
 */
public class BannerView extends InAppViewGroup implements InAppButtonLayout.ButtonClickListener,
                                                          View.OnClickListener,
                                                          BannerDismissLayout.Listener {

    private static final float PRESSED_ALPHA_PERCENT = .2f;

    @Nullable
    private final InAppMessageCache cache;

    @NonNull
    private BannerDisplayContent displayContent;

    @NonNull
    private Timer timer;

    /**
     * Default constructor.
     *
     * @param context The context.
     * @param displayHandler The display handler.
     * @param displayContent The banner display content.
     * @param cache The IAM cache.
     */
    public BannerView(@NonNull Context context, @NonNull DisplayHandler displayHandler, @NonNull BannerDisplayContent displayContent, @Nullable InAppMessageCache cache) {
        super(context, displayHandler);
        this.displayContent = displayContent;
        this.cache = cache;

        final long duration = displayContent.getDuration();
        this.timer = new Timer(duration) {
            @Override
            protected void onFinish() {
                if (isResumed()) {
                    dismiss(true, ResolutionInfo.timedOut(duration));
                }
            }
        };
    }

    @NonNull
    @Override
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
            InAppViewUtils.loadMediaInfo(mediaView, displayContent.getMedia(), cache);
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

    @Override
    protected void onResume(@NonNull Activity activity) {
        super.onResume(activity);
        timer.start();
    }

    @Override
    protected void onPause(@NonNull Activity activity) {
        super.onPause(activity);
        timer.stop();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        timer.stop();
    }

    @Override
    public void onButtonClicked(@NonNull View view, @NonNull ButtonInfo buttonInfo) {
        InAppActionUtils.runActions(buttonInfo);
        dismiss(true, ResolutionInfo.buttonPressed(buttonInfo, timer.getRunTime()));

        if (buttonInfo.getBehavior().equals(ButtonInfo.BEHAVIOR_CANCEL)) {
            getDisplayHandler().cancelFutureDisplays();
        }
    }

    @Override
    public void onDismissed(@NonNull View view) {
        dismiss(false, ResolutionInfo.dismissed(timer.getRunTime()));
    }

    @Override
    public void onDragStateChanged(@NonNull View view, int state) {
        switch (state) {
            case ViewDragHelper.STATE_DRAGGING:
                getTimer().stop();
                break;
            case ViewDragHelper.STATE_IDLE:
                if (isResumed()) {
                    getTimer().start();
                }
                break;
        }
    }

    @Override
    public void onClick(@NonNull View view) {
        if (displayContent.getActions().isEmpty()) {
            return;
        }

        InAppActionUtils.runActions(displayContent.getActions());
        dismiss(true, ResolutionInfo.messageClicked(timer.getRunTime()));
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
