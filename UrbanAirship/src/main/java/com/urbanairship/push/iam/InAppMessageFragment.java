/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.push.iam;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Fragment;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.widget.ViewDragHelper;
import android.support.v7.widget.CardView;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.actions.Situation;
import com.urbanairship.push.notifications.NotificationActionButton;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;
import com.urbanairship.widget.SwipeDismissViewLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A fragment that displays an in-app message.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
public class InAppMessageFragment extends Fragment {

    private static Boolean isCardViewAvailable;

    /**
     * Listener for InAppMessageFragment events.
     */
    public interface Listener {
        /**
         * The fragment was resumed.
         *
         * @param fragment The resumed fragment.
         */
        public void onResume(InAppMessageFragment fragment);

        /**
         * The fragment was paused.
         *
         * @param fragment The paused fragment.
         */
        public void onPause(InAppMessageFragment fragment);

        /**
         * The fragment is finished displaying the in-app message.
         *
         * @param fragment The fragment.
         */
        public void onFinish(InAppMessageFragment fragment);
    }

    /**
     * Default primary color for in-app messages. The value is only used if the in-app message's
     * {@link InAppMessage#getPrimaryColor()} returns null.
     */
    public static final int DEFAULT_PRIMARY_COLOR = Color.WHITE;

    /**
     * Default secondary color for in-app messages. The value is only used if the in-app message's
     * {@link InAppMessage#getSecondaryColor()} returns null.
     */
    public static final int DEFAULT_SECONDARY_COLOR = Color.DKGRAY;

    /**
     * Default duration in milliseconds. The value is only used if the in-app message's
     * {@link InAppMessage#getDuration()} returns null.
     */
    public static final long DEFAULT_DURATION = 15000;

    private static final String MESSAGE = "message";
    private static final String DISMISS_ANIMATION = "dismiss_animation";
    private static final String DISMISSED = "dismissed";
    private static final String DISMISS_ON_RECREATE = "dismiss_on_recreate";

    private InAppMessage message;
    private boolean isDismissed;
    private Timer timer;
    private final List<Listener> listeners = new ArrayList<>();
    private boolean dismissOnRecreate;

    /**
     * Creates arguments for the InAppMessageFragment. Arguments must be set
     * after creating the initial fragment.
     *
     * @param message The associated in-app message.
     * @param dismissAnimation Resource ID of a fragment transition to run when the message is dismissed.
     * @return A bundle with the given arguments for creating the InAppMessageFragment.
     */
    public static Bundle createArgs(InAppMessage message, int dismissAnimation) {
        Bundle args = new Bundle();
        args.putParcelable(MESSAGE, message);
        args.putInt(DISMISS_ANIMATION, dismissAnimation);
        return args;
    }

