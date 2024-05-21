/* Copyright Airship and Contributors */
package com.urbanairship.app

import android.app.Activity
import androidx.annotation.MainThread
import com.urbanairship.Predicate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Activity Monitor.
 */
public interface ActivityMonitor {

    /**
     * State flow for the foreground state.
     */
    public val foregroundState: StateFlow<Boolean>

    /**
     * Adds an activity listener.
     *
     * @param listener The added listener.
     */
    public fun addActivityListener(listener: ActivityListener)

    /**
     * Removes an activity listener.
     *
     * @param listener The removed listener.
     */
    public fun removeActivityListener(listener: ActivityListener)

    /**
     * Adds an application listener.
     *
     * @param listener The added listener.
     */
    public fun addApplicationListener(listener: ApplicationListener)

    /**
     * Removes an application listener.
     *
     * @param listener The removed listener.
     */
    public fun removeApplicationListener(listener: ApplicationListener)

    /**
     * Determines if the application is in the foreground.
     *
     * @return `true` if the application is in the foreground, otherwise
     * `false`.
     */
    public val isAppForegrounded: Boolean

    @get:MainThread
    public val resumedActivities: List<Activity>

    /**
     * Gets the list of current resumed activities that match the filter.
     *
     * @param filter A predicate to filter out activities from the result.
     * @return The filtered resumed activities.
     */
    @MainThread
    public fun getResumedActivities(filter: Predicate<Activity>?): List<Activity>
}
