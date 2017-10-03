/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.banner;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.iam.DisplayArguments;
import com.urbanairship.push.iam.Timer;
import com.urbanairship.push.iam.view.Banner;
import com.urbanairship.push.iam.view.SwipeDismissViewLayout;
import com.urbanairship.push.notifications.NotificationActionButton;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;

import java.util.Map;

/**
 * A fragment that displays an in-app message.
 */
public class BannerFragment extends Fragment {

    private static final String DISMISSED = "DISMISSED";
    private static Boolean isCardViewAvailable;

    /**
     * Default duration in milliseconds. The value is only used if the in-app message's
     * {@link BannerDisplayContent#getDuration()} returns null.
     */
    public static final long DEFAULT_DURATION = 15000;

    private static final String DISPLAY_ARGS = "DISPLAY_ARGS";
    private static final String EXIT_ANIMATION = "EXIT_ANIMATION";

    private boolean isDismissed;
    private Timer timer;
    private DisplayArguments displayArguments;
    private BannerDisplayContent displayInfo;

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

        this.displayInfo = displayArguments.getMessage().getDisplayContent();
        if (displayInfo == null) {
            dismiss(false);
            return;
        }

        long duration = displayInfo.getDuration() == null ? DEFAULT_DURATION : displayInfo.getDuration();
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
        Logger.error("onStop: " + this);
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
        if (isDismissed || displayInfo == null) {
            return null;
        }

        int layout = checkCardViewDependencyAvailable() ? R.layout.ua_fragment_iam_card : R.layout.ua_fragment_iam;

        SwipeDismissViewLayout view = (SwipeDismissViewLayout) inflater.inflate(layout, container, false);

        // Adjust gravity depending on the message's position
        if (container != null && container instanceof FrameLayout) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
            layoutParams.gravity = displayInfo.getPosition() == BannerDisplayContent.POSITION_TOP ? Gravity.TOP : Gravity.BOTTOM;
            view.setLayoutParams(layoutParams);
        }

        view.setListener(new SwipeDismissViewLayout.Listener() {
            @Override
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
        });

        FrameLayout bannerView = (FrameLayout) view.findViewById(R.id.in_app_message);

        if (!displayInfo.getClickActionValues().isEmpty()) {
            bannerView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss(true);

                    runActions(displayInfo.getClickActionValues(), Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON);
                }
            });
        } else {
            bannerView.setClickable(false);
            bannerView.setForeground(null);
        }

        Banner banner = (Banner) bannerView;
        banner.setOnDismissClickListener(new Banner.OnDismissClickListener() {
            @Override
            public void onDismissClick() {
                dismiss(true);
            }
        });

        banner.setOnActionClickListener(new Banner.OnActionClickListener() {
            @Override
            public void onActionClick(NotificationActionButton actionButton) {
                Logger.info("In-app message button clicked: " + actionButton.getId());
                dismiss(true);

                @Action.Situation int situation = actionButton.isForegroundAction() ? Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON :
                                                  Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON;

                runActions(displayInfo.getButtonActionValues(actionButton.getId()), situation);
            }
        });

        if (displayInfo.getPrimaryColor() != null) {
            banner.setPrimaryColor(displayInfo.getPrimaryColor());
        }

        if (displayInfo.getSecondaryColor() != null) {
            banner.setSecondaryColor(displayInfo.getSecondaryColor());
        }

        banner.setText(displayInfo.getAlert());

        NotificationActionButtonGroup group = UAirship.shared().getPushManager().getNotificationActionGroup(displayInfo.getButtonGroupId());
        banner.setNotificationActionButtonGroup(group);

        // Only apply window insets fix for Android M and  if we are displaying the in-app message in the default "content" view
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && container != null && container.getId() == android.R.id.content) {
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @SuppressLint("NewApi")
                @Override
                public void onViewAttachedToWindow(View v) {
                    if (!ViewCompat.getFitsSystemWindows(v)) {
                        return;
                    }

                    switch (displayInfo.getPosition()) {
                        case BannerDisplayContent.POSITION_BOTTOM:
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (getActivity().getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION) > 0) {
                                v.dispatchApplyWindowInsets(v.getRootWindowInsets());
                            }
                            break;

                        case BannerDisplayContent.POSITION_TOP:
                            if ((getActivity().getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS) > 0) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    v.dispatchApplyWindowInsets(v.getRootWindowInsets());
                                } else {
                                    int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
                                    if (resourceId > 0) {
                                        int height = getResources().getDimensionPixelSize(resourceId);
                                        v.setPadding(v.getPaddingLeft(), v.getPaddingTop() + height, v.getPaddingRight(), v.getPaddingBottom());
                                    }
                                }
                            }
                            break;
                    }
                }

                @Override
                public void onViewDetachedFromWindow(View v) {}
            });
        }


        return view;
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
    public BannerDisplayContent getDisplayInfo() {
        return displayInfo;
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
     * Helper method to check if the card view dependency is available or not.
     *
     * @return {@code true} if available, otherwise {@code false}.
     */
    private static boolean checkCardViewDependencyAvailable() {
        if (isCardViewAvailable == null) {
            try {
                Class.forName("android.support.v7.widget.CardView");
                isCardViewAvailable = true;
            } catch (ClassNotFoundException e) {
                isCardViewAvailable = false;
            }
        }

        return isCardViewAvailable;
    }

    /**
     * Helper method to run a map of action name to action values.
     *
     * @param actionValueMap The action value map.
     * @param situation The actions' situation.
     */
    private void runActions(Map<String, ActionValue> actionValueMap, @Action.Situation int situation) {
        if (actionValueMap == null) {
            return;
        }

        for (Map.Entry<String, ActionValue> entry : actionValueMap.entrySet()) {
            ActionRunRequest.createRequest(entry.getKey())
                            .setValue(entry.getValue())
                            .setSituation(situation)
                            .run();
        }
    }
}

