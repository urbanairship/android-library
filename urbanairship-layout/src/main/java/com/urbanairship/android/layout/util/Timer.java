/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.util;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Timer that can be stopped and started.
 */
public abstract class Timer {

    private boolean isStarted;
    private long startTimeMs;
    private long remainingTimeMs;
    private long duration;
    private long elapsedTimeMs;
    private final Handler handler = new Handler(Looper.myLooper());
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
    public Timer(long milliseconds) {
        this.duration = milliseconds;
        this.remainingTimeMs = milliseconds;
    }

    /**
     * Starts the timer.
     */
    public void start() {
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
    public void stop() {
        if (!isStarted) {
            return;
        }

        elapsedTimeMs += SystemClock.elapsedRealtime() - startTimeMs;

        isStarted = false;
        handler.removeCallbacks(trigger);
        remainingTimeMs = Math.max(0, remainingTimeMs - (SystemClock.elapsedRealtime() - startTimeMs));
    }

    /**
     * Gets the total run time in milliseconds.
     *
     * @return The total run time in milliseconds.
     */
    public long getRunTime() {
        if (isStarted) {
            return elapsedTimeMs + SystemClock.elapsedRealtime() - startTimeMs;
        }

        return elapsedTimeMs;
    }

    /**
     * Gets the progress in percentage.
     *
     * @return The progress in percentage.
     */
    public int getProgress() {
        return (int) (getRunTime() * 100 / duration);
    }

    public boolean isStarted() {
        return isStarted;
    }

    /**
     * Called when the timer finishes.
     */
    protected abstract void onFinish();

}
