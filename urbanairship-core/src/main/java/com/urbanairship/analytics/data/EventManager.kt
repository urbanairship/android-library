package com.urbanairship.analytics.data

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UALog
import com.urbanairship.analytics.AirshipEventData
import com.urbanairship.analytics.Analytics
import com.urbanairship.analytics.Event
import com.urbanairship.analytics.data.EventEntity.EventIdAndData
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.GlobalActivityMonitor.Companion.shared
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.RequestException
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.util.Clock
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Handles event storage and uploading.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class EventManager @VisibleForTesting internal constructor(
    private val preferenceDataStore: PreferenceDataStore,
    private val runtimeConfig: AirshipRuntimeConfig,
    private val jobDispatcher: JobDispatcher,
    private val activityMonitor: ActivityMonitor,
    private val eventDao: EventDao,
    private val apiClient: EventApiClient,
    private val clock: Clock = Clock.DEFAULT_CLOCK
) {
    private val eventLock = Mutex()

    private val isScheduled = MutableStateFlow(false)

    public constructor(
        context: Context,
        preferenceDataStore: PreferenceDataStore,
        runtimeConfig: AirshipRuntimeConfig
    ) : this(
        preferenceDataStore,
        runtimeConfig,
        JobDispatcher.shared(context),
        shared(context),
        AnalyticsDatabase.createDatabase(context, runtimeConfig).eventDao,
        EventApiClient(runtimeConfig)
    )

    /**
     * Schedule a batch event upload at a given time in the future.
     *
     * @param delay The initial delay.
     */
    public fun scheduleEventUpload(delay: Duration) {
        var nextDelay = delay

        UALog.v("Requesting to schedule event upload with delay $delay")

        var conflictStrategy = JobInfo.ConflictStrategy.REPLACE

        isScheduled.update { current ->
            // If its currently scheduled at an earlier time then skip rescheduling
            if (current) {
                val previousScheduledTime = preferenceDataStore.getLong(SCHEDULED_SEND_TIME, 0)
                val currentDelay = max(
                    (clock.currentTimeMillis() - previousScheduledTime), 0
                ).milliseconds

                if (currentDelay < nextDelay) {
                    UALog.v("Event upload already scheduled for an earlier time.")
                    conflictStrategy = JobInfo.ConflictStrategy.KEEP
                    nextDelay = currentDelay
                }
            }

            UALog.v("Scheduling upload in $nextDelay ms.")
            val jobInfo = JobInfo.newBuilder()
                .setAction(ACTION_SEND)
                .setNetworkAccessRequired(true)
                .setScope(Analytics::class.java.name)
                .setMinDelay(nextDelay)
                .setConflictStrategy(conflictStrategy)
                .build()

            jobDispatcher.dispatch(jobInfo)

            preferenceDataStore.put(SCHEDULED_SEND_TIME, clock.currentTimeMillis() + nextDelay.inWholeMilliseconds)
            true
        }
    }

    /**
     * Adds an event.
     *
     * @param eventData The event data.
     * @param priority The event priority.
     */
    public suspend fun addEvent(eventData: AirshipEventData, priority: Event.Priority) {
        val entity = EventEntity(eventData)

        eventLock.withLock {
            eventDao.insert(entity)
            // Handle database max size exceeded
            val maxSize = preferenceDataStore.getInt(
                MAX_TOTAL_DB_SIZE_KEY, EventResponse.MAX_TOTAL_DB_SIZE_BYTES
            )
            eventDao.trimDatabase(maxSize)
        }

        when (priority) {
            Event.Priority.HIGH -> scheduleEventUpload(HIGH_PRIORITY_BATCH_DELAY)
            Event.Priority.NORMAL -> {
                scheduleEventUpload(
                    maxOf(nextSendDelay, NORMAL_PRIORITY_BATCH_DELAY)
                )
            }
            Event.Priority.LOW -> {
                if (activityMonitor.isAppForegrounded) {
                    scheduleEventUpload(maxOf(nextSendDelay, LOW_PRIORITY_BATCH_DELAY))
                } else {
                    val currentTime = clock.currentTimeMillis()
                    val lastSendTime = preferenceDataStore.getLong(LAST_SEND_KEY, 0)
                    val sendDelta = currentTime - lastSendTime
                    val minimumWait = max(
                        (runtimeConfig.configOptions.backgroundReportingIntervalMS - sendDelta).toDouble(),
                        nextSendDelay.inWholeMilliseconds.toDouble()
                    ).milliseconds

                    scheduleEventUpload(maxOf(minimumWait, LOW_PRIORITY_BATCH_DELAY))
                }
            }
        }
    }

    /**
     * Deletes all events.
     */
    public suspend fun deleteEvents() {
        eventLock.withLock {
            eventDao.deleteAll()
        }
    }

    /**
     * Gets the next upload delay. The next upload delay is calculated by the following:
     * Max(0, (Last Send Time + MIN_BATCH_INTERVAL) - Current Time)
     *
     *
     * This delay is used to schedule an upload for low and normal priority events.
     *
     * @return A delay.
     */
    private val nextSendDelay: Duration
        get() {
            val nextSendTime = preferenceDataStore.getLong(LAST_SEND_KEY, 0) +
                    preferenceDataStore.getInt(MIN_BATCH_INTERVAL_KEY, EventResponse.MIN_BATCH_INTERVAL_MS)

            return max((nextSendTime - clock.currentTimeMillis()).toDouble(), 0.0).milliseconds
        }

    /**
     * Uploads events.
     *
     * @param channelId The channel Id.
     * @param headers The analytic headers.
     * @return `true` if the events uploaded, otherwise `false`.
     */
    public suspend fun uploadEvents(channelId: String, headers: Map<String, String>): Boolean {
        isScheduled.update {
            preferenceDataStore.put(LAST_SEND_KEY, clock.currentTimeMillis())
            false
        }

        val eventCount: Int
        val events: List<EventIdAndData>

        try {
            eventLock.withLock {
                eventCount = eventDao.count()
                if (eventCount <= 0) {
                    UALog.d("No events to send.")
                    return true
                }

                val avgSize = max(1.0, (eventDao.databaseSize() / eventCount).toDouble()).toInt()

                //pull enough events to fill a batch (roughly)
                val batchEventCount = min(
                    MAX_BATCH_EVENT_COUNT.toDouble(),
                    (preferenceDataStore.getInt(MAX_BATCH_SIZE_KEY, EventResponse.MAX_BATCH_SIZE_BYTES) / avgSize).toDouble()
                ).toInt()

                events = eventDao.getBatch(batchEventCount)
            }
        } catch (e: SQLiteException) {
            UALog.e(e, "EventManager - Failed to query batched events")
            return false
        }

        if (events.isEmpty()) {
            UALog.v("No analytics events to send.")
            return false
        }

        val eventPayloads = events.map { it.data }

        try {
            val response = apiClient.sendEvents(channelId, eventPayloads, headers)
            if (!response.isSuccessful || response.value == null) {
                UALog.d("Analytic upload failed.")
                return false
            }

            UALog.d("Analytic events uploaded.")
            eventLock.withLock {
                eventDao.deleteBatch(events)
            }

            // Update preferences
            preferenceDataStore.put(MAX_TOTAL_DB_SIZE_KEY, response.value.maxTotalSize)
            preferenceDataStore.put(MAX_BATCH_SIZE_KEY, response.value.maxBatchSize)
            preferenceDataStore.put(MIN_BATCH_INTERVAL_KEY, response.value.minBatchInterval)

            // If there are still events left, schedule the next send
            if (eventCount - events.size > 0) {
                scheduleEventUpload(MULTIPLE_BATCH_DELAY)
            }

            return true
        } catch (e: RequestException) {
            UALog.e(e, "EventManager - Failed to upload events")
            return false
        }
    }

    internal companion object {

        const val ACTION_SEND = "ACTION_SEND"
        const val MAX_TOTAL_DB_SIZE_KEY = "com.urbanairship.analytics.MAX_TOTAL_DB_SIZE"
        const val MAX_BATCH_SIZE_KEY = "com.urbanairship.analytics.MAX_BATCH_SIZE"
        const val LAST_SEND_KEY = "com.urbanairship.analytics.LAST_SEND"
        const val SCHEDULED_SEND_TIME = "com.urbanairship.analytics.SCHEDULED_SEND_TIME"
        const val MIN_BATCH_INTERVAL_KEY = "com.urbanairship.analytics.MIN_BATCH_INTERVAL"

        /**
         * Max batch event count.
         */
        private const val MAX_BATCH_EVENT_COUNT = 500

        /**
         * Batch delay for low priority events.
         */
        private val LOW_PRIORITY_BATCH_DELAY = 30.seconds

        /**
         * Batch delay for high priority events.
         */
        private val HIGH_PRIORITY_BATCH_DELAY = 0.seconds // 0s

        /**
         * Batch delay for normal priority events.
         */
        private val NORMAL_PRIORITY_BATCH_DELAY = 10.seconds

        /**
         * Batch delay between multiple event uploads.
         */
        private val MULTIPLE_BATCH_DELAY = 1.seconds
    }
}
