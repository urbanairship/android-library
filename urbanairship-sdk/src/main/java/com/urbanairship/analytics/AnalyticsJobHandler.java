/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.VisibleForTesting;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.job.Job;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.util.UAStringUtil;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Handles intents for {@link Analytics#onPerformJob(UAirship, Job)}.
 */
class AnalyticsJobHandler {

    /**
     * Intent action to send an event.
     *
     * @hide
     */
    static final String ACTION_SEND = "com.urbanairship.analytics.SEND";

    /**
     * Intent action to add an event.
     */
    static final String ACTION_ADD = "com.urbanairship.analytics.ADD";

    /**
     * Intent action to delete all locally stored events.
     */
    static final String ACTION_DELETE_ALL = "com.urbanairship.analytics.DELETE_ALL";

    /**
     * Intent action to update the ad ID on foreground.
     */
    static final String ACTION_UPDATE_ADVERTISING_ID = "com.urbanairship.com.analytics.UPDATE_ADVERTISING_ID";

    /**
     * Intent extra for the event's type.
     */
    static final String EXTRA_EVENT_TYPE = "EXTRA_EVENT_TYPE";

    /**
     * Intent extra for the event's ID.
     */
    static final String EXTRA_EVENT_ID = "EXTRA_EVENT_ID";

    /**
     * Intent extra for the event's data.
     */
    static final String EXTRA_EVENT_DATA = "EXTRA_EVENT_DATA";

    /**
     * Intent extra for the event's time stamp.
     */
    static final String EXTRA_EVENT_TIME_STAMP = "EXTRA_EVENT_TIME_STAMP";

    /**
     * Intent extra for the event's session ID.
     */
    static final String EXTRA_EVENT_SESSION_ID = "EXTRA_EVENT_SESSION_ID";

    /**
     * Intent extra for the event's priority.
     */
    static final String EXTRA_EVENT_PRIORITY = "EXTRA_EVENT_PRIORITY";


    /**
     * Batch delay between multiple event uploads in milliseconds.
     */
    private static final long MULTIPLE_BATCH_DELAY = 1000; // 1s

    /**
     * Batch delay for high priority events in milliseconds.
     */
    private static final long HIGH_PRIORITY_BATCH_DELAY = 0; // 0s

    /**
     * Batch delay for normal priority events in milliseconds.
     */
    private static final long NORMAL_PRIORITY_BATCH_DELAY = 10000; // 10s

    /**
     * Max batch event count.
     */
    private static final int MAX_BATCH_EVENT_COUNT = 500;

    static final String MAX_TOTAL_DB_SIZE_KEY = "com.urbanairship.analytics.MAX_TOTAL_DB_SIZE";
    static final String MAX_BATCH_SIZE_KEY = "com.urbanairship.analytics.MAX_BATCH_SIZE";
    static final String MIN_BATCH_INTERVAL_KEY = "com.urbanairship.analytics.MIN_BATCH_INTERVAL";
    static final String LAST_SEND_KEY = "com.urbanairship.analytics.LAST_SEND";
    static final String SCHEDULED_SEND_TIME = "com.urbanairship.analytics.SCHEDULED_SEND_TIME";

    /**
     * Batch delay for low priority events in milliseconds.
     */
    private static final long LOW_PRIORITY_BATCH_DELAY = 30000; // 30s

    private final Context context;
    private final EventDataManager dataManager;
    private final PreferenceDataStore preferenceDataStore;
    private final EventApiClient apiClient;
    private final UAirship airship;
    private final JobDispatcher dispatcher;
    private boolean isScheduled;

    AnalyticsJobHandler(Context context, UAirship airship, PreferenceDataStore preferenceDataStore) {
        this(context, airship, preferenceDataStore, JobDispatcher.shared(context), new EventDataManager(context, airship.getAirshipConfigOptions().getAppKey()), new EventApiClient(context));
    }

    @VisibleForTesting
    AnalyticsJobHandler(Context context, UAirship airship, PreferenceDataStore preferenceDataStore, JobDispatcher dispatcher, EventDataManager dataManager, EventApiClient apiClient) {
        this.airship = airship;
        this.context = context;
        this.dataManager = dataManager;
        this.preferenceDataStore = preferenceDataStore;
        this.apiClient = apiClient;
        this.dispatcher = dispatcher;
    }

    public
    @Job.JobResult
    int performJob(Job job) {
        Logger.verbose("AnalyticsJobHandler - Received job with action: " + job.getAction());

        switch (job.getAction()) {
            case ACTION_DELETE_ALL:
                return onDeleteEvents();

            case ACTION_ADD:
                return onAddEvent(job);

            case ACTION_SEND:
                return onUploadEvents();

            case ACTION_UPDATE_ADVERTISING_ID:
                return onUpdateAdvertisingId();

            default:
                Logger.warn("AnalyticsJobHandler - Unrecognized job with action: " + job.getAction());
                return Job.JOB_FINISHED;
        }
    }

    /**
     * Updates the advertising ID and limited ad tracking preference.
     *
     * @return The job result.
     */
    @Job.JobResult
    private int onUpdateAdvertisingId() {
        AssociatedIdentifiers associatedIdentifiers = airship.getAnalytics().getAssociatedIdentifiers();

        String advertisingId = associatedIdentifiers.getAdvertisingId();
        boolean limitedAdTrackingEnabled = associatedIdentifiers.isLimitAdTrackingEnabled();


        switch (airship.getPlatformType()) {
            case UAirship.AMAZON_PLATFORM:
                advertisingId = Settings.Secure.getString(context.getContentResolver(), "advertising_id");
                limitedAdTrackingEnabled = Settings.Secure.getInt(context.getContentResolver(), "limit_ad_tracking", -1) == 0;
                break;

            case UAirship.ANDROID_PLATFORM:
                if (!PlayServicesUtils.isGoogleAdsDependencyAvailable()) {
                    break;
                }

                try {
                    AdvertisingIdClient.Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context);
                    if (adInfo == null) {
                        break;
                    }

                    advertisingId = adInfo.getId();
                    limitedAdTrackingEnabled = adInfo.isLimitAdTrackingEnabled();
                } catch (IOException | GooglePlayServicesNotAvailableException | GooglePlayServicesRepairableException e) {
                    Logger.error("AnalyticsJobHandler - Failed to retrieve and update advertising ID.", e);
                    return Job.JOB_RETRY;
                }

                break;
        }

        if (!UAStringUtil.equals(associatedIdentifiers.getAdvertisingId(), advertisingId) ||
                associatedIdentifiers.isLimitAdTrackingEnabled() != limitedAdTrackingEnabled) {

            airship.getAnalytics().editAssociatedIdentifiers()
                   .setAdvertisingId(advertisingId, limitedAdTrackingEnabled)
                   .apply();
        }

        return Job.JOB_FINISHED;

    }

    /**
     * Deletes all events.
     *
     * @return The job result.
     */
    @Job.JobResult
    private int onDeleteEvents() {
        Logger.info("Deleting all analytic events.");
        dataManager.deleteAllEvents();

        return Job.JOB_FINISHED;
    }

    /**
     * Adds an event from an intent to the database.
     *
     * @param job A job containing the event's content values to be added
     * to the database.
     * @return The job result.
     */
    @Job.JobResult
    private int onAddEvent(Job job) {
        if (!airship.getAnalytics().isEnabled()) {
            return Job.JOB_FINISHED;
        }

        Bundle extras = job.getExtras();
        String eventType = extras.getString(EXTRA_EVENT_TYPE);
        String eventId = extras.getString(EXTRA_EVENT_ID);
        String eventData = extras.getString(EXTRA_EVENT_DATA);
        String eventTimeStamp = extras.getString(EXTRA_EVENT_TIME_STAMP);
        String sessionId = extras.getString(EXTRA_EVENT_SESSION_ID);
        int priority = extras.getInt(EXTRA_EVENT_PRIORITY, Event.NORMAL_PRIORITY);

        if (eventType == null || eventData == null || eventTimeStamp == null || eventId == null) {
            Logger.warn("Event service unable to add event with missing data.");
            return Job.JOB_FINISHED;
        }

        // Handle database max size exceeded
        if (dataManager.getDatabaseSize() > preferenceDataStore.getInt(MAX_TOTAL_DB_SIZE_KEY, EventResponse.MAX_TOTAL_DB_SIZE_BYTES)) {
            Logger.info("Event database size exceeded. Deleting oldest session.");
            String oldestSessionId = dataManager.getOldestSessionId();
            if (oldestSessionId != null && oldestSessionId.length() > 0) {
                dataManager.deleteSession(oldestSessionId);
            }
        }

        if (dataManager.insertEvent(eventType, eventData, eventId, sessionId, eventTimeStamp) <= 0) {
            Logger.error("AnalyticsJobHandler - Unable to insert event into database.");
        }

        switch (priority) {
            case Event.HIGH_PRIORITY:
                scheduleEventUpload(HIGH_PRIORITY_BATCH_DELAY);
                break;

            case Event.NORMAL_PRIORITY:
                scheduleEventUpload(Math.max(getNextSendDelay(), NORMAL_PRIORITY_BATCH_DELAY));
                break;

            case Event.LOW_PRIORITY:
            default:
                if (airship.getAnalytics().isAppInForeground()) {
                    scheduleEventUpload(Math.max(getNextSendDelay(), LOW_PRIORITY_BATCH_DELAY));
                } else {
                    long currentTime = System.currentTimeMillis();
                    long lastSendTime = preferenceDataStore.getLong(LAST_SEND_KEY, 0);
                    long sendDelta = currentTime - lastSendTime;
                    long throttleDelta = airship.getAirshipConfigOptions().backgroundReportingIntervalMS;
                    long minimumWait = Math.max(throttleDelta - sendDelta, getNextSendDelay());
                    scheduleEventUpload(Math.max(minimumWait, LOW_PRIORITY_BATCH_DELAY));
                }
                break;
        }

        return Job.JOB_FINISHED;
    }

    /**
     * Uploads events.
     *
     * @return The job result.
     */
    @Job.JobResult
    private int onUploadEvents() {
        isScheduled = false;
        dispatcher.cancel(ACTION_SEND);

        preferenceDataStore.put(LAST_SEND_KEY, System.currentTimeMillis());

        final int eventCount = dataManager.getEventCount();

        if (!airship.getAnalytics().isEnabled()) {
            return Job.JOB_FINISHED;
        }

        if (airship.getPushManager().getChannelId() == null) {
            Logger.debug("AnalyticsJobHandler - No channel ID, skipping analytics send.");
            return Job.JOB_FINISHED;
        }

        if (eventCount <= 0) {
            Logger.debug("AnalyticsJobHandler - No events to send. Ending analytics upload.");
            return Job.JOB_FINISHED;
        }

        final int avgSize = dataManager.getDatabaseSize() / eventCount;

        //pull enough events to fill a batch (roughly)
        int batchEventCount = Math.min(MAX_BATCH_EVENT_COUNT, preferenceDataStore.getInt(MAX_BATCH_SIZE_KEY, EventResponse.MAX_BATCH_SIZE_BYTES) / avgSize);
        Map<String, String> events = dataManager.getEvents(batchEventCount);

        EventResponse response = apiClient.sendEvents(airship, events.values());

        if (response == null || response.getStatus() != 200) {
            Logger.debug("Analytic events failed, retrying.");
            isScheduled = true;
            return Job.JOB_RETRY;
        }

        Logger.debug("Analytic events uploaded.");
        dataManager.deleteEvents(events.keySet());

        // Update preferences
        preferenceDataStore.put(MAX_TOTAL_DB_SIZE_KEY, response.getMaxTotalSize());
        preferenceDataStore.put(MAX_BATCH_SIZE_KEY, response.getMaxBatchSize());
        preferenceDataStore.put(MIN_BATCH_INTERVAL_KEY, response.getMinBatchInterval());

        // If there are still events left, schedule the next send
        if (eventCount - events.size() > 0) {
            scheduleEventUpload(MULTIPLE_BATCH_DELAY);
        }

        return Job.JOB_FINISHED;
    }

    /**
     * Gets the next upload delay in milliseconds.
     *
     * @return A delay in ms for the time the events should be sent.
     */
    private long getNextSendDelay() {
        long nextSendTime = preferenceDataStore.getLong(LAST_SEND_KEY, 0) + preferenceDataStore.getInt(MIN_BATCH_INTERVAL_KEY, EventResponse.MIN_BATCH_INTERVAL_MS);
        return Math.max(nextSendTime - System.currentTimeMillis(), 0);
    }

    /**
     * Schedule a batch event upload at a given time in the future.
     *
     * @param milliseconds The milliseconds from the current time to schedule the event upload.
     */
    private void scheduleEventUpload(final long milliseconds) {
        Logger.verbose("AnalyticsJobHandler - Requesting to schedule event upload with delay " + milliseconds + "ms.");

        long sendTime = System.currentTimeMillis() + milliseconds;
        long previousScheduledTime = preferenceDataStore.getLong(SCHEDULED_SEND_TIME, 0);

        if (isScheduled) {
            // If its currently scheduled at an earlier time then skip rescheduling
            if (previousScheduledTime <= sendTime && previousScheduledTime >= System.currentTimeMillis()) {
                Logger.verbose("AnalyticsJobHandler - Event upload already scheduled for an earlier time.");
                return;
            }

            // Cancel the current job
            dispatcher.cancel(ACTION_SEND);
        }

        Logger.verbose("AnalyticsJobHandler - Scheduling event uploads in " + milliseconds + "ms.");

        Job job = Job.newBuilder(ACTION_SEND)
                     .setAirshipComponent(Analytics.class)
                     .build();

        dispatcher.dispatch(job, milliseconds, TimeUnit.MILLISECONDS);

        preferenceDataStore.put(SCHEDULED_SEND_TIME, sendTime);
        isScheduled = true;
    }
}
