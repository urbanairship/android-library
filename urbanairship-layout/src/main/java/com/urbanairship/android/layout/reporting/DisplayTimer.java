/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.reporting;

import android.app.Activity;

import com.urbanairship.UALog;
import com.urbanairship.Predicate;
import com.urbanairship.app.ActivityListener;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.FilteredActivityListener;
import com.urbanairship.app.SimpleActivityListener;

import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

/**
 * Tracks the amount of time displayed for the given {@code LifecycleOwner} or
 * {@code ActivityMonitor} and Activity predicate.
 * <p>
 * This class does not handle persisting timer state, which must be saved and restored from {@code savedInstanceState}
 * or elsewhere in order for tracking across pauses/resumes to remain accurate.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DisplayTimer {
    private long resumeTime = 0;
    private long displayTime = 0;

    /** Constructs a {@code DisplayTimer} with the default initial time of zero. */
    public DisplayTimer(@NonNull LifecycleOwner lifecycleOwner) {
        this(lifecycleOwner, 0);
    }

    /** Constructs a {@code DisplayTimer} with initial state set from the supplied {@code restoredDisplayTime}. */
    public DisplayTimer(@NonNull LifecycleOwner lifecycleOwner, long restoredDisplayTime) {
        if (restoredDisplayTime > 0) {
            displayTime = restoredDisplayTime;
        }

        lifecycleOwner.getLifecycle().addObserver(new LifecycleListener(this));
    }

    /** Constructs a {@code DisplayTimer} with the default initial time of zero. */
    public DisplayTimer(@NonNull ActivityMonitor activityMonitor) {
        this(activityMonitor, null, 0);
    }

    /** Constructs a {@code DisplayTimer} with initial state set from the supplied {@code restoredDisplayTime}. */
    public DisplayTimer(
        @NonNull ActivityMonitor activityMonitor,
        @Nullable Predicate<Activity> activityPredicate,
        long restoredDisplayTime
    ) {
        if (restoredDisplayTime > 0) {
            displayTime = restoredDisplayTime;
        }
        Predicate<Activity> predicate =
            activityPredicate != null ? activityPredicate : (activity) -> true;
        ActivityListener activityListener =
                new FilteredActivityListener(new DisplayActivityListener(this), predicate);

        activityMonitor.addActivityListener(activityListener);
    }


    /** Returns the current displayed time. */
    public long getTime() {
        long time = displayTime;
        if (resumeTime > 0) {
            time += System.currentTimeMillis() - resumeTime;
        }
        return time;
    }

    public void onResume() {
        resumeTime = System.currentTimeMillis();

    }

    public void onPause() {
        displayTime += System.currentTimeMillis() - resumeTime;
        resumeTime = 0;
    }

    private static final class DisplayActivityListener extends SimpleActivityListener {
        private final WeakReference<DisplayTimer> weakTimer;

        public DisplayActivityListener(DisplayTimer timer) {
            weakTimer = new WeakReference<>(timer);
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
            DisplayTimer timer = weakTimer.get();
            if (timer != null) {
                timer.onPause();
            } else {
                UALog.w("DisplayTimer ref was null!");
            }
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
            DisplayTimer timer = weakTimer.get();
            if (timer != null) {
                timer.onResume();
            } else {
                UALog.w("DisplayTimer ref was null!");
            }
        }
    }

    private static final class LifecycleListener implements DefaultLifecycleObserver {
        private final WeakReference<DisplayTimer> weakTimer;

        public LifecycleListener(DisplayTimer timer) {
            weakTimer = new WeakReference<>(timer);
        }

        @Override
        public void onResume(@NonNull LifecycleOwner owner) {
            DisplayTimer timer = weakTimer.get();
            if (timer != null) {
                timer.onResume();
            } else {
                UALog.w("DisplayTimer ref was null!");
            }
        }

        @Override
        public void onPause(@NonNull LifecycleOwner owner) {
            DisplayTimer timer = weakTimer.get();
            if (timer != null) {
                timer.onPause();
            } else {
                UALog.w("DisplayTimer ref was null!");
            }
        }

        @Override
        public void onDestroy(@NonNull LifecycleOwner owner) {
            owner.getLifecycle().removeObserver(this);
        }
    }
}
