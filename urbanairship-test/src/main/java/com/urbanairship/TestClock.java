/* Copyright Airship and Contributors */

package com.urbanairship;

import android.os.SystemClock;

import com.urbanairship.util.Clock;

public class TestClock extends Clock {

    public long currentTimeMillis = System.currentTimeMillis();
    public long elapsedRealtime = SystemClock.elapsedRealtime();

    @Override
    public long currentTimeMillis() {
        return currentTimeMillis;
    }

    @Override
    public long elapsedRealtime() {
        return elapsedRealtime;
    }

}
