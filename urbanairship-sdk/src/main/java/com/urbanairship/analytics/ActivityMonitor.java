/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.analytics;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.util.SparseArray;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This class monitors all activities
 */
class ActivityMonitor {

    @IntDef({MANUAL_INSTRUMENTATION, AUTO_INSTRUMENTATION})
    @Retention(RetentionPolicy.SOURCE)
    @interface Source {}

    /**
     * The activity was instrumented manually
     */
    static final int MANUAL_INSTRUMENTATION = 0;

    /**
     * The activity was instrumented automatically from lifecycle callbacks
     */
    static final int AUTO_INSTRUMENTATION = 1;

    private SparseArray<ActivityState> activityStates = new SparseArray<>();
    private Listener listener;
    private boolean isForeground = false;
    private int minSdkVersion;
    private int currentSdkVersion;
    private boolean analyticsEnabled;

    //a brief delay, to give the app a chance to perform screen rotation cleanup
    private final static int BACKGROUND_DELAY_MS = 2000;

    /**
     * The ActivityMonitor constructor
     *
     * @param minSdkVersion The minimum SDK version the application supports
     * @param currentSdkVersion The device SDK version
     * @param analyticsEnabled If analytics is enabled or not
     */
    ActivityMonitor(int minSdkVersion, int currentSdkVersion, boolean analyticsEnabled) {
        this.minSdkVersion = minSdkVersion;
        this.currentSdkVersion = currentSdkVersion;
        this.analyticsEnabled = analyticsEnabled;
    }

    /**
     * Sets the listener for activity events.
     *
     * @param listener The activity event listener.
     */
    void setListener(Listener listener) {
        synchronized (this) {
            this.listener = listener;
        }
    }

    /**
     * Tracks when an activity is started
     *
     * @param activity The specified activity
     * @param source Specifies how the activity was instrumented
     * @param timeStamp The time the activity started in milliseconds
     */
    void activityStarted(@NonNull Activity activity, @Source int source, long timeStamp) {
        getActivityState(activity).setStarted(source, timeStamp);
        updateForegroundState();
    }

    /**
     * Tracks when an activity is stopped
     *
     * @param activity The specified activity
     * @param source Specifies how the activity was instrumented
     * @param timeStamp The time the activity stopped in milliseconds
     */
    void activityStopped(@NonNull Activity activity, @Source int source, long timeStamp) {
        getActivityState(activity).setStopped(source, timeStamp);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateForegroundState();
            }
        }, BACKGROUND_DELAY_MS);
    }

    /**
     * Helper method to get the activity state for activity. If it's not found, it creates and returns one.
     *
     * @param activity The specified activity
     * @return ActivityState The state of the activity
     */
    private ActivityState getActivityState(@NonNull final Activity activity) {
        ActivityState state = activityStates.get(activity.hashCode());
        if (state == null) {
            state = new ActivityState(activity.toString(), minSdkVersion, currentSdkVersion, analyticsEnabled);
            activityStates.put(activity.hashCode(), state);
        }

        return state;
    }

    /**
     * Checks all the activity states to determine the application's foreground state.
     * Call the delegate's callback if the foreground state changes.
     */
    void updateForegroundState() {
        long lastForegroundTime = 0;
        long lastBackgroundTime = 0;

        boolean isAppForegrounded = false;

        for (int i = 0; i < activityStates.size(); i++) {
            ActivityState state = activityStates.valueAt(i);

            if (state.isForeground()) {
                isAppForegrounded = true;
                if (state.getLastModifiedTime() > lastForegroundTime) {
                    lastForegroundTime = state.getLastModifiedTime();
                }
            } else {
                if (state.getLastModifiedTime() > lastBackgroundTime) {
                    lastBackgroundTime = state.getLastModifiedTime();
                }
            }
        }

        if (this.isForeground != isAppForegrounded) {
            this.isForeground = isAppForegrounded;

            synchronized (this) {
                if (isAppForegrounded) {
                    if (listener != null) {
                        listener.onForeground(lastForegroundTime);
                    }
                } else {
                    if (listener != null) {
                        listener.onBackground(lastBackgroundTime);
                    }
                }
            }
        }
    }

    /**
     * The listener for activity events.
     */
    static abstract class Listener {
        /**
         * Called when the app is foregrounded from an activity.
         *
         * @param timeMS The time the app is foregrounded.
         */
        abstract void onForeground(long timeMS);

        /**
         * Called when the app is backgrounded from an activity.
         *
         * @param timeMS The time the app is backgrounded.
         */
        abstract void onBackground(long timeMS);
    }
}
