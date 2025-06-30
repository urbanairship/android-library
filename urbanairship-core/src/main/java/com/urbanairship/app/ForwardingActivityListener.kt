/* Copyright Airship and Contributors */
package com.urbanairship.app

import android.app.Activity
import android.os.Bundle

/**
 * Activity listener that forwards activity events to a list of listeners.
 */
public open class ForwardingActivityListener public constructor() : ActivityListener {

    private val listeners = mutableListOf<ActivityListener>()

    /**
     * Adds a listener.
     *
     * @param listener The added listener.
     */
    public fun addListener(listener: ActivityListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    /**
     * Removes a listener.
     *
     * @param listener The removed listener.
     */
    public fun removeListener(listener: ActivityListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        listeners.toList().forEach { it.onActivityCreated(activity, bundle) }
    }

    override fun onActivityStarted(activity: Activity) {
        listeners.toList().forEach { it.onActivityStarted(activity) }
    }

    override fun onActivityResumed(activity: Activity) {
        listeners.toList().forEach { it.onActivityResumed(activity) }
    }

    override fun onActivityPaused(activity: Activity) {
        listeners.toList().forEach { it.onActivityPaused(activity) }
    }

    override fun onActivityStopped(activity: Activity) {
        listeners.toList().forEach { it.onActivityStopped(activity) }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        listeners.toList().forEach { it.onActivitySaveInstanceState(activity, outState) }
    }

    override fun onActivityDestroyed(activity: Activity) {
        listeners.toList().forEach { it.onActivityDestroyed(activity) }
    }
}
