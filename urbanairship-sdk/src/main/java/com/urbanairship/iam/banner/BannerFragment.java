/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.banner;

import android.app.Fragment;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.AnimatorRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.support.v4.view.OnApplyWindowInsetsListener;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.WindowInsetsCompat;
import android.support.v4.widget.ViewDragHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.urbanairship.ActivityMonitor;
import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageCache;
import com.urbanairship.iam.view.InAppButtonLayout;
import com.urbanairship.iam.view.InAppTextView;
import com.urbanairship.json.JsonValue;
import com.urbanairship.messagecenter.ImageLoader;
import com.urbanairship.push.iam.Timer;
import com.urbanairship.util.Checks;

import java.util.Map;

/**
 * A fragment that displays an in-app message banner.
 */
public class BannerFragment extends Fragment implements InAppButtonLayout.ButtonClickListener,
                                                        BannerDismissLayout.Listener {

    /**
     * Cache key for the banner image.
     */
    public static final String IMAGE_CACHE_KEY = "IMAGE_CACHE_KEY";

    private static final String DISMISSED = "DISMISSED";
    private static final String IN_APP_MESSAGE = "IN_APP_MESSAGE";
    private static final String EXIT_ANIMATION = "EXIT_ANIMATION";
    private static final String DISPLAY_HANDLER = "DISPLAY_HANDLER";
    private static final String CACHE = "CACHE";

    private boolean isDismissed;
    private Timer timer;
    private InAppMessage inAppMessage;
    private DisplayHandler displayHandler;
    private InAppMessageCache cache;
    private BannerDisplayContent displayContent;

    /**
     * Factory method to create a BannerFragment.
     *
     * @param builder The fragment builder.
     * @return A banner fragment.
     */
    private static BannerFragment newInstance(Builder builder) {
        BannerFragment fragment = new BannerFragment();

        Bundle bundle = new Bundle();
        if (builder.cache != null) {
            bundle.putParcelable(IN_APP_MESSAGE, builder.cache);
        }

        bundle.putInt(EXIT_ANIMATION, builder.exitAnimation);
        bundle.putParcelable(IN_APP_MESSAGE, builder.inAppMessage);
        bundle.putParcelable(DISPLAY_HANDLER, builder.displayHandler);
        bundle.putParcelable(CACHE, builder.cache);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        this.setRetainInstance(true);

        this.displayHandler = getArguments().getParcelable(DISPLAY_HANDLER);
        this.inAppMessage = getArguments().getParcelable(IN_APP_MESSAGE);
        this.cache = getArguments().getParcelable(CACHE);

        if (displayHandler == null || inAppMessage == null || inAppMessage.getType() != InAppMessage.TYPE_BANNER) {
            isDismissed = true;
            removeSelf(false);
            return;
        }

        this.displayContent = inAppMessage.getDisplayContent();
        long duration = displayContent.getDuration();
        this.timer = new Timer(duration) {
            @Override
            protected void onFinish() {
                if (isResumed()) {
                    dismiss(true);
                }
            }
        };

        if (savedInstance != null) {
            isDismissed = savedInstance.getBoolean(DISMISSED, false);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable final ViewGroup container, Bundle savedInstanceState) {
        if (isDismissed) {
            return null;
        }

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
        bannerView.setBackgroundColor(displayContent.getBackgroundColor());

        // Heading
        InAppTextView heading = view.findViewById(R.id.heading);
        if (displayContent.getHeading() != null) {
            heading.setTextInfo(displayContent.getHeading());
        } else {
            heading.setVisibility(View.GONE);
        }

        // Body
        InAppTextView body = view.findViewById(R.id.body);
        if (displayContent.getBody() != null) {
            body.setTextInfo(displayContent.getBody());
        } else {
            body.setVisibility(View.GONE);
        }

        // Image
        ImageView image = view.findViewById(R.id.image);
        if (displayContent.getImage() != null) {
            String imageLocation = cache != null ? cache.getBundle().getString(IMAGE_CACHE_KEY, null) : displayContent.getImage().getUrl();
            if (imageLocation != null) {
                ImageLoader.shared(getActivity()).load(imageLocation, 0, image);
            }
        } else {
            image.setVisibility(View.GONE);
        }

        // Button Layout
        InAppButtonLayout buttonLayout = view.findViewById(R.id.buttons);
        buttonLayout.setButtons(displayContent.getButtonLayout(), displayContent.getButtons());
        buttonLayout.setButtonClickListener(this);

        // Banner dismiss pull
        View bannerPull = view.findViewById(R.id.banner_pull);
        bannerPull.getBackground().setColorFilter(displayContent.getDismissButtonColor(), PorterDuff.Mode.MULTIPLY);

        // If the parent is `android.R.id.content` apply the window insets
        if (container != null && container.getId() == android.R.id.content) {
            applyWindowInsets(view);
        }

        return view;
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(DISMISSED, isDismissed);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (displayHandler != null && !displayHandler.requestDisplayLock(getActivity())) {
            isDismissed = true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (isDismissed) {
            removeSelf(false);
            return;
        }

        timer.start();

    }

    @Override
    public void onPause() {
        super.onPause();
        timer.stop();

        if (!isDismissed && getActivity().isFinishing()) {
            isDismissed = true;

            if (displayHandler != null) {
                displayHandler.continueOnNextActivity();
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (ActivityMonitor.shared(getActivity()).getResumedActivity() != null && !getActivity().isChangingConfigurations()) {
            if (displayHandler != null && !isDismissed) {
                displayHandler.continueOnNextActivity();
            }

            isDismissed = true;
        }

        if (isDismissed) {
            removeSelf(false);
        }
    }


    /**
     * Helper method to remove itself from the activity.
     *
     * @param animate {@code true} if the fragment should animate out, otherwise {@code false}.
     */
    private void removeSelf(boolean animate) {
        if (getActivity() != null) {

            /*
             * Commit allowing state loss is ok because we keep track or our own
             * state and will dismiss the fragment anyways when its restored.
             * We do not rely on the fragment manager to keep track for us.
             */

            int exit;

            switch (displayContent.getPlacement()) {
                case BannerDisplayContent.PLACEMENT_TOP:
                    exit = R.animator.ua_iam_slide_out_top;
                    break;

                case BannerDisplayContent.PLACEMENT_BOTTOM:
                default:
                    exit = R.animator.ua_iam_slide_out_bottom;
            }

            //noinspection ResourceType
            getActivity().getFragmentManager().beginTransaction()
                         .setCustomAnimations(0, animate ? exit : 0)
                         .remove(this)
                         .commitAllowingStateLoss();
        }
    }

    /**
     * Dismisses the fragment.
     *
     * @param animate {@code true} if the fragment should animate out, otherwise {@code false}.
     */
    public void dismiss(boolean animate) {
        if (isDismissed) {
            return;
        }

        if (displayHandler != null) {
            displayHandler.finished();
        }

        timer.stop();

        if (isDismissed) {
            return;
        }

        isDismissed = true;
        removeSelf(animate);

    }

    /**
     * Checks if the fragment has been dismissed.
     *
     * @return {@code true} if the fragment is dismissed, otherwise {@code false}.
     */
    public boolean isDismissed() {
        return isDismissed;
    }

    /**
     * Gets the in-app message.
     *
     * @return The in-app message.
     */
    public BannerDisplayContent getdisplayContent() {
        return displayContent;
    }

    @Override
    public void onButtonClicked(View view, ButtonInfo buttonInfo) {
        runActions(buttonInfo.getActions());
        dismiss(true);

        if (buttonInfo.getBehavior().equals(ButtonInfo.BEHAVIOR_CANCEL)) {
            UAirship.shared().getInAppMessagingManager().cancelMessage(inAppMessage.getId());
        }
    }

    public void onDismissed(View view) {
        dismiss(false);
    }

    @Override
    public void onDragStateChanged(View view, int state) {
        switch (state) {
            case ViewDragHelper.STATE_DRAGGING:
                timer.stop();
                break;
            case ViewDragHelper.STATE_IDLE:
                if (isResumed()) {
                    timer.start();
                }
                break;
        }
    }

    /**
     * In-app message display timer.
     *
     * @return The in-app message display timer.
     */
    protected Timer getTimer() {
        return timer;
    }

    /**
     * Helper method to run a map of action name to action values.
     *
     * @param actions The action value map.
     */
    protected void runActions(Map<String, JsonValue> actions) {
        if (actions == null) {
            return;
        }

        for (Map.Entry<String, JsonValue> entry : actions.entrySet()) {
            ActionRunRequest.createRequest(entry.getKey())
                            .setValue(entry.getValue())
                            .run();
        }
    }

    /**
     * Applies the window insets to the view.
     *
     * @param view The fragment's view.
     */
    private void applyWindowInsets(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, new OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View view, WindowInsetsCompat src) {
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
            public void onViewAttachedToWindow(View view) {
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
            public void onViewDetachedFromWindow(View view) {
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

        TypedArray a = getActivity().obtainStyledAttributes(new int[] { android.R.attr.windowTranslucentNavigation });
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
        int compatWindowActionBarAttr = getActivity().getResources().getIdentifier("windowActionBar", "attr", getActivity().getPackageName());
        TypedArray a = getActivity().obtainStyledAttributes(new int[] { android.R.attr.windowActionBar, compatWindowActionBarAttr });
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
     * Gets the banner content layout for the banner's template.
     * @return The banner template layout.
     */
    @LayoutRes
    private int getContentLayout() {
        switch (displayContent.getTemplate()) {
            case BannerDisplayContent.TEMPLATE_RIGHT_IMAGE:
                return R.layout.ua_iam_banner_content_right_image;
            case BannerDisplayContent.TEMPLATE_LEFT_IMAGE:
            default:
                return R.layout.ua_iam_banner_content_left_image;
        }
    }

    /**
     * Creates a new fragment builder.
     *
     * @return A banner fragment builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * BannerFragment builder.
     */
    public static class Builder {

        private InAppMessage inAppMessage;
        private DisplayHandler displayHandler;
        private InAppMessageCache cache;
        private int exitAnimation;

        private Builder() {}

        /**
         * Sets the display handler.
         *
         * @param displayHandler The display handler.
         * @return The builder instance.
         */
        public Builder setDisplayHandler(DisplayHandler displayHandler) {
            this.displayHandler = displayHandler;
            return this;
        }

        /**
         * Sets the in-app message.
         *
         * @param inAppMessage The in-app message.
         * @return The builder instance.
         */
        public Builder setInAppMessage(InAppMessage inAppMessage) {
            this.inAppMessage = inAppMessage;
            return this;
        }

        /**
         * Sets the cache.
         *
         * @param cache The in-app message cache.
         * @return The builder instance.
         */
        public Builder setCache(InAppMessageCache cache) {
            this.cache = cache;
            return this;
        }

        /**
         * Sets the exit animation.
         *
         * @param animation The exit animation.
         * @return The builder instance.
         */
        public Builder setExitAnimation(@AnimatorRes int animation) {
            this.exitAnimation = animation;
            return this;
        }

        /**
         * Builds the fragment.
         *
         * @return The banner fragment.
         * @throws IllegalArgumentException if the in-app message or display handler is not set.
         */
        public BannerFragment build() {
            Checks.checkNotNull(inAppMessage, "Missing in-app message.");
            Checks.checkNotNull(displayHandler, "Missing display handler.");
            return BannerFragment.newInstance(this);
        }
    }
}

