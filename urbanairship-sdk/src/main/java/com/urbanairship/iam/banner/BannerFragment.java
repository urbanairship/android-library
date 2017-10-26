/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.banner;

import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.AnimatorRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageCache;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.iam.Timer;
import com.urbanairship.util.Checks;

import java.util.Map;

/**
 * A fragment that displays an in-app message.
 */
public class BannerFragment extends Fragment {

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
    private int exitAnimation;


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
            dismiss(false);
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

        if (!getActivity().isChangingConfigurations()) {
            if (displayHandler != null && !isDismissed) {
                displayHandler.continueOnNextActivity();
            }
            isDismissed = true;
            removeSelf(false);
        }

        if (isDismissed) {
            removeSelf(false);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {
        if (isDismissed || displayContent == null) {
            return null;
        }

        // TODO
        return null;
    }


    private void removeSelf(boolean animate) {
        if (getActivity() != null) {

            /*
             * Commit allowing state loss is ok because we keep track or our own
             * state and will dismiss the fragment anyways when its restored.
             * We do not rely on the fragment manager to keep track for us.
             */

            //noinspection ResourceType
            getActivity().getFragmentManager().beginTransaction()
                         .setCustomAnimations(0, animate ? getArguments().getInt(EXIT_ANIMATION, 0) : 0)
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

    /**
     * In-app message display timer.
     *
     * @return The in-app message display timer.
     */
    protected Timer getTimer() {
        return timer;
    }

    /**
     * Gets the dismiss animation resource ID.
     *
     * @return The dismiss animation resource ID.
     */
    public int getDismissAnimation() {
        return getArguments().getInt(EXIT_ANIMATION, 0);
    }

    /**
     * Helper method to run a map of action name to action values.
     *
     * @param actions The action value map.
     * @param situation The actions' situation.
     */
    private void runActions(Map<String, JsonValue> actions, @Action.Situation int situation) {
        if (actions == null) {
            return;
        }

        for (Map.Entry<String, JsonValue> entry : actions.entrySet()) {
            ActionRunRequest.createRequest(entry.getKey())
                            .setValue(entry.getValue())
                            .setSituation(situation)
                            .run();
        }


    }

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

        private Builder() {};

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

