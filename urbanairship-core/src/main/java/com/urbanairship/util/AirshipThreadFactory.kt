package com.urbanairship.util

import android.net.TrafficStats
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * ThreadFactory that sets the Airship thread stats tags on each thread.
 *
 * @hide
 */
internal class AirshipThreadFactory(
    private val threadNamePrefix: String
) : ThreadFactory {

    override fun newThread(runnable: Runnable?): Thread {
        val wrapped = Runnable {
            TrafficStats.setThreadStatsTag(THREAD_STATS_TAG)
            runnable?.run()
        }

        val thread = Thread(wrapped, threadNamePrefix + "#" + count.getAndIncrement())

        if (thread.isDaemon) {
            thread.isDaemon = false
        }

        if (thread.priority != Thread.NORM_PRIORITY) {
            thread.priority = Thread.NORM_PRIORITY
        }

        return thread
    }

    internal companion object {

        /**
         * The Airship thread stats tag.
         */
        internal const val THREAD_STATS_TAG: Int = 11797 // ua

        /**
         * Default thread factory.
         */
        internal val DEFAULT_THREAD_FACTORY: AirshipThreadFactory = AirshipThreadFactory("UrbanAirship")

        private val count = AtomicInteger(1)
    }
}
