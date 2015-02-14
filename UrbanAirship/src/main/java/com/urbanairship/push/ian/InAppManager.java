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

package com.urbanairship.push.ian;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.os.Build;
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

import java.lang.ref.WeakReference;

/**
 * This class is the primary interface for interacting with in app notifications.
 */
public class InAppManager extends BaseManager {

    // Preference data store keys
    private final static String PENDING_IN_APP_NOTIFICATION_KEY = "com.urbanairship.push.ian.PENDING_IN_APP_NOTIFICATION";
    private final static String DISPLAY_ASAP_KEY = "com.urbanairship.push.ian.DISPLAY_ASAP";
    private final static String AUTO_DISPLAY_ENABLED_KEY = "com.urbanairship.push.ian.AUTO_DISPLAY_ENABLED";

    private final static String IN_APP_TAG = "com.urbanairship.in_app_fragment";

    /**
     * The delay before attempting to show an InAppNotification when an activity resumes.
     */
    private static long ACTIVITY_RESUME_DELAY_MS = 3000;

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
    private InAppNotificationFragment currentFragment;
    private boolean autoDisplayPendingNotification;
    private InAppNotification currentNotification;

    // Runnable that we post on the main looper whenever we attempt to auto display a in app notification
    private final Runnable displayRunnable = new Runnable() {
        @Override
        public void run() {
            if (!autoDisplayPendingNotification && !isDisplayAsapEnabled() || !isAutoDisplayEnabled()) {
                return;
            }

            if (showPendingNotification(getCurrentActivity())) {
                autoDisplayPendingNotification = false;
            }
        }
    };


    /**
     * Default constructor.
     *
     * @param dataStore The preference data store.
     * @hide
     */
    public InAppManager(PreferenceDataStore dataStore) {
        this.dataStore = dataStore;
        handler = new Handler(Looper.getMainLooper());
        autoDisplayPendingNotification = isDisplayAsapEnabled();
    }

    /**
     * Sets if in app notifications should be displayed as soon as possible or only on app foregrounds.
     * <p/>
     * If a pending notification is already available, it will be displayed on the next time an activity
     * is resumed.
     *
     * @param enabled {@code true} to enable display in app notifications as soon as possible, otherwise
     * {@code false}.
     */
    public void setDisplayAsapEnabled(boolean enabled) {
        dataStore.put(DISPLAY_ASAP_KEY, enabled);

        if (enabled) {
            autoDisplayPendingNotification = true;
        }
    }

    /**
     * Checks in app notifications should be displayed as soon as possible or only on app foregrounds.
     *
     * @return {@code true} if in app notifications as soon as possible is enabled, otherwise
     * {@code false}.
     */
    public boolean isDisplayAsapEnabled() {
        return dataStore.getBoolean(DISPLAY_ASAP_KEY, false);
    }


    /**
     * Sets if in app notifications should be displayed automatically.
     *
     * @param enabled {@code true} to display in app notifications automatically, otherwise {@code false}.
     */
    public void setAutoDisplayEnabled(boolean enabled) {
        dataStore.put(AUTO_DISPLAY_ENABLED_KEY, enabled);
    }

    /**
     * Checks if auto displaying in app notifications is enabled.
     *
     * @return {@code true} if displaying in app notifications automatically is enabled, otherwise
     * {@code false}.
     */
    public boolean isAutoDisplayEnabled() {
        return dataStore.getBoolean(AUTO_DISPLAY_ENABLED_KEY, true);
    }

    /**
     * Sets the pending notification.
     *
     * @param notification The InAppNotification.
     */
    public void setPendingNotification(InAppNotification notification) {
        if (notification == null) {
            dataStore.remove(PENDING_IN_APP_NOTIFICATION_KEY);
        } else {
            dataStore.put(PENDING_IN_APP_NOTIFICATION_KEY, notification.toJsonValue().toString());

            if (isDisplayAsapEnabled()) {
                autoDisplayPendingNotification = true;
                handler.removeCallbacks(displayRunnable);
                handler.post(displayRunnable);
            }
        }
    }

