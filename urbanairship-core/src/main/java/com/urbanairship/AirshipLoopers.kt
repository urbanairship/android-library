package com.urbanairship

import android.os.Looper
import com.urbanairship.util.AirshipHandlerThread

/**
 * Shared SDK loopers.
 */
public object AirshipLoopers {

    /**
     * Gets the background looper.
     */
    @JvmStatic
    public val backgroundLooper: Looper by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        val thread = AirshipHandlerThread("background")
        thread.start()
        return@lazy thread.looper
    }
}
