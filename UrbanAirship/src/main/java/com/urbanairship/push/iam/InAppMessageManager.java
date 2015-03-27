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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.urbanairship.BaseManager;
import com.urbanairship.Cancelable;
import com.urbanairship.LifeCycleCallbacks;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.json.JsonException;
import com.urbanairship.util.ManifestUtils;
import com.urbanairship.util.UAStringUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is the primary interface for interacting with in-app messages.
 */
public class InAppMessageManager extends BaseManager {

    /**
     * Listener for in-app messaging receive and display events.
     */
    public interface Listener {

        /**
         * Called when a new pending message is available.
         * @param message The in-app message.
         */
        public void onPendingMessageAvailable(InAppMessage message);

        /**
         * Called when an InAppMessage is being displayed
         * @param fragment The InAppMessageFragment that will display the in-app message.
         * @param message The in-app message.
         */
        public void onDisplay(InAppMessageFragment fragment, InAppMessage message);
    }

    // Preference data store keys
    private final static String KEY_PREFIX = "com.urbanairship.push.iam.";
    private final static String PENDING_IN_APP_MESSAGE_KEY = KEY_PREFIX + "PENDING_IN_APP_MESSAGE";
    private final static String DISPLAY_ASAP_KEY = KEY_PREFIX + "DISPLAY_ASAP";
    private final static String AUTO_DISPLAY_ENABLED_KEY = KEY_PREFIX + "AUTO_DISPLAY_ENABLED";
    private final static String LAST_DISPLAYED_ID_KEY = KEY_PREFIX + "LAST_DISPLAYED_ID";

    private final static String IN_APP_TAG = "com.urbanairship.in_app_fragment";

    /**
     * Activity metadata key to exclude an activity from automatically displaying an in-app message.
     */
    public static final String EXCLUDE_FROM_AUTO_SHOW = "com.urbanairship.push.iam.EXCLUDE_FROM_AUTO_SHOW";

    /**
     * The delay before attempting to show an in-app message when an activity resumes.
     */
    private static long DEFAULT_ACTIVITY_RESUME_DELAY_MS = 3000;

    /**
     * A small delay that allows activity configuration changes to happen before we consider the app
     * backgrounded.
     */
    private static long BACKGROUND_DELAY_MS = 500;

    // Static properties for lifecycle callbacks
    private static LifeCycleCallbacks lifeCycleCallbacks;
    private static Cancelable activityResumedOperation;
    private static int activityCount = 0;
    private static boolean isForeground = false;

    private final PreferenceDataStore dataStore;
    private final Handler handler;

    private WeakReference<Activity> activityReference;
    private InAppMessageFragment currentFragment;
    private boolean autoDisplayPendingMessage;
    private InAppMessage currentMessage;
    private final List<Listener> listeners = new ArrayList<>();
    private final Object pendingMessageLock = new Object();
    private InAppMessageFragmentFactory fragmentFactory;
    private long autoDisplayDelayMs;


    // Runnable that we post on the main looper whenever we attempt to auto display a in-app message
    private final Runnable displayRunnable = new Runnable() {
        @Override
        public void run() {
            if (!autoDisplayPendingMessage && !isDisplayAsapEnabled() || !isAutoDisplayEnabled()) {
                return;
            }

            if (showPendingMessage(getCurrentActivity())) {
                autoDisplayPendingMessage = false;
            }
        }
    };

