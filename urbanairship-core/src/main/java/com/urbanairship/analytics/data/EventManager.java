package com.urbanairship.analytics.data;

import android.content.Context;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.Event;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

/**
 * Handles event storage and uploading.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class EventManager {

    @NonNull
    public static final String ACTION_SEND = "ACTION_SEND";

    static final String MAX_TOTAL_DB_SIZE_KEY = "com.urbanairship.analytics.MAX_TOTAL_DB_SIZE";
    static final String MAX_BATCH_SIZE_KEY = "com.urbanairship.analytics.MAX_BATCH_SIZE";
    static final String LAST_SEND_KEY = "com.urbanairship.analytics.LAST_SEND";
    static final String SCHEDULED_SEND_TIME = "com.urbanairship.analytics.SCHEDULED_SEND_TIME";
    static final String MIN_BATCH_INTERVAL_KEY = "com.urbanairship.analytics.MIN_BATCH_INTERVAL";

    /**
     * Max batch event count.
     */
    private static final int MAX_BATCH_EVENT_COUNT = 500;

    /**
     * Batch delay for low priority events in milliseconds.
     */
    private static final long LOW_PRIORITY_BATCH_DELAY = 30000; // 30s

    /**
     * Batch delay for high priority events in milliseconds.
     */
    private static final long HIGH_PRIORITY_BATCH_DELAY = 0; // 0s

    /**
     * Batch delay for normal priority events in milliseconds.
     */
    private static final long NORMAL_PRIORITY_BATCH_DELAY = 10000; // 10s

    /**
     * Batch delay between multiple event uploads in milliseconds.
     */
    private static final long MULTIPLE_BATCH_DELAY = 1000; // 1s

    private final PreferenceDataStore preferenceDataStore;
    private final JobDispatcher jobDispatcher;
    private final ActivityMonitor activityMonitor;
    private final EventResolver eventResolver;
    private final EventApiClient apiClient;
    private final AirshipRuntimeConfig runtimeConfig;

    private final Object eventLock = new Object();

    private boolean isScheduled;

    public EventManager(@NonNull Context context,
                 @NonNull PreferenceDataStore preferenceDataStore,
                 @NonNull AirshipRuntimeConfig runtimeConfig) {
        this(preferenceDataStore, runtimeConfig, JobDispatcher.shared(context),GlobalActivityMonitor.shared(context),
                new EventResolver(context), new EventApiClient(runtimeConfig));
    }

    @VisibleForTesting
    EventManager(@NonNull PreferenceDataStore preferenceDataStore,
                        @NonNull AirshipRuntimeConfig runtimeConfig,
                        @NonNull JobDispatcher jobDispatcher,
                        @NonNull ActivityMonitor activityMonitor,
                        @NonNull EventResolver eventResolver,
                        @NonNull EventApiClient apiClient) {

        this.preferenceDataStore = preferenceDataStore;
        this.runtimeConfig = runtimeConfig;
        this.jobDispatcher = jobDispatcher;
        this.activityMonitor = activityMonitor;
        this.eventResolver = eventResolver;
        this.apiClient = apiClient;

    }

    /**
     * Schedule a batch event upload at a given time in the future.
     *
     * @param delay The initial delay.
     * @param timeUnit The time unit of the delay.
     */
    public void scheduleEventUpload(final long delay, @NonNull TimeUnit timeUnit) {
        long milliseconds = timeUnit.toMillis(delay);
        Logger.verbose("EventManager - Requesting to schedule event upload with delay %s ms.", milliseconds);

        long sendTime = System.currentTimeMillis() + milliseconds;
        long previousScheduledTime = preferenceDataStore.getLong(SCHEDULED_SEND_TIME, 0);

        // If its currently scheduled at an earlier time then skip rescheduling
        if (isScheduled) {
            if (previousScheduledTime <= sendTime && previousScheduledTime >= System.currentTimeMillis()) {
                Logger.verbose("EventManager - Event upload already scheduled for an earlier time.");
                return;
            }
        }

        Logger.verbose("EventManager - Scheduling upload in %s ms.", milliseconds);
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setId(JobInfo.ANALYTICS_EVENT_UPLOAD)
                                 .setAction(ACTION_SEND)
                                 .setNetworkAccessRequired(true)
                                 .setAirshipComponent(Analytics.class)
                                 .setInitialDelay(milliseconds, TimeUnit.MILLISECONDS)
                                 .build();

        jobDispatcher.dispatch(jobInfo);

        preferenceDataStore.put(SCHEDULED_SEND_TIME, sendTime);
        isScheduled = true;
    }

    /**
     * Adds an event.
     *
     * @param event The event.
     * @param sessionId The event's session ID.
     */
    @WorkerThread
    public void addEvent(@NonNull Event event, @NonNull String sessionId) {
        synchronized (eventLock) {
            eventResolver.insertEvent(event, sessionId);

            // Handle database max size exceeded
            eventResolver.trimDatabase(preferenceDataStore.getInt(MAX_TOTAL_DB_SIZE_KEY, EventResponse.MAX_TOTAL_DB_SIZE_BYTES));
        }

        switch (event.getPriority()) {
            case Event.HIGH_PRIORITY:
                scheduleEventUpload(HIGH_PRIORITY_BATCH_DELAY, TimeUnit.MILLISECONDS);
                break;

            case Event.NORMAL_PRIORITY:
                scheduleEventUpload(Math.max(getNextSendDelay(), NORMAL_PRIORITY_BATCH_DELAY), TimeUnit.MILLISECONDS);
                break;

            case Event.LOW_PRIORITY:
            default:
                if (activityMonitor.isAppForegrounded()) {
                    scheduleEventUpload(Math.max(getNextSendDelay(), LOW_PRIORITY_BATCH_DELAY), TimeUnit.MILLISECONDS);
                } else {
                    long currentTime = System.currentTimeMillis();
                    long lastSendTime = preferenceDataStore.getLong(LAST_SEND_KEY, 0);
                    long sendDelta = currentTime - lastSendTime;
                    long minimumWait = Math.max(runtimeConfig.getConfigOptions().backgroundReportingIntervalMS - sendDelta, getNextSendDelay());
                    scheduleEventUpload(Math.max(minimumWait, LOW_PRIORITY_BATCH_DELAY), TimeUnit.MILLISECONDS);
                }
                break;
        }
    }

    /**
     * Deletes all events.
     */
    @WorkerThread
    public void deleteEvents() {
        synchronized (eventLock) {
            eventResolver.deleteAllEvents();
        }
    }

    /**
     * Gets the next upload delay in milliseconds. The next upload delay is calculated by the following:
     * Max(0, (Last Send Time + MIN_BATCH_INTERVAL) - Current Time)
     * <p>
     * This delay is used to schedule an upload for low and normal priority events.
     *
     * @return A delay in ms.
     */
    private long getNextSendDelay() {
        long nextSendTime = preferenceDataStore.getLong(LAST_SEND_KEY, 0) + preferenceDataStore.getInt(MIN_BATCH_INTERVAL_KEY, EventResponse.MIN_BATCH_INTERVAL_MS);
        return Math.max(nextSendTime - System.currentTimeMillis(), 0);
    }

    /**
     * Uploads events.
     *
     * @param headers The analytic headers.
     * @return {@code true} if the events uploaded, otherwise {@code false}.
     */
    @WorkerThread
    public boolean uploadEvents(@NonNull Map<String, String> headers) {
        isScheduled = false;
        preferenceDataStore.put(LAST_SEND_KEY, System.currentTimeMillis());

        int eventCount;
        Map<String, String> events;

        synchronized (eventLock) {
            eventCount = eventResolver.getEventCount();

            if (eventCount <= 0) {
                Logger.debug("EventManager - No events to send.");
                return true;
            }

            final int avgSize = Math.max(1, eventResolver.getDatabaseSize() / eventCount);

            //pull enough events to fill a batch (roughly)
            int batchEventCount = Math.min(MAX_BATCH_EVENT_COUNT, preferenceDataStore.getInt(MAX_BATCH_SIZE_KEY, EventResponse.MAX_BATCH_SIZE_BYTES) / avgSize);
            events = eventResolver.getEvents(batchEventCount);
        }

        if (events.isEmpty()) {
            return true;
        }

        EventResponse response = apiClient.sendEvents(events.values(), headers);

        if (response == null || response.getStatus() != 200) {
            Logger.debug("EventManager - Analytic upload failed.");
            return false;
        }

        Logger.debug("EventManager - Analytic events uploaded.");

        synchronized (eventLock) {
            eventResolver.deleteEvents(events.keySet());
        }

        // Update preferences
        preferenceDataStore.put(MAX_TOTAL_DB_SIZE_KEY, response.getMaxTotalSize());
        preferenceDataStore.put(MAX_BATCH_SIZE_KEY, response.getMaxBatchSize());
        preferenceDataStore.put(MIN_BATCH_INTERVAL_KEY, response.getMinBatchInterval());

        // If there are still events left, schedule the next send
        if (eventCount - events.size() > 0) {
            scheduleEventUpload(MULTIPLE_BATCH_DELAY, TimeUnit.MILLISECONDS);
        }

        return true;
    }
}
