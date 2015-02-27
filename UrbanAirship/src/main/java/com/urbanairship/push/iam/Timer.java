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

package com.urbanairship.push.iam;

import android.os.Handler;
import android.os.SystemClock;

/**
 * Timer that can be stopped and started.
 */
abstract class Timer {
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

