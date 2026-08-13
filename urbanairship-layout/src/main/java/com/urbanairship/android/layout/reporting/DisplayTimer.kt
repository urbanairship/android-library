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
import com.urbanairship.util.Clock
import com.urbanairship.util.minus
import java.lang.ref.WeakReference
import java.time.Instant
import kotlin.time.Duration

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
public class DisplayTimer private constructor(
    private val clock: Clock,
    restoredDisplayTime: Duration
) {

    /** The instant of the last [onResume], or `null` while paused. */
    private var resumeTime: Instant? = null

    /** Time accrued across completed resume/pause spans. */
    private var displayTime: Duration = restoredDisplayTime.coerceAtLeast(Duration.ZERO)

    public constructor(
        lifecycleOwner: LifecycleOwner,
        restoredDisplayTime: Duration = Duration.ZERO,
        clock: Clock = Clock.DEFAULT_CLOCK
    ) : this(clock, restoredDisplayTime) {
        lifecycleOwner.lifecycle.addObserver(LifecycleListener(this))
    }

    public constructor(
        activityMonitor: ActivityMonitor,
        activityPredicate: Predicate<Activity>? = null,
        restoredDisplayTime: Duration = Duration.ZERO,
        clock: Clock = Clock.DEFAULT_CLOCK
    ) : this(clock, restoredDisplayTime) {
        val predicate = activityPredicate ?: Predicate { activity: Activity -> true }
        val activityListener = FilteredActivityListener(
            listener = DisplayActivityListener(this),
            filter = predicate
        )

        activityMonitor.addActivityListener(activityListener)
    }

    public val time: Duration
        /** Returns the current displayed time.  */
        get() = resumeTime?.let { displayTime + (clock.now() - it) } ?: displayTime

    public fun onResume() {
        resumeTime = clock.now()
    }

    public fun onPause() {
        // Only accrue time if we were actually resumed. Previously `resumeTime` defaulted to 0,
        // so an unpaired onPause added the entire epoch-to-now span to the display time.
        resumeTime?.let { displayTime += clock.now() - it }
        resumeTime = null
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
