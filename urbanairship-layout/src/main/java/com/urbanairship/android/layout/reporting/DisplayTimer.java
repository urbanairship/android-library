/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.reporting;

import com.urbanairship.Logger;

import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

/**
 * Tracks the amount of time displayed for the given {@code LifecycleOwner}.
 *
 * This class does not handle persisting timer state, which must be saved and restored from {@code savedInstanceState}
 * or elsewhere in order for tracking across pauses/resumes to remain accurate.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DisplayTimer {
    private long resumeTime = 0;
    private long displayTime = 0;

    /** Constructs a {@code DisplayTimer} with the default initial time of zero. */
    public DisplayTimer(LifecycleOwner lifecycleOwner) {
        this(lifecycleOwner, 0);
    }

    /** Constructs a {@code DisplayTimer} with initial state set from the supplied {@code restoredDisplayTime}. */
    public DisplayTimer(LifecycleOwner lifecycleOwner, long restoredDisplayTime) {
        if (restoredDisplayTime > 0) {
            displayTime = restoredDisplayTime;
        }

        lifecycleOwner.getLifecycle().addObserver(new LifecycleListener(this));
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
                Logger.warn("DisplayTimer ref was null!");
            }
        }

        @Override
        public void onPause(@NonNull LifecycleOwner owner) {
            DisplayTimer timer = weakTimer.get();
            if (timer != null) {
                weakTimer.get().onPause();
            } else {
                Logger.warn("DisplayTimer ref was null!");
            }
        }

        @Override
        public void onDestroy(@NonNull LifecycleOwner owner) {
            owner.getLifecycle().removeObserver(this);
        }
    }
}
