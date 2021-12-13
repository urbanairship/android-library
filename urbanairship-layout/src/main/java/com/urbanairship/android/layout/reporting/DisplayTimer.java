/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.reporting;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
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
        Lifecycle lifecycle = lifecycleOwner.getLifecycle();

        if (restoredDisplayTime > 0) {
            displayTime = restoredDisplayTime;
        }

        lifecycle.addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onResume(@NonNull LifecycleOwner owner) {
                resumeTime = System.currentTimeMillis();
            }

            @Override
            public void onPause(@NonNull LifecycleOwner owner) {
                displayTime += System.currentTimeMillis() - resumeTime;
                resumeTime = 0;
            }
        });
    }

    /** Returns the current displayed time. */
    public long getTime() {
        long time = displayTime;
        if (resumeTime > 0) {
            time += System.currentTimeMillis() - resumeTime;
            resumeTime = 0;
        }
        return time;
    }
}