    /**
     * Default constructor.
     *
     * @param dataStore The preference data store.
     * @hide
     */
    public InAppMessageManager(PreferenceDataStore dataStore) {
        this.dataStore = dataStore;
        autoDisplayDelayMs = DEFAULT_ACTIVITY_RESUME_DELAY_MS;
        handler = new Handler(Looper.getMainLooper());
        autoDisplayPendingMessage = isDisplayAsapEnabled();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            fragmentFactory = new InAppMessageFragmentFactory() {
                @Override
                public InAppMessageFragment createFragment(InAppMessage message) {
                    return new InAppMessageFragment();
                }
            };
        }
    }

    @Override
    protected void init() {
        InAppMessage pending = getPendingMessage();
        if (pending != null && pending.isExpired()) {
            Logger.debug("InAppMessageManager - pending in-app message expired.");
            ResolutionEvent resolutionEvent = ResolutionEvent.createExpiredResolutionEvent(pending);
            UAirship.shared().getAnalytics().addEvent(resolutionEvent);
            setPendingMessage(null);
        }
    }

    /**
     * Sets the default delay before an in-app message is automatically displayed when an activity
     * is resumed.
     * @param milliseconds The auto display delay in milliseconds.
     */
    public void setAutoDisplayDelay(long milliseconds) {
        this.autoDisplayDelayMs = milliseconds;
    }

    /**
     * Gets the delay in milliseconds before an in-app message is automatically displayed when
     * an activity is resumed.
     * @return The auto display delay in milliseconds.
     */
    public long getAutoDisplayDelay() {
        return this.autoDisplayDelayMs;
    }
    /**
     * Sets if in-app messages should be displayed as soon as possible or only on app foregrounds.
     * <p/>
     * If a pending in-app message is already available, it will be displayed on the next time an activity
     * is resumed.
     *
     * @param enabled {@code true} to enable display in-app messages as soon as possible, otherwise
     * {@code false}.
     */
    public void setDisplayAsapEnabled(boolean enabled) {
        dataStore.put(DISPLAY_ASAP_KEY, enabled);

        if (enabled) {
            autoDisplayPendingMessage = true;
        }
    }

    /**
     * Checks in-app messages should be displayed as soon as possible or only on app foregrounds.
     *
     * @return {@code true} if in-app messages as soon as possible is enabled, otherwise

     * {@code false}.
     */
    public boolean isDisplayAsapEnabled() {
        return dataStore.getBoolean(DISPLAY_ASAP_KEY, false);
    }

    /**
     * Sets if in-app messages should be displayed automatically.
     *
     * @param enabled {@code true} to display in-app messages automatically, otherwise {@code false}.
     */
    public void setAutoDisplayEnabled(boolean enabled) {
        dataStore.put(AUTO_DISPLAY_ENABLED_KEY, enabled);
    }

    /**
     * Checks if auto displaying in-app messages is enabled.
     *
     * @return {@code true} if displaying in-app messages automatically is enabled, otherwise
     * {@code false}.
     */
    public boolean isAutoDisplayEnabled() {
        return dataStore.getBoolean(AUTO_DISPLAY_ENABLED_KEY, true);
    }

    /**
     * Sets the pending in-app message.
     *
     * @param message The in-app message.
     */
    public void setPendingMessage(final InAppMessage message) {
        synchronized (pendingMessageLock) {
            if (message == null) {
                dataStore.remove(PENDING_IN_APP_MESSAGE_KEY);
            } else {
                InAppMessage previous = getPendingMessage();
                if (message.equals(previous)) {
                    return;
                }

                // Notify the listener on the main thread of the new pending message
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (listeners) {
                            for (Listener listener : listeners) {
                                listener.onPendingMessageAvailable(message);
                            }
                        }
                    }
                });

                dataStore.put(PENDING_IN_APP_MESSAGE_KEY, message.toJsonValue().toString());

                if (currentMessage == null && previous != null) {
                    Logger.debug("InAppMessageManager - pending in-app message replaced.");
                    ResolutionEvent resolutionEvent = ResolutionEvent.createReplacedResolutionEvent(previous, message);
                    UAirship.shared().getAnalytics().addEvent(resolutionEvent);
                }

                if (isDisplayAsapEnabled()) {
                    autoDisplayPendingMessage = true;
                    handler.removeCallbacks(displayRunnable);
                    handler.post(displayRunnable);
                }


            }
        }
    }

    /**
     * Gets the pending in-app message.
     *
     * @return The pending in-app message.
     */
    public InAppMessage getPendingMessage() {
        synchronized (pendingMessageLock) {
            String payload = dataStore.getString(PENDING_IN_APP_MESSAGE_KEY, null);
            if (payload != null) {
                try {
                    return InAppMessage.parseJson(payload);
                } catch (JsonException e) {
                    Logger.error("InAppMessageManager - Failed to read pending in-app message: " + payload, e);
                    setPendingMessage(null);
                }
            }

            return null;
        }
    }

    /**
     * Gets the in-app message that is in the process of displaying.
     *
     * @return The current in-app message.
     * @hide
     */
    public InAppMessage getCurrentMessage() {
        return currentMessage;
    }

    /**
     * Shows the pending in-app message.
     *
     * @param activity The current activity.
     * @return {@code true} if a {@link InAppMessageFragment} was added and displayed in the
     * activity, otherwise {@code false}.
     */
    @TargetApi(14)
    public boolean showPendingMessage(Activity activity) {
        return showPendingMessage(activity, android.R.id.content);
    }

    /**
     * Shows the pending in-app message in a specified container ID.
     *
     * @param activity The current activity.
     * @param containerId An ID of a container in the activity's view to add the
     * {@link InAppMessageFragment}.
     * @return {@code true} if a {@link InAppMessageFragment} was added and displayed in the
     * activity, otherwise {@code false}.
     */
    @TargetApi(14)
    public boolean showPendingMessage(Activity activity, int containerId) {
        synchronized (pendingMessageLock) {
            InAppMessage pending = getPendingMessage();

            if (activity == null || pending == null) {
                return false;
            }

            int enter, exit;
            if (pending.getPosition() == InAppMessage.POSITION_TOP) {
                enter = R.animator.ua_iam_slide_in_top;
                exit = R.animator.ua_iam_slide_out_top;
            } else {
                enter = R.animator.ua_iam_slide_in_bottom;
                exit = R.animator.ua_iam_slide_out_bottom;
            }

            return showPendingMessage(activity, containerId, enter, exit);
        }
    }

    /**
     * Shows the pending in-app message in a specified container ID and fragment animations.
     * <p/>
     * Note: The animations must refer to API 11 object animators. View animators will result in a
     * runtime exception.
     *
     * @param activity The current activity.
     * @param containerId An ID of a container in the activity's view to add the
     * {@link InAppMessageFragment}.
     * @param enterAnimation The animation resource to run when the {@link InAppMessageFragment}
     * enters the view.
     * @param exitAnimation The animation resource to run when the {@link InAppMessageFragment}
     * exits the view.
     * @return {@code true} if a {@link InAppMessageFragment} was added and displayed in the
     * activity, otherwise {@code false}.
     */
    @TargetApi(14)
    public boolean showPendingMessage(Activity activity, int containerId, int enterAnimation, int exitAnimation) {
        final InAppMessage pending;

        synchronized (pendingMessageLock) {
            pending = getPendingMessage();
            // Expired event
            if (pending != null && pending.isExpired()) {
                Logger.debug("InAppMessageManager - Unable to display pending in-app message. Message has expired.");
                ResolutionEvent resolutionEvent = ResolutionEvent.createExpiredResolutionEvent(pending);
                UAirship.shared().getAnalytics().addEvent(resolutionEvent);
                setPendingMessage(null);
                return false;
            }
        }

        if (activity == null || pending == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT < 14) {
            Logger.error("InAppMessageManager - Unable to show in-app messages on Android versions older than API 14 (Ice Cream Sandwich).");
            return false;
        }

        if (activity.isFinishing()) {
            Logger.error("InAppMessageManager - Unable to display in-app messages for an activity that is finishing.");
            return false;
        }

        if (Looper.getMainLooper() != Looper.myLooper()) {
            Logger.error("InAppMessageManager - Show message must be called on the main thread.");
            return false;
        }

        if (currentFragment != null) {
            Logger.debug("InAppMessageManager - An in-app message is already displayed.");
            return false;
        }

        // Add a display event if the last displayed id does not match the current message
        if (!UAStringUtil.equals(pending.getId(), dataStore.getString(LAST_DISPLAYED_ID_KEY, null))) {
            Logger.debug("InAppMessageManager - Displaying pending message: " + pending + " for first time.");
            DisplayEvent displayEvent = new DisplayEvent(pending);
            UAirship.shared().getAnalytics().addEvent(displayEvent);
            dataStore.put(LAST_DISPLAYED_ID_KEY, pending.getId());
        }

        if (currentMessage != null && currentMessage.equals(pending)) {
            // Replaced
            ResolutionEvent resolutionEvent = ResolutionEvent.createReplacedResolutionEvent(currentMessage, pending);
            UAirship.shared().getAnalytics().addEvent(resolutionEvent);
        }

        Logger.info("InAppMessageManager - Displaying in-app message.");
        try {

            InAppMessageFragmentFactory factory = getFragmentFactory();
            if (factory == null) {
                Logger.error("InAppMessageManager - InAppMessageFragmentFactory is null, unable to display an in-app message.");
                return false;
            }

            currentFragment = factory.createFragment(pending);
            if (currentFragment == null) {
                Logger.error("InAppMessageManager - InAppMessageFragmentFactory returned a null fragment, unable to display an in-app message.");
                return false;
            }

            Bundle args = InAppMessageFragment.createArgs(pending, exitAnimation);
            if (currentFragment.getArguments() != null) {
                args.putAll(currentFragment.getArguments());
            }

            currentFragment.setArguments(args);

            currentFragment.addListener(fragmentListener);
            currentFragment.setDismissOnRecreate(true);
            currentMessage = pending;

            synchronized (listeners) {
                for (Listener listener : listeners) {
                    listener.onDisplay(currentFragment, pending);
                }
            }

            activity.getFragmentManager().beginTransaction()
                    .setCustomAnimations(enterAnimation, 0)
                    .add(containerId, currentFragment, IN_APP_TAG)
                    .commit();

            return true;
        } catch (IllegalStateException e) {
            Logger.debug("InAppMessageManager - Failed to display in-app message.", e);
            return false;
        }

    }

    /**
     * Subscribe a listener for in-app message events.
     *
     * @param listener An object implementing the
     * {@link com.urbanairship.push.iam.InAppMessageManager.Listener } interface.
     */
    public void addListener(Listener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Unsubscribe a listener for in-app message events.
     *
     * @param listener An object implementing the
     * {@link com.urbanairship.push.iam.InAppMessageManager.Listener } interface.
     */
    public void removeListener(Listener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Sets the the in-app message fragment factory. The factory can be used to provide
     * a customized in-app message fragment to provide a different look and feel for in-app messages.
     *
     * @param factory The InAppMessageFragmentFactory.
     */
    public void setFragmentFactory(InAppMessageFragmentFactory factory) {
        this.fragmentFactory = factory;
    }

    /**
     * Returns the current in-app message fragment factory.
     * @return The current in-app message fragment factory.
     */
    public InAppMessageFragmentFactory getFragmentFactory() {
        return fragmentFactory;
    }

    /**
     * Helper method to get the current, resumed activity.
     *
     * @return The current, resumed activity or null.
     */
    private Activity getCurrentActivity() {
        return activityReference == null ? null : activityReference.get();
    }

    // Life cycle hooks

    /**
     * Called when the app is foregrounded.
     */
    void onForeground() {
        Logger.verbose("InAppMessageManager - App foregrounded.");
        InAppMessage pending = getPendingMessage();
        if ((currentMessage == null && pending != null) || (pending != null && !pending.equals(currentMessage))) {

            if (currentMessage != null) {
                // Replaced
                ResolutionEvent resolutionEvent = ResolutionEvent.createReplacedResolutionEvent(currentMessage, pending);
                UAirship.shared().getAnalytics().addEvent(resolutionEvent);
            }

            currentMessage = null;
            autoDisplayPendingMessage = true;
            handler.removeCallbacks(displayRunnable);
            handler.postDelayed(displayRunnable, autoDisplayDelayMs);
        }
    }

    /**
     * Called when an activity is paused.
     *
     * @param activity The paused activity.
     */
    void onActivityPaused(Activity activity) {
        Logger.verbose("InAppMessageManager - Activity paused: " + activity);
        activityReference = null;
        handler.removeCallbacks(displayRunnable);
    }

    /**
     * Called when an activity is resumed.
     *
     * @param activity The resumed activity.
     */
    void onActivityResumed(Activity activity) {
        Logger.verbose("InAppMessageManager - Activity resumed: " + activity);

        ActivityInfo info = ManifestUtils.getActivityInfo(activity.getClass());
        if (info != null && info.metaData != null && info.metaData.getBoolean(EXCLUDE_FROM_AUTO_SHOW, false)) {
            Logger.verbose("InAppMessageManager - Activity contains metadata to exclude it from auto showing an in-app message");
            return;
        }

        activityReference = new WeakReference<>(activity);
        handler.removeCallbacks(displayRunnable);

        if (autoDisplayPendingMessage) {
            handler.postDelayed(displayRunnable, autoDisplayDelayMs);
        }
    }

    private final InAppMessageFragment.Listener fragmentListener = new InAppMessageFragment.Listener() {
        @Override
        public void onResume(InAppMessageFragment fragment) {
            Logger.verbose("InAppMessageManager - InAppMessageFragment resumed: " + fragment);

            if (currentFragment != null && currentFragment != fragment) {
                Logger.debug("InAppMessageManager - Dismissing " + fragment + " because it is no longer the current fragment.");
                fragment.dismiss(false);
                return;
            }

            if (currentMessage == null || !currentMessage.equals(fragment.getMessage())) {
                Logger.debug("InAppMessageManager - Dismissing " + fragment + " because its message is no longer current.");
                fragment.dismiss(false);
            } else {
                currentFragment = fragment;
            }
        }

        @Override
        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        public void onPause(InAppMessageFragment fragment) {
            Logger.verbose("InAppMessageManager - InAppMessageFragment paused: " + fragment);
            if (fragment != currentFragment) {
                return;
            }

            currentFragment = null;
            if (!fragment.isDismissed() && fragment.getActivity().isFinishing()) {

                Logger.verbose("InAppMessageManager - InAppMessageFragment's activity is finishing: " + fragment);

                /*
                 * If the InAppMessageFragment's activity is finishing, but the message is still
                 * displaying, show the message on the next activity.
                 */
                autoDisplayPendingMessage = true;
            }
        }

        @Override
        public void onFinish(InAppMessageFragment fragment) {
            Logger.verbose("InAppMessageManager - InAppMessageFragment finished: " + fragment);

            InAppMessage message = fragment.getMessage();

            synchronized (pendingMessageLock) {
                if (message != null && message.equals(getPendingMessage())) {
                    setPendingMessage(null);
                }
            }

            if (message != null && message.equals(currentMessage)) {
                currentMessage = null;
            }
        }
    };

    /**
     * Registers life cycle callbacks.
     *
     * @param application The application.
     * @hide
     */
    @TargetApi(14)
    public static void registerLifeCycleCallbacks(Application application) {
        if (lifeCycleCallbacks == null) {
            lifeCycleCallbacks = new LifeCycleCallbacks(application) {
                @Override
                public void onActivityStarted(final Activity activity) {
                    activityCount++;

                    if (!isForeground) {
                        isForeground = true;
                        if (UAirship.isFlying()) {
                            UAirship.shared().getInAppMessageManager().onForeground();
                        } else {
                            UAirship.shared(new UAirship.OnReadyCallback() {
                                @Override
                                public void onAirshipReady(UAirship airship) {
                                    UAirship.shared().getInAppMessageManager().onForeground();
                                }
                            });
                        }
                    }
                }

                @Override
                public void onActivityStopped(final Activity activity) {
                    activityCount--;

                    if (activityCount == 0) {
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (activityCount == 0) {
                                    isForeground = false;
                                }
                            }
                        }, BACKGROUND_DELAY_MS);
                    }
                }

                @Override
                public void onActivityResumed(final Activity activity) {
                    activityResumedOperation = UAirship.shared(new UAirship.OnReadyCallback() {
                        @Override
                        public void onAirshipReady(UAirship airship) {
                            UAirship.shared().getInAppMessageManager().onActivityResumed(activity);
                        }
                    });
                }

                @Override
                public void onActivityPaused(final Activity activity) {
                    if (activityResumedOperation != null && !activityResumedOperation.isDone()) {
                        activityResumedOperation.cancel();
                        return;
                    }

                    UAirship.shared().getInAppMessageManager().onActivityPaused(activity);
                }
            };

            lifeCycleCallbacks.register();
        }
    }

    /**
     * Unregisters the life cycle callbacks.
     *
     * @hide
     */
    @TargetApi(14)
    public static void unregisterLifeCycleCallbacks() {
        if (lifeCycleCallbacks != null) {
            lifeCycleCallbacks.unregister();
        }
    }
}
