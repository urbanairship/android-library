/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;

import com.urbanairship.Logger;
import com.urbanairship.Predicate;
import com.urbanairship.app.ActivityListener;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.ApplicationListener;
import com.urbanairship.app.FilteredActivityListener;
import com.urbanairship.app.ForwardingActivityListener;
import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.util.ManifestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

/**
 * Activity monitor that filters out any activities with {@link #EXCLUDE_FROM_AUTO_SHOW} metadata.
 */
public class InAppActivityMonitor implements ActivityMonitor {

    /**
     * Metadata an app can use to prevent an in-app message from showing on a specific activity.
     */
    @NonNull
    public final static String EXCLUDE_FROM_AUTO_SHOW = "com.urbanairship.push.iam.EXCLUDE_FROM_AUTO_SHOW";

    private static InAppActivityMonitor shared;
    private final ActivityMonitor globalActivityMonitor;

    private final Set<Class> allowedActivities = new HashSet<>();
    private final Set<Class> ignoredActivities = new HashSet<>();

    private final Predicate<Activity> activityPredicate = new Predicate<Activity>() {
        @Override
        public boolean apply(Activity activity) {
            if (allowedActivities.contains(activity.getClass())) {
                return true;
            }

            if (ignoredActivities.contains(activity.getClass())) {
                return false;
            }

            if (shouldIgnoreActivity(activity)) {
                ignoredActivities.add(activity.getClass());
                return false;
            }

            allowedActivities.add(activity.getClass());
            return true;
        }
    };

    private final ForwardingActivityListener forwardingActivityListener;
    private final FilteredActivityListener filteredActivityListener;

    private InAppActivityMonitor(@NonNull ActivityMonitor globalActivityMonitor) {
        this.globalActivityMonitor = globalActivityMonitor;
        this.forwardingActivityListener = new ForwardingActivityListener();
        this.filteredActivityListener = new FilteredActivityListener(forwardingActivityListener, activityPredicate);
    }

    /**
     * Gets the shared in-app activity monitor instance.
     *
     * @param context The application context.
     * @return The shared in-app activity monitor instance.
     */
    @NonNull
    public static InAppActivityMonitor shared(@NonNull Context context) {
        if (shared == null) {
            synchronized (InAppActivityMonitor.class) {
                if (shared == null) {
                    shared = new InAppActivityMonitor(GlobalActivityMonitor.shared(context));
                    shared.init();
                }
            }
        }

        return shared;
    }

    private void init() {
        this.globalActivityMonitor.addActivityListener(filteredActivityListener);
    }

    @Override
    public void addActivityListener(@NonNull ActivityListener listener) {
        forwardingActivityListener.addListener(listener);
    }

    @Override
    public void removeActivityListener(@NonNull ActivityListener listener) {
        forwardingActivityListener.removeListener(listener);
    }

    @Override
    public void addApplicationListener(@NonNull ApplicationListener listener) {
        globalActivityMonitor.addApplicationListener(listener);
    }

    @Override
    public void removeApplicationListener(@NonNull ApplicationListener listener) {
        globalActivityMonitor.removeApplicationListener(listener);
    }

    @Override
    public boolean isAppForegrounded() {
        return globalActivityMonitor.isAppForegrounded();
    }

    @NonNull
    @MainThread
    @Override
    public List<Activity> getResumedActivities() {
        return globalActivityMonitor.getResumedActivities(activityPredicate);
    }

    @NonNull
    @MainThread
    @Override
    public List<Activity> getResumedActivities(@NonNull final Predicate<Activity> filter) {
        return globalActivityMonitor.getResumedActivities(new Predicate<Activity>() {
            @Override
            public boolean apply(Activity object) {
                return activityPredicate.apply(object) && filter.apply(object);
            }
        });
    }

    /**
     * Helper method to check if the activity is marked as do not use.
     *
     * @param activity The activity.
     * @return {@code true} if the activity should be ignored for in-app messaging, otherwise {@code false}.
     */
    private boolean shouldIgnoreActivity(Activity activity) {
        ActivityInfo info = ManifestUtils.getActivityInfo(activity.getClass());
        if (info != null && info.metaData != null && info.metaData.getBoolean(EXCLUDE_FROM_AUTO_SHOW, false)) {
            Logger.verbose("Activity contains metadata to exclude it from auto showing an in-app message");
            return true;
        }

        return false;
    }

}