    /**
     * Gets the pending notification.
     *
     * @return The pending InAppNotification.
     */
    public InAppNotification getPendingNotification() {
        String payload = dataStore.getString(PENDING_IN_APP_NOTIFICATION_KEY, null);
        if (payload != null) {
            try {
                return InAppNotification.parseJson(payload);
            } catch (JsonException e) {
                Logger.error("InAppManager - Failed to read pending in app notification: " + payload, e);
                setPendingNotification(null);
            }
        }

        return null;
    }

    /**
     * Shows the pending notification.
     *
     * @param activity The current activity.
     * @return {@code true} if a {@link InAppNotificationFragment} was added and displayed in the
     * activity, otherwise {@code false}.
     */
    @TargetApi(14)
    public boolean showPendingNotification(Activity activity) {
        return showPendingNotification(activity, android.R.id.content);
    }

    /**
     * Shows the pending notification in a specified container ID.
     *
     * @param activity The current activity.
     * @param containerId An ID of a container in the activity's view to add the
     * {@link InAppNotificationFragment}.
     * @return {@code true} if a {@link InAppNotificationFragment} was added and displayed in the
     * activity, otherwise {@code false}.
     */
    @TargetApi(14)
    public boolean showPendingNotification(Activity activity, int containerId) {
        InAppNotification pending = getPendingNotification();

        if (pending == null) {
            return false;
        }

        int enter, exit;
        if (pending.getPosition() == InAppNotification.POSITION_TOP) {
            enter = R.animator.ua_ian_slide_in_top;
            exit = R.animator.ua_ian_slide_out_top;
        } else {
            enter = R.animator.ua_ian_slide_in_bottom;
            exit = R.animator.ua_ian_slide_out_bottom;
        }

        return showNotification(pending, activity, containerId, enter, exit);
    }


    /**
     * Shows the pending notification in a specified container ID and fragment animations.
     * <p/>
     * Note: The animations must refer to API 11 object animators. View animators will result in a
     * runtime exception.
     *
     * @param activity The current activity.
     * @param containerId An ID of a container in the activity's view to add the
     * {@link InAppNotificationFragment}.
     * @param enterAnimation The animation resource to run when the {@link InAppNotificationFragment}
     * enters the view.
     * @param exitAnimation The animation resource to run when the {@link InAppNotificationFragment}
     * exits the view.
     * @return {@code true} if a {@link InAppNotificationFragment} was added and displayed in the
     * activity, otherwise {@code false}.
     */
    @TargetApi(14)
    public boolean showPendingNotification(Activity activity, int containerId, int enterAnimation, int exitAnimation) {
        return showNotification(getPendingNotification(), activity, containerId, enterAnimation, exitAnimation);
    }

    /**
     * Shows an {@link InAppNotification}.
     *
     * @param notification The InAppNotification.
     * @param activity The current activity.
     * @param containerId An ID of a container in the activity's view to add the
     * {@link InAppNotificationFragment}.
     * @param enterAnimation The animation resource to run when the {@link InAppNotificationFragment}
     * enters the view.
     * @param exitAnimation The animation resource to run when the {@link InAppNotificationFragment}
     * exits the view.
     * @return {@code true} if a {@link InAppNotificationFragment} was added and displayed in the
     * activity, otherwise {@code false}.
     */
    @TargetApi(14)
    private boolean showNotification(InAppNotification notification, Activity activity, int containerId, int enterAnimation, int exitAnimation) {
        if (activity == null || notification == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT < 14) {
            Logger.error("InAppManager - Unable to show InAppNotification on Android versions older than API 14 (Ice Cream Sandwich).");
            return false;
        }

        if (activity.isFinishing()) {
            Logger.error("InAppManager - Unable to display InAppNotification for an activity that is finishing.");
            return false;
        }

        if (Looper.getMainLooper() != Looper.myLooper()) {
            Logger.error("InAppManager - Show notification must be called on the main thread.");
            return false;
        }

        if (currentFragment != null) {
            Logger.debug("InAppManager - InAppNotification already displayed.");
            return false;
        }

        Logger.info("InAppManager - Displaying InAppNotification.");
        try {
            currentFragment = InAppNotificationFragment.newInstance(notification, exitAnimation);
            currentNotification = notification;

            activity.getFragmentManager().beginTransaction()
                    .setCustomAnimations(enterAnimation, 0)
                    .add(containerId, currentFragment, IN_APP_TAG)
                    .commit();

            return true;
        } catch (IllegalStateException e) {
            Logger.debug("InAppManager - Failed to display InAppNotification.", e);
            return false;
        }
    }