    /**
     * Subscribe a listener for in-app message fragment events.
     *
     * @param listener An object implementing the
     * {@link com.urbanairship.push.iam.InAppMessageFragment.Listener } interface.
     */
    public final void addListener(Listener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Unsubscribe a listener for in-app message fragment events.
     *
     * @param listener An object implementing the
     * {@link com.urbanairship.push.iam.InAppMessageFragment.Listener } interface.
     */
    public final void removeListener(Listener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);

        this.setRetainInstance(true);

        this.message = getArguments().getParcelable(MESSAGE);
        this.isDismissed = savedInstance != null && savedInstance.getBoolean(DISMISSED, false);

        long duration = message.getDuration() == null ? DEFAULT_DURATION : message.getDuration();
        this.timer = new Timer(duration) {
            @Override
            protected void onFinish() {
                dismiss(true);
                ResolutionEvent resolutionEvent = ResolutionEvent.createTimedOutResolutionEvent(message, timer.getRunTime());
                UAirship.shared().getAnalytics().addEvent(resolutionEvent);
            }
        };

        /**
         * If we have saved state and our saved dismissOnRecreate does not match the value in the saved
         * state then we know the fragment was reconstructed.
         */
        if (savedInstance != null && savedInstance.getBoolean(DISMISS_ON_RECREATE, false) != dismissOnRecreate) {
            Logger.debug("InAppMessageFragment - Dismissing on recreate.");
            dismiss(true);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(DISMISSED, isDismissed);
        outState.putBoolean(DISMISS_ON_RECREATE, dismissOnRecreate);
    }

    @Override
    public void onResume() {
        super.onResume();
        timer.start();

        synchronized (listeners) {
            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onResume(this);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        timer.stop();

        synchronized (listeners) {
            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onPause(this);
            }
        }
    }

    @SuppressLint("NewAPI")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (message == null || message.getAlert() == null) {
            dismiss(false);
            return null;
        }

        int primaryColor = message.getPrimaryColor() == null ? DEFAULT_PRIMARY_COLOR : message.getPrimaryColor();
        int secondaryColor = message.getSecondaryColor() == null ? DEFAULT_SECONDARY_COLOR : message.getSecondaryColor();

        int layout = isCardViewAvailable() ? R.layout.ua_fragment_iam_card : R.layout.ua_fragment_iam;

        final SwipeDismissViewLayout view = (SwipeDismissViewLayout) inflater.inflate(layout, container, false);
        view.setListener(new SwipeDismissViewLayout.Listener() {
            @Override
            public void onDismissed(View view) {
                dismiss(false);

                ResolutionEvent resolutionEvent = ResolutionEvent.createUserDismissedResolutionEvent(message, timer.getRunTime());
                UAirship.shared().getAnalytics().addEvent(resolutionEvent);
            }

            @Override
            public void onDragStateChanged(View view, int state) {
                switch (state) {
                    case ViewDragHelper.STATE_DRAGGING:
                        timer.stop();
                        break;
                    case ViewDragHelper.STATE_IDLE:
                        timer.start();
                        break;
                }
            }
        });

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
        layoutParams.gravity = message.getPosition() == InAppMessage.POSITION_TOP ? Gravity.TOP : Gravity.BOTTOM;
        view.setLayoutParams(layoutParams);

        FrameLayout messageView = (FrameLayout) view.findViewById(R.id.in_app_message);

        if (checkCardViewDependencyAvailable()) {
            CardView cardView = (CardView) messageView;
            cardView.setCardBackgroundColor(primaryColor);
            cardView.setRadius(getResources().getDimension(R.dimen.iam_corner_radius));
            cardView.setCardElevation(getResources().getDimension(R.dimen.iam_elevation));
        } else {
            messageView.getBackground().setColorFilter(primaryColor, PorterDuff.Mode.MULTIPLY);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                messageView.setElevation(getResources().getDimension(R.dimen.iam_elevation));
            }
        }

