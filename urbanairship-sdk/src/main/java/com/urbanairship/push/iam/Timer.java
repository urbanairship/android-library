/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.iam;

import android.os.Handler;
import android.os.SystemClock;

/**
 * Timer that can be stopped and started.
 */
public abstract class Timer {
    private boolean isStarted;
    private long startTimeMs;
    private long remainingTimeMs;

    private long elapsedTimeMs;

    private final Handler handler = new Handler();
    private final Runnable trigger = new Runnable() {
        @Override
        public void run() {
            if (isStarted) {
                stop();
                onFinish();
            }
        }
    };

    /**
     * Creates a new timer.
     *
     * @param milliseconds The duration of the timer in milliseconds.
     */
    Timer(long milliseconds) {
        this.remainingTimeMs = milliseconds;
    }

    /**
     * Starts the timer.
     */
    void start() {
        if (isStarted) {
            return;
        }

        isStarted = true;
        startTimeMs = SystemClock.elapsedRealtime();

        if (remainingTimeMs > 0) {
            handler.postDelayed(trigger, remainingTimeMs);
        } else {
            handler.post(trigger);
        }
    }

    /**
     * Stops the timer.
     */
    void stop() {
        if (!isStarted) {
            return;
        }

        elapsedTimeMs = SystemClock.elapsedRealtime() - startTimeMs;

        isStarted = false;
        handler.removeCallbacks(trigger);
        remainingTimeMs = Math.max(0, remainingTimeMs - (SystemClock.elapsedRealtime() - startTimeMs));
    }

    /**
     * Gets the total run time in milliseconds.
     *
     * @return The total run time in milliseconds.
     */
    long getRunTime() {
        if (isStarted) {
            return elapsedTimeMs + SystemClock.elapsedRealtime() - startTimeMs;
        }

        return elapsedTimeMs;
    }

    /**
     * Called when the timer finishes.
     */
    protected abstract void onFinish();
}

