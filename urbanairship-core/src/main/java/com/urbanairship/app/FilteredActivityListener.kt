/* Copyright Airship and Contributors */
package com.urbanairship.app

import android.app.Activity
import android.os.Bundle
import com.urbanairship.Predicate

/**
 * Activity listener that filters events with a predicate.
 */
public class FilteredActivityListener public constructor(
    private val listener: ActivityListener,
    private val filter: Predicate<Activity>
) : ActivityListener {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (filter.apply(activity)) {
            listener.onActivityCreated(activity, savedInstanceState)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (filter.apply(activity)) {
            listener.onActivityStarted(activity)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (filter.apply(activity)) {
            listener.onActivityResumed(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (filter.apply(activity)) {
            listener.onActivityPaused(activity)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (filter.apply(activity)) {
            listener.onActivityStopped(activity)
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        if (filter.apply(activity)) {
            listener.onActivitySaveInstanceState(activity, outState)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (filter.apply(activity)) {
            listener.onActivityDestroyed(activity)
        }
    }
}
