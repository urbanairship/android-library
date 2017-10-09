/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.banner;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.urbanairship.Logger;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.iam.DisplayArguments;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.iam.Timer;

import java.util.Map;

/**
 * A fragment that displays an in-app message.
 */
public class BannerFragment extends Fragment {

    private static final String DISMISSED = "DISMISSED";

    /**
     * Default duration in milliseconds. The value is only used if the in-app message's
     * {@link BannerDisplayContent#getDuration()} returns null.
     */
    private static final String DISPLAY_ARGS = "DISPLAY_ARGS";
    private static final String EXIT_ANIMATION = "EXIT_ANIMATION";

    private boolean isDismissed;
    private Timer timer;
    private DisplayArguments displayArguments;
    private BannerDisplayContent displayContent;

    public static BannerFragment newInstance(DisplayArguments arguments, int exitAnimation) {
        BannerFragment fragment = new BannerFragment();

        Bundle bundle = new Bundle();
        bundle.putParcelable(DISPLAY_ARGS, arguments);
        bundle.putInt(EXIT_ANIMATION, exitAnimation);
        fragment.setArguments(bundle);
        return fragment;
    }


    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        this.setRetainInstance(true);

        this.displayArguments = getArguments().getParcelable(DISPLAY_ARGS);
        if (displayArguments == null) {
            dismiss(false);
            return;
        }

        this.displayContent = displayArguments.getMessage().getDisplayContent();
        if (displayContent == null) {
            dismiss(false);
            return;
        }

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

        if (displayArguments != null && !displayArguments.getHandler().requestDisplayLock(getActivity())) {
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

            if (displayArguments != null) {
                displayArguments.getHandler().continueOnNextActivity();
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (!getActivity().isChangingConfigurations()) {
            if (displayArguments != null &&  !isDismissed) {
                displayArguments.getHandler().continueOnNextActivity();
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

        if (displayArguments != null) {
            displayArguments.getHandler().finished();
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
}

