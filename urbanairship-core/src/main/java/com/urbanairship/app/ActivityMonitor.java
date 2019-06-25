/* Copyright Airship and Contributors */

package com.urbanairship.app;

import android.app.Activity;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.urbanairship.Predicate;

import java.util.List;

/**
 * Activity Monitor.
 */
public interface ActivityMonitor {

    /**
     * Adds an activity listener.
     *
     * @param listener The added listener.
     */
    void addActivityListener(@NonNull ActivityListener listener);

    /**
     * Removes an activity listener.
     *
     * @param listener The removed listener.
     */
    void removeActivityListener(@NonNull ActivityListener listener);

    /**
     * Adds an application listener.
     *
     * @param listener The added listener.
     */
    void addApplicationListener(@NonNull ApplicationListener listener);

    /**
     * Removes an application listener.
     *
     * @param listener The removed listener.
     */
    void removeApplicationListener(@NonNull ApplicationListener listener);

    /**
     * Determines if the application is in the foreground.
     *
     * @return <code>true</code> if the application is in the foreground, otherwise
     * <code>false</code>.
     */
    boolean isAppForegrounded();

    /**
     * Gets the list of current resumed activities.
     *
     * @return The resumed activities.
     */
    @NonNull
    @MainThread
    List<Activity> getResumedActivities();

    /**
     * Gets the list of current resumed activities that match the filter.
     *
     * @param filter A predicate to filter out activities from the result.
     * @return The filtered resumed activities.
     */
    @NonNull
    @MainThread
    List<Activity> getResumedActivities(@NonNull Predicate<Activity> filter);

}
