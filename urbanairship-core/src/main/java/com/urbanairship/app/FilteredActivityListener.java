/* Copyright Airship and Contributors */

package com.urbanairship.app;

import android.app.Activity;
import android.os.Bundle;
import androidx.annotation.NonNull;

import com.urbanairship.Predicate;

/**
 * Activity listener that filters events with a predicate.
 */
public class FilteredActivityListener implements ActivityListener {

    private final ActivityListener listener;
    private final Predicate<Activity> filter;

    public FilteredActivityListener(@NonNull ActivityListener listener, @NonNull Predicate<Activity> filter) {
        this.listener = listener;
        this.filter = filter;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        if (filter.apply(activity)) {
            listener.onActivityCreated(activity, savedInstanceState);
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (filter.apply(activity)) {
            listener.onActivityStarted(activity);
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (filter.apply(activity)) {
            listener.onActivityResumed(activity);
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (filter.apply(activity)) {
            listener.onActivityPaused(activity);
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        if (filter.apply(activity)) {
            listener.onActivityStopped(activity);
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        if (filter.apply(activity)) {
            listener.onActivitySaveInstanceState(activity, outState);
        }
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (filter.apply(activity)) {
            listener.onActivityDestroyed(activity);
        }
    }

}
