package com.urbanairship.util;

import android.net.TrafficStats;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ThreadFactory that sets the Airship thread stats tags on each thread.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AirshipThreadFactory implements ThreadFactory {

    /**
     * The Airship thread stats tag.
     */
    public static final int THREAD_STATS_TAG = 11797; // ua

    /**
     * Default thread factory.
     */
    public static final AirshipThreadFactory DEFAULT_THREAD_FACTORY = new AirshipThreadFactory("UrbanAirship");

    private static final AtomicInteger count = new AtomicInteger(1);
    private final String threadNamePrefix;

    /**
     * Default constructor.
     *
     * @param threadNamePrefix Thread name prefix. #COUNT will be appended to the name.
     */
    public AirshipThreadFactory(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    @NonNull
    @Override
    public Thread newThread(@Nullable final Runnable runnable) {
        Runnable wrapped = new Runnable() {
            @Override
            public void run() {
                TrafficStats.setThreadStatsTag(THREAD_STATS_TAG);
                if (runnable != null) {
                    runnable.run();
                }
            }
        };

        Thread thread = new Thread(wrapped, threadNamePrefix + "#" + count.getAndIncrement());
        if (thread.isDaemon()) {
            thread.setDaemon(false);
        }
        if (thread.getPriority() != Thread.NORM_PRIORITY) {
            thread.setPriority(Thread.NORM_PRIORITY);
        }

        return thread;
    }

}