    /**
     * Helper method to get the current, resumed activity.
     *
     * @return The current, resumed activity or null.
     */
    private Activity getCurrentActivity() {
        return activityReference == null ? null : activityReference.get();
    }

    // InAppNotificationFragment hooks

    /**
     * Called when an InAppNotification is finished displaying.
     *
     * @param notification The InAppNotification.
     */
    void onInAppNotificationFinished(InAppNotification notification) {
        if (notification != null && notification.equals(getPendingNotification())) {
            setPendingNotification(null);
        }

        if (notification != null && notification.equals(currentNotification)) {
            currentNotification = null;
        }
    }

    /**
     * Called from an {@link InAppNotificationFragment#onResume()}.
     *
     * @param fragment The InAppNotificationFragment.
     */
    @TargetApi(11)
    void onInAppNotificationFragmentResumed(InAppNotificationFragment fragment) {
        if (currentFragment != null && currentFragment != fragment) {
            fragment.dismiss(false);
            return;
        }

        if (currentNotification == null || !currentNotification.equals(fragment.getNotification())) {
            fragment.dismiss(false);
        } else {
            currentFragment = fragment;
        }
    }

    /**
     * Called from an {@link InAppNotificationFragment#onPause()}.
     *
     * @param fragment The InAppNotificationFragment.
     */
    @TargetApi(11)
    void onInAppNotificationFragmentPaused(InAppNotificationFragment fragment) {
        if (fragment != currentFragment) {
            return;
        }

        currentFragment = null;
        if (!fragment.isDismissed() && fragment.getActivity().isFinishing()) {
            /*
             * If the InAppNotificationFragment's activity is finishing, but the notification is still
             * displaying, show the notification on the next activity.
             */
            autoDisplayPendingNotification = true;
        }
    }

    // Life cycle hooks

    /**
     * Called when the app is foregrounded.
     */
    void onForeground() {
        Logger.verbose("InAppManager - App foregrounded.");
        InAppNotification pending = getPendingNotification();
        if ((currentNotification == null && pending != null) || (pending != null && !pending.equals(currentNotification))) {
            currentNotification = null;
            autoDisplayPendingNotification = true;
            handler.removeCallbacks(displayRunnable);
            handler.postDelayed(displayRunnable, ACTIVITY_RESUME_DELAY_MS);
        }
    }

    /**
     * Called when an activity is paused.
     *
     * @param activity The paused activity.
     */
    void onActivityPaused(Activity activity) {
        Logger.verbose("InAppManager - Activity paused: " + activity);
        activityReference = null;
        handler.removeCallbacks(displayRunnable);
    }

    /**
     * Called when an activity is resumed.
     *
     * @param activity The resumed activity.
     */
    void onActivityResumed(Activity activity) {
        Logger.verbose("InAppManager - Activity resumed: " + activity);
        activityReference = new WeakReference<>(activity);
        handler.removeCallbacks(displayRunnable);

        if (autoDisplayPendingNotification) {
            handler.postDelayed(displayRunnable, ACTIVITY_RESUME_DELAY_MS);
        }
    }

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
                            UAirship.shared().getInAppManager().onForeground();
                        } else {
                            UAirship.shared(new UAirship.OnReadyCallback() {
                                @Override
                                public void onAirshipReady(UAirship airship) {
                                    UAirship.shared().getInAppManager().onForeground();
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
                            UAirship.shared().getInAppManager().onActivityResumed(activity);
                        }
                    });
                }

                @Override
                public void onActivityPaused(final Activity activity) {
                    if (activityResumedOperation != null && !activityResumedOperation.isDone()) {
                        activityResumedOperation.cancel();
                        return;
                    }

                    UAirship.shared().getInAppManager().onActivityPaused(activity);
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
