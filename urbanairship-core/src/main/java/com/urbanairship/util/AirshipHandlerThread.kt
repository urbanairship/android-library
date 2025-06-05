package com.urbanairship.util

import android.net.TrafficStats
import android.os.HandlerThread
import androidx.annotation.CallSuper
import androidx.annotation.RestrictTo

/**
 * HandlerThread that sets the Airship thread stats tag.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AirshipHandlerThread(name: String) : HandlerThread(name) {
    @CallSuper
    override fun run() {
        TrafficStats.setThreadStatsTag(AirshipThreadFactory.THREAD_STATS_TAG)
        super.run()
    }
}
