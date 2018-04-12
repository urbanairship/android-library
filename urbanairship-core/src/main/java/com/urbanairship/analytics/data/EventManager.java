package com.urbanairship.analytics.data;


import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.support.annotation.WorkerThread;

import com.urbanairship.ActivityMonitor;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.Event;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.util.Checks;

import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * Handles event storage and uploading.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class EventManager {

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
    private final long backgroundReportingIntervalMS;
    private final String jobAction;

    private boolean isScheduled;

    /**
     * Default constructor.
     *
     * @param builder The builder instance.
     */
    private EventManager(Builder builder) {
        this.preferenceDataStore = builder.preferenceDataStore;
        this.jobDispatcher = builder.jobDispatcher;
        this.activityMonitor = builder.activityMonitor;
        this.eventResolver = builder.eventResolver;
        this.apiClient = builder.apiClient;
        this.backgroundReportingIntervalMS = builder.backgroundReportingIntervalMS;
        this.jobAction = builder.jobAction;
    }

    /**
     * Schedule a batch event upload at a given time in the future.
     *
     * @param delay The initial delay.
     * @param timeUnit The time unit of the delay.
     */
    public void scheduleEventUpload(final long delay, TimeUnit timeUnit) {
        long milliseconds = timeUnit.toMillis(delay);
        Logger.verbose("EventManager - Requesting to schedule event upload with delay " + milliseconds + "ms.");

        long sendTime = System.currentTimeMillis() + milliseconds;
        long previousScheduledTime = preferenceDataStore.getLong(SCHEDULED_SEND_TIME, 0);

        // If its currently scheduled at an earlier time then skip rescheduling
        if (isScheduled) {
            if (previousScheduledTime <= sendTime && previousScheduledTime >= System.currentTimeMillis()) {
                Logger.verbose("EventManager - Event upload already scheduled for an earlier time.");
                return;
            }
        }

        Logger.verbose("EventManager - Scheduling upload in " + milliseconds + "ms.");
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(jobAction)
                                 .setId(JobInfo.ANALYTICS_EVENT_UPLOAD)
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
    public void addEvent(Event event, String sessionId) {
        eventResolver.insertEvent(event, sessionId);

        // Handle database max size exceeded
        eventResolver.trimDatabase(preferenceDataStore.getInt(MAX_TOTAL_DB_SIZE_KEY, EventResponse.MAX_TOTAL_DB_SIZE_BYTES));

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
                    long throttleDelta = backgroundReportingIntervalMS;
                    long minimumWait = Math.max(throttleDelta - sendDelta, getNextSendDelay());
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
        eventResolver.deleteAllEvents();
    }

    /**
     * Gets the next upload delay in milliseconds. The next upload delay is calculated by the following:
     * Max(0, (Last Send Time + MIN_BATCH_INTERVAL) - Current Time)
     *
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
     * @param airship The airship instance.
     * @return {@code true} if the events uploaded, otherwise {@code false}.
     */
    @WorkerThread
    public boolean uploadEvents(UAirship airship) {
        isScheduled = false;
        preferenceDataStore.put(LAST_SEND_KEY, System.currentTimeMillis());

        final int eventCount = eventResolver.getEventCount();

        if (eventCount <= 0) {
            Logger.debug("EventManager - No events to send.");
            return true;
        }

        final int avgSize = eventResolver.getDatabaseSize() / eventCount;

        //pull enough events to fill a batch (roughly)
        int batchEventCount = Math.min(MAX_BATCH_EVENT_COUNT, preferenceDataStore.getInt(MAX_BATCH_SIZE_KEY, EventResponse.MAX_BATCH_SIZE_BYTES) / avgSize);
        Map<String, String> events = eventResolver.getEvents(batchEventCount);

        EventResponse response = apiClient.sendEvents(airship, events.values());

        if (response == null || response.getStatus() != 200) {
            Logger.debug("EventManager - Analytic upload failed.");
            return false;
        }

        Logger.debug("EventManager - Analytic events uploaded.");
        eventResolver.deleteEvents(events.keySet());

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


    /**
     * EventManager builder
     */
    public static class Builder {
        private PreferenceDataStore preferenceDataStore;
        private JobDispatcher jobDispatcher;
        private ActivityMonitor activityMonitor;
        private EventResolver eventResolver;
        private EventApiClient apiClient;
        private String jobAction;
        private long backgroundReportingIntervalMS;

        /**
         * Sets the {@link PreferenceDataStore}.
         *
         * @param preferenceDataStore The {@link PreferenceDataStore}.
         * @return The builder instance.
         */
        public Builder setPreferenceDataStore(@NonNull PreferenceDataStore preferenceDataStore) {
            this.preferenceDataStore = preferenceDataStore;
            return this;
        }

        /**
         * Sets the {@link JobDispatcher}.
         *
         * @param jobDispatcher The {@link JobDispatcher}.
         * @return The builder instance.
         */
        public Builder setJobDispatcher(@NonNull JobDispatcher jobDispatcher) {
            this.jobDispatcher = jobDispatcher;
            return this;
        }

        /**
         * Sets the {@link ActivityMonitor}.
         *
         * @param activityMonitor The {@link ActivityMonitor}.
         * @return The builder instance.
         */
        public Builder setActivityMonitor(@NonNull ActivityMonitor activityMonitor) {
            this.activityMonitor = activityMonitor;
            return this;
        }

        /**
         * Sets the {@link EventResolver}.
         *
         * @param eventResolver The {@link EventResolver}.
         * @return The builder instance.
         */
        public Builder setEventResolver(EventResolver eventResolver) {
            this.eventResolver = eventResolver;
            return this;
        }

        /**
         * Sets the {@link EventApiClient}.
         *
         * @param apiClient The {@link EventApiClient}.
         * @return The builder instance.
         */
        public Builder setApiClient(EventApiClient apiClient) {
            this.apiClient = apiClient;
            return this;
        }

        /**
         * Sets the job action to be used when scheduling event uploads.
         *
         * @param jobAction The job action.
         * @return The builder instance.
         */
        public Builder setJobAction(String jobAction) {
            this.jobAction = jobAction;
            return this;
        }

        /**
         * Sets low priority event background reporting interval in milliseconds.
         *
         * @param backgroundReportingIntervalMS The background reporting interval in milliseconds.
         * @return The builder instance.
         */
        public Builder setBackgroundReportingIntervalMS(long backgroundReportingIntervalMS) {
            this.backgroundReportingIntervalMS = backgroundReportingIntervalMS;
            return this;
        }

        /**
         * Builds the event manager.
         *
         * @return An event manager instance.
         */
        public EventManager build() {
            Checks.checkNotNull(jobDispatcher, "Missing job dispatcher.");
            Checks.checkNotNull(activityMonitor, "Missing activity monitor.");
            Checks.checkNotNull(eventResolver, "Missing event resolver.");
            Checks.checkNotNull(apiClient, "Missing events api client.");
            Checks.checkNotNull(jobAction, "Missing job action.");
            Checks.checkArgument(backgroundReportingIntervalMS > 0, "Missing background reporting interval.");
            return new EventManager(this);
        }
    }

}