        if (!message.getClickActionValues().isEmpty()) {
            messageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onMessageClick(v);
                }
            });
        } else {
            messageView.setClickable(false);
            messageView.setForeground(null);
        }

        TextView alertView = (TextView) view.findViewById(R.id.alert);
        alertView.setText(message.getAlert());
        alertView.setTextColor(secondaryColor);

        View buttonDivider = view.findViewById(R.id.action_divider);
        buttonDivider.setBackgroundColor(secondaryColor);

        View actionButtons = view.findViewById(R.id.action_buttons);
        NotificationActionButtonGroup group = UAirship.shared().getPushManager().getNotificationActionGroup(message.getButtonGroupId());
        if (group != null) {

            Resources r = getResources();
            int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, r.getDisplayMetrics());

            for (final NotificationActionButton actionButton : group.getNotificationActionButtons()) {
                Button button = (Button) inflater.inflate(R.layout.ua_iam_button, (ViewGroup) actionButtons, false);

                final Drawable drawable = getResources().getDrawable(actionButton.getIcon());
                drawable.setBounds(0, 0, size, size);
                drawable.setColorFilter(secondaryColor, PorterDuff.Mode.MULTIPLY);
                button.setCompoundDrawables(drawable, null, null, null);
                button.setText(actionButton.getLabel());
                button.setTextColor(secondaryColor);

                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        onButtonClick(view, actionButton);
                    }
                });

                ((ViewGroup) actionButtons).addView(button);
            }

        } else {
            actionButtons.setVisibility(View.GONE);
            buttonDivider.setVisibility(View.GONE);
        }

        ImageButton imageButton = (ImageButton) view.findViewById(R.id.close);
        if (imageButton != null) {
            final Drawable drawable = imageButton.getDrawable();
            if (drawable != null) {
                drawable.setColorFilter(secondaryColor, PorterDuff.Mode.MULTIPLY);
            }

            imageButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss(true);

                    ResolutionEvent resolutionEvent = ResolutionEvent.createUserDismissedResolutionEvent(message, timer.getRunTime());
                    UAirship.shared().getAnalytics().addEvent(resolutionEvent);
                }
            });
        }

        return view;
    }

    /**
     * Dismisses the fragment.
     *
     * @param animate {@code true} if the fragment should animate out, otherwise {@code false}.
     */
    public void dismiss(boolean animate) {
        timer.stop();

        if (isDismissed) {
            return;
        }

        synchronized (listeners) {
            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onFinish(this);
            }
        }

        isDismissed = true;

        if (getActivity() != null) {
            getActivity().getFragmentManager().beginTransaction()
                         .setCustomAnimations(0, animate ? getArguments().getInt(DISMISS_ANIMATION, 0) : 0)
                         .remove(this)
                         .commit();
        }
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
    public InAppMessage getMessage() {
        return message;
    }

    /**
     * Gets the dismiss animation resource ID.
     *
     * @return The dismiss animation resource ID.
     */
    public int getDismissAnimation() {
        return getArguments().getInt(DISMISS_ANIMATION, 0);
    }

    /**
     * Dismisses the fragment automatically if the fragment is reconstructed.
     * <p/>
     * Since fragments can be recreated from saved state (bundle) it will lose any saved fields that
     * are not saved in the bundle including things such as the listeners.
     *
     * @param dismissOnRecreate {@code true} to dismiss the fragment if the fragment is reconstructed,
     * otherwise {@code false}.
     */
    void setDismissOnRecreate(boolean dismissOnRecreate) {
        this.dismissOnRecreate = dismissOnRecreate;
    }

    /**
     * Called when the message body is clicked. Will dismiss the fragment and run
     * actions with the {@link InAppMessage#getClickActionValues()}.
     *
     * @param view The view that was clicked.
     */
    protected void onMessageClick(View view) {
        dismiss(true);

        runActions(message.getClickActionValues(), Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON);

        ResolutionEvent resolutionEvent = ResolutionEvent.createClickedResolutionEvent(message, timer.getRunTime());
        UAirship.shared().getAnalytics().addEvent(resolutionEvent);
    }

    /**
     * Called when a message button is clicked. Will dismiss the fragment and run
     * actions for the button's ID in {@link InAppMessage#getButtonActionValues(String)}.
     *
     * @param view The view that was clicked.
     * @param actionButton The associated button.
     */
    protected void onButtonClick(View view, NotificationActionButton actionButton) {
        Logger.info("In-app message button clicked: " + actionButton.getId());
        dismiss(true);

        Situation situation = actionButton.isForegroundAction() ? Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON :
                              Situation.BACKGROUND_NOTIFICATION_ACTION_BUTTON;

        runActions(message.getButtonActionValues(actionButton.getId()), situation);

        ResolutionEvent resolutionEvent = ResolutionEvent.createButtonClickedResolutionEvent(getActivity(), message, actionButton, timer.getRunTime());
        UAirship.shared().getAnalytics().addEvent(resolutionEvent);
    }

    /**
     * Helper method to check if the card view dependency is available or not.
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
    private void runActions(Map<String, ActionValue> actionValueMap, Situation situation) {
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

