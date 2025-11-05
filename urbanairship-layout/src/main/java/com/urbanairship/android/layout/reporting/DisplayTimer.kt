/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.reporting

import android.app.Activity
import androidx.annotation.RestrictTo
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.urbanairship.Predicate
import com.urbanairship.UALog
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.FilteredActivityListener
import com.urbanairship.app.SimpleActivityListener
import java.lang.ref.WeakReference

/**
 * Tracks the amount of time displayed for the given `LifecycleOwner` or
 * `ActivityMonitor` and Activity predicate.
 *
 *
 * This class does not handle persisting timer state, which must be saved and restored from `savedInstanceState`
 * or elsewhere in order for tracking across pauses/resumes to remain accurate.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DisplayTimer {

    private var resumeTime: Long = 0
    private var displayTime: Long = 0

    public constructor(
        lifecycleOwner: LifecycleOwner,
        restoredDisplayTime: Long = 0
    ) {
        if (restoredDisplayTime > 0) {
            displayTime = restoredDisplayTime
        }

        lifecycleOwner.lifecycle.addObserver(LifecycleListener(this))
    }

    public constructor(
        activityMonitor: ActivityMonitor,
        activityPredicate: Predicate<Activity>? = null,
        restoredDisplayTime: Long = 0
    ) {
        if (restoredDisplayTime > 0) {
            displayTime = restoredDisplayTime
        }
        val predicate = activityPredicate ?: Predicate { activity: Activity -> true }
        val activityListener = FilteredActivityListener(
            listener = DisplayActivityListener(this),
            filter = predicate
        )

        activityMonitor.addActivityListener(activityListener)
    }

    public val time: Long
        /** Returns the current displayed time.  */
        get() {
            var time = displayTime
            if (resumeTime > 0) {
                time += System.currentTimeMillis() - resumeTime
            }
            return time
        }

    public fun onResume() {
        resumeTime = System.currentTimeMillis()
    }

    public fun onPause() {
        displayTime += System.currentTimeMillis() - resumeTime
        resumeTime = 0
    }

    private class DisplayActivityListener(timer: DisplayTimer): SimpleActivityListener() {

        private val weakTimer = WeakReference(timer)

        override fun onActivityPaused(activity: Activity) {
            val timer = weakTimer.get()?.apply { onPause() }
            if (timer == null) {
                UALog.w("DisplayTimer ref was null!")
            }
        }

        override fun onActivityResumed(activity: Activity) {
            val timer = weakTimer.get()?.apply { onResume() }
            if (timer == null) {
                UALog.w("DisplayTimer ref was null!")
            }
        }
    }

    private class LifecycleListener(timer: DisplayTimer): DefaultLifecycleObserver {

        private val weakTimer = WeakReference(timer)

        override fun onResume(owner: LifecycleOwner) {
            val timer = weakTimer.get()?.apply { onResume() }
            if (timer == null) {
                UALog.w("DisplayTimer ref was null!")
            }
        }

        override fun onPause(owner: LifecycleOwner) {
            val timer = weakTimer.get()?.apply { onPause() }
            if (timer == null) {
                UALog.w("DisplayTimer ref was null!")
            }
        }

        override fun onDestroy(owner: LifecycleOwner) {
            owner.lifecycle.removeObserver(this)
        }
    }
}
