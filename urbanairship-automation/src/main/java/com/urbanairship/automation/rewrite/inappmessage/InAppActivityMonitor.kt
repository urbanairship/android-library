/* Copyright Airship and Contributors */
package com.urbanairship.automation.rewrite.inappmessage

import android.app.Activity
import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import com.urbanairship.Predicate
import com.urbanairship.UALog
import com.urbanairship.app.ActivityListener
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.ApplicationListener
import com.urbanairship.app.FilteredActivityListener
import com.urbanairship.app.ForwardingActivityListener
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.app.SimpleActivityListener
import com.urbanairship.util.ManifestUtils
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Activity monitor that filters out any activities with `EXCLUDE_FROM_AUTO_SHOW` metadata.
 */
internal class InAppActivityMonitor @VisibleForTesting internal constructor(
    private val globalActivityMonitor: ActivityMonitor
) : ActivityMonitor {

    private val allowedActivities = mutableSetOf<Class<*>>()
    private val ignoredActivities = mutableSetOf<Class<*>>()
    private val activityPredicate = object : Predicate<Activity> {
        override fun apply(activity: Activity): Boolean {
            if (allowedActivities.contains(activity.javaClass)) {
                return true
            }
            if (ignoredActivities.contains(activity.javaClass)) {
                return false
            }
            if (shouldIgnoreActivity(activity)) {
                ignoredActivities.add(activity.javaClass)
                return false
            }
            allowedActivities.add(activity.javaClass)
            return true
        }
    }

    private val forwardingActivityListener = ForwardingActivityListener()
    private val filteredActivityListener = FilteredActivityListener(forwardingActivityListener, activityPredicate)

    @VisibleForTesting
    internal fun init() {
        globalActivityMonitor.addActivityListener(filteredActivityListener)
    }

    override fun addActivityListener(listener: ActivityListener) {
        forwardingActivityListener.addListener(listener)
    }

    override fun removeActivityListener(listener: ActivityListener) {
        forwardingActivityListener.removeListener(listener)
    }

    override fun addApplicationListener(listener: ApplicationListener) {
        globalActivityMonitor.addApplicationListener(listener)
    }

    override fun removeApplicationListener(listener: ApplicationListener) {
        globalActivityMonitor.removeApplicationListener(listener)
    }

    override fun isAppForegrounded(): Boolean {
        return globalActivityMonitor.isAppForegrounded
    }

    @MainThread
    override fun getResumedActivities(): List<Activity> {
        return globalActivityMonitor.getResumedActivities(activityPredicate)
    }

    @MainThread
    override fun getResumedActivities(filter: Predicate<Activity>): List<Activity> {
        return globalActivityMonitor.getResumedActivities { activity ->
            activityPredicate.apply(activity) && filter.apply(activity)
        }
    }

    internal suspend fun waitForActive() {
        if (isAppForegrounded) {
            return
        }

        suspendCancellableCoroutine { continuation ->
            val listener = object : SimpleActivityListener() {
                override fun onActivityStarted(activity: Activity) {
                    if (isAppForegrounded) {
                        removeActivityListener(this)
                        continuation.resumeWith(Result.success(Unit))
                    }
                }
            }

            addActivityListener(listener)
        }
    }

    /**
     * Helper method to check if the activity is marked as do not use.
     *
     * @param activity The activity.
     * @return `true` if the activity should be ignored for in-app messaging, otherwise `false`.
     */
    private fun shouldIgnoreActivity(activity: Activity): Boolean {
        val metadata = ManifestUtils.getActivityInfo(activity.javaClass)?.metaData ?: return false
        if (metadata.getBoolean(EXCLUDE_FROM_AUTO_SHOW, false)) {
            UALog.v { "Activity contains metadata to exclude it from auto showing an in-app message" }
            return true
        }

        return false
    }

    companion object {
        /**
         * Metadata an app can use to prevent an in-app message from showing on a specific activity.
         */
        const val EXCLUDE_FROM_AUTO_SHOW = "com.urbanairship.push.iam.EXCLUDE_FROM_AUTO_SHOW"
        private var shared: InAppActivityMonitor? = null

        /**
         * Gets the shared in-app activity monitor instance.
         *
         * @param context The application context.
         * @return The shared in-app activity monitor instance.
         */
        fun shared(context: Context): InAppActivityMonitor {
            return shared ?:
                synchronized(InAppActivityMonitor::class.java) {
                    shared ?: InAppActivityMonitor(GlobalActivityMonitor.shared(context)).also { it.init() }
                }
        }
    }
}
