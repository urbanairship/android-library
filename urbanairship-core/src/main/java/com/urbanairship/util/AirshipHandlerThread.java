package com.urbanairship.util;

import android.net.TrafficStats;
import android.os.HandlerThread;
import androidx.annotation.RestrictTo;

/**
 * HandlerThread that sets the Airship thread stats tag.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AirshipHandlerThread extends HandlerThread {

    /**
     * Default constructor.
     *
     * @param name The thread name.
     */
    public AirshipHandlerThread(String name) {
        super(name);
    }

    @Override
    public void run() {
        TrafficStats.setThreadStatsTag(AirshipThreadFactory.THREAD_STATS_TAG);
        super.run();
    }

}
