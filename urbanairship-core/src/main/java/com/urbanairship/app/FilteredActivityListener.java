/* Copyright Airship and Contributors */

package com.urbanairship.app;

import android.app.Activity;
import android.os.Bundle;

import com.urbanairship.Predicate;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        if (filter.apply(activity)) {
            listener.onActivityCreated(activity, savedInstanceState);
        }
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        if (filter.apply(activity)) {
            listener.onActivityStarted(activity);
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (filter.apply(activity)) {
            listener.onActivityResumed(activity);
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (filter.apply(activity)) {
            listener.onActivityPaused(activity);
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        if (filter.apply(activity)) {
            listener.onActivityStopped(activity);
        }
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        if (filter.apply(activity)) {
            listener.onActivitySaveInstanceState(activity, outState);
        }
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if (filter.apply(activity)) {
            listener.onActivityDestroyed(activity);
        }
    }

}
