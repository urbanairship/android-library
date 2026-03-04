/* Copyright Airship and Contributors */
package com.urbanairship

import android.app.Activity
import com.urbanairship.app.ActivityListener
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.ApplicationListener
import com.urbanairship.app.ForwardingActivityListener
import com.urbanairship.app.ForwardingApplicationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Test Activity Monitor.
 */
public class TestActivityMonitor : ActivityMonitor {

    private val _foregroundState = MutableStateFlow(false)
    override val foregroundState: StateFlow<Boolean> = _foregroundState.asStateFlow()

    private var startedActivities = 0
    private val applicationListener = ForwardingApplicationListener()
    private val activityListener: ForwardingActivityListener =
        object : ForwardingActivityListener() {
            override fun onActivityResumed(activity: Activity) {
                _resumedActivities.add(activity)
                super.onActivityResumed(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                _resumedActivities.remove(activity)
                super.onActivityPaused(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                if (!isAppForegrounded) {
                    isAppForegrounded = true
                    _foregroundState.value = true
                    applicationListener.onForeground(System.currentTimeMillis())
                }
                super.onActivityStarted(activity)
            }

            override fun onActivityStopped(activity: Activity) {
                if (startedActivities > 0) {
                    startedActivities--
                }
                if (startedActivities == 0 && isAppForegrounded) {
                    isAppForegrounded = false
                    applicationListener.onBackground(System.currentTimeMillis())
                }
                super.onActivityStopped(activity)
            }
        }
    override var isAppForegrounded: Boolean = false
        private set

    private var _resumedActivities: MutableList<Activity> = mutableListOf()
    public override val resumedActivities: List<Activity>
        get() = _resumedActivities


    /**
     * Starts an activity.
     */
    public fun startActivity() {
        val activity = Activity()
        startActivity(activity)
    }

    /**
     * Stops an activity.
     */
    public fun stopActivity() {
        val activity = Activity()
        stopActivity(activity)
    }

    public fun foreground(timeStamp: Long) {
        applicationListener.onForeground(timeStamp)
    }

    public fun foreground() {
        isAppForegrounded = true
        _foregroundState.value = true
        applicationListener.onForeground(0)
    }

    public fun background() {
        isAppForegrounded = false
        _foregroundState.value = false
        applicationListener.onBackground(0)
    }

    public fun startActivity(activity: Activity) {
        activityListener.onActivityStarted(activity)
    }

    public fun resumeActivity(activity: Activity) {
        activityListener.onActivityResumed(activity)
    }

    public fun pauseActivity(activity: Activity) {
        activityListener.onActivityPaused(activity)
    }

    public fun stopActivity(activity: Activity) {
        activityListener.onActivityStopped(activity)
    }

    override fun addActivityListener(listener: ActivityListener) {
        activityListener.addListener(listener)
    }

    override fun removeActivityListener(listener: ActivityListener) {
        activityListener.removeListener(listener)
    }

    override fun addApplicationListener(listener: ApplicationListener) {
        applicationListener.addListener(listener)
    }

    override fun removeApplicationListener(listener: ApplicationListener) {
        applicationListener.removeListener(listener)
    }

    override fun getResumedActivities(filter: Predicate<Activity>?): List<Activity> {
        val activities: MutableList<Activity> = ArrayList()
        for (activity in resumedActivities) {
            if (filter?.apply(activity) != false) {
                activities.add(activity)
            }
        }
        return activities
    }
}
