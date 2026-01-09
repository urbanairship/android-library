/* Copyright Airship and Contributors */
package com.urbanairship

import android.os.SystemClock
import com.urbanairship.util.Clock

public class TestClock public constructor() : Clock() {

    public var currentTimeMillis: Long = System.currentTimeMillis()
    public var elapsedRealtime: Long = SystemClock.elapsedRealtime()

    override fun currentTimeMillis(): Long {
        return currentTimeMillis
    }

    override fun elapsedRealtime(): Long {
        return elapsedRealtime
    }
}
