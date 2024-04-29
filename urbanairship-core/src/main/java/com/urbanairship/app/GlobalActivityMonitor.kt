/* Copyright Airship and Contributors */
package com.urbanairship.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.Predicate
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global activity monitor.
 */
public class GlobalActivityMonitor @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) constructor() : ActivityMonitor {

    private val _foregroundState = MutableStateFlow(false)
    override val foregroundState: StateFlow<Boolean> = _foregroundState.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())

    private val backgroundRunnable = Runnable {
        isAppForegrounded = false
        _foregroundState.value = false
        forwardingApplicationListener.onBackground(backgroundTime)
    }

    private var startedActivities = 0
    private var backgroundTime: Long = 0
    public override var isAppForegrounded: Boolean = false
        private set

    private val _resumedActivities: MutableList<Activity> = mutableListOf()

    public override val resumedActivities: MutableList<Activity>
        get() {
            return Collections.unmodifiableList(_resumedActivities)
        }

    private val forwardingApplicationListener = ForwardingApplicationListener()

    private val forwardingActivityListener: ForwardingActivityListener =
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
                handler.removeCallbacks(backgroundRunnable)
                startedActivities++
                if (!isAppForegrounded) {
                    isAppForegrounded = true
                    _foregroundState.value = true
                    forwardingApplicationListener.onForeground(System.currentTimeMillis())
                }
                super.onActivityStarted(activity)
            }

            override fun onActivityStopped(activity: Activity) {
                if (startedActivities > 0) {
                    startedActivities--
                }
                if (startedActivities == 0 && isAppForegrounded) {
                    backgroundTime = System.currentTimeMillis() + BACKGROUND_DELAY_MS
                    handler.postDelayed(backgroundRunnable, BACKGROUND_DELAY_MS)
                }
                super.onActivityStopped(activity)
            }
        }

    /**
     * Registers for activity lifecycle callbacks.
     *
     * @param context The application context.
     * @hide
     */
    @VisibleForTesting
    public fun registerListener(context: Context) {
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(
            forwardingActivityListener
        )
    }

    /**
     * Unregisters for activity lifecycle callbacks.
     *
     * @param context The application context.
     */
    @VisibleForTesting
    public fun unregisterListener(context: Context) {
        (context.applicationContext as Application).unregisterActivityLifecycleCallbacks(
            forwardingActivityListener
        )
    }

    override fun addActivityListener(listener: ActivityListener) {
        forwardingActivityListener.addListener(listener)
    }

    override fun removeActivityListener(listener: ActivityListener) {
        forwardingActivityListener.removeListener(listener)
    }

    override fun addApplicationListener(listener: ApplicationListener) {
        forwardingApplicationListener.addListener(listener)
    }

    override fun removeApplicationListener(listener: ApplicationListener) {
        forwardingApplicationListener.removeListener(listener)
    }

    @MainThread
    override fun getResumedActivities(filter: Predicate<Activity>): List<Activity> {
        val activities: MutableList<Activity> = ArrayList()
        for (activity in _resumedActivities) {
            if (filter.apply(activity)) {
                activities.add(activity)
            }
        }
        return activities
    }

    public companion object {

        // Brief delay, to give the app a chance to perform screen rotation cleanup
        private const val BACKGROUND_DELAY_MS: Long = 200
        private var singleton: GlobalActivityMonitor? = null

        /**
         * Creates and retrieves the shared activity monitor instance.
         *
         * @param context The application context.
         * @return The singleton.
         */
        @JvmStatic
        public fun shared(context: Context): GlobalActivityMonitor {
            return singleton ?: synchronized(GlobalActivityMonitor::class.java) {
                singleton ?: GlobalActivityMonitor().also {
                    it.registerListener(context)
                }
            }
        }
    }
}
