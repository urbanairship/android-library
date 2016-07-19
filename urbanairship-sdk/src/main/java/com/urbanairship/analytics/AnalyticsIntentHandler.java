/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.analytics;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.urbanairship.AirshipService;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.util.UAStringUtil;

import java.io.IOException;
import java.util.Map;

/**
 * Handles intents for {@link Analytics#onHandleIntent(UAirship, Intent)}.
 */
class AnalyticsIntentHandler {

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
     * Batch delay for high priority events in milliseconds.
     */
    private static final long HIGH_PRIORITY_BATCH_DELAY = 1000; // 1s

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
    static final String MAX_WAIT_KEY = "com.urbanairship.analytics.MAX_WAIT";
    static final String MIN_BATCH_INTERVAL_KEY = "com.urbanairship.analytics.MIN_BATCH_INTERVAL";
    static final String LAST_SEND_KEY = "com.urbanairship.analytics.LAST_SEND";
    static final String SCHEDULED_SEND_TIME = "com.urbanairship.analytics.SCHEDULED_SEND_TIME";

    /**
     * Batch delay for low priority events in milliseconds.
     */
    private static final long LOW_PRIORITY_BATCH_DELAY = 30000; // 30s


    private long backoffMs = 0;

    private final Context context;
    private final EventDataManager dataManager;
    private final PreferenceDataStore preferenceDataStore;
    private final EventApiClient apiClient;
    private final UAirship airship;

    AnalyticsIntentHandler(Context context, UAirship airship, PreferenceDataStore preferenceDataStore) {
        this(context, airship, preferenceDataStore, new EventDataManager(context, airship.getAirshipConfigOptions().getAppKey()), new EventApiClient(context));
    }

    @VisibleForTesting
    AnalyticsIntentHandler(Context context, UAirship airship, PreferenceDataStore preferenceDataStore, EventDataManager dataManager, EventApiClient apiClient) {
        this.airship = airship;
        this.context = context;
        this.dataManager = dataManager;
        this.preferenceDataStore = preferenceDataStore;
        this.apiClient = apiClient;
    }

    /**
     * Checks if an intent action is allowed.
     * @param action The intent action.
     * @return {@code true} if the handler accepts the intent, otherwise {@code false}.
     */
    protected boolean acceptsIntentAction(String action) {
        switch (action) {
            case ACTION_DELETE_ALL:
            case ACTION_ADD:
            case ACTION_SEND:
            case ACTION_UPDATE_ADVERTISING_ID:
                return true;
        }

        return false;
    }

    /**
     * Handles {@link AirshipService} intents for {@link Analytics}.
     * @param intent The intent.
     */
    protected void handleIntent(Intent intent) {
        Logger.verbose("AnalyticsIntentHandler - Received intent: " + intent.getAction());

        switch (intent.getAction()) {
            case ACTION_DELETE_ALL:
                onDeleteEvents();
                break;
            case ACTION_ADD:
                onAddEvent(intent);
                break;
            case ACTION_SEND:
                onUploadEvents();
                break;
            case ACTION_UPDATE_ADVERTISING_ID:
                onUpdateAdvertisingId();
            default:
                Logger.warn("AnalyticsIntentHandler - Unrecognized intent action: " + intent.getAction());
                break;
        }
    }

    /**
     * Updates the advertising ID and limited ad tracking preference.
     */
    @WorkerThread
    private void onUpdateAdvertisingId() {
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
                    advertisingId = adInfo.getId();
                    limitedAdTrackingEnabled = adInfo.isLimitAdTrackingEnabled();
                } catch (IOException | GooglePlayServicesNotAvailableException | GooglePlayServicesRepairableException e) {
                    Logger.error("AnalyticsIntentHandler - Failed to retrieve and update advertising ID.", e);
                    return;
                }
                break;
        }

        if (!UAStringUtil.equals(associatedIdentifiers.getAdvertisingId(), advertisingId) ||
                associatedIdentifiers.isLimitAdTrackingEnabled() != limitedAdTrackingEnabled) {

            airship.getAnalytics().editAssociatedIdentifiers()
                   .setAdvertisingId(advertisingId, limitedAdTrackingEnabled)
                   .apply();
        }
    }

    private void onDeleteEvents() {
        Logger.info("Deleting all analytic events.");
        dataManager.deleteAllEvents();
    }

    /**
     * Adds an event from an intent to the database.
     *
     * @param intent An intent containing the event's content values to be added
     * to the database.
     */
    private void onAddEvent(Intent intent) {
        String eventType = intent.getStringExtra(EXTRA_EVENT_TYPE);
        String eventId = intent.getStringExtra(EXTRA_EVENT_ID);
        String eventData = intent.getStringExtra(EXTRA_EVENT_DATA);
        String eventTimeStamp = intent.getStringExtra(EXTRA_EVENT_TIME_STAMP);
        String sessionId = intent.getStringExtra(EXTRA_EVENT_SESSION_ID);
        int priority = intent.getIntExtra(EXTRA_EVENT_PRIORITY, Event.NORMAL_PRIORITY);

        if (eventType == null || eventData == null || eventTimeStamp == null || eventId == null) {
            Logger.warn("Event service unable to add event with missing data.");
            return;
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
            Logger.error("AnalyticsIntentHandler - Unable to insert event into database.");
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
    }

    /**
     * Uploads events.
     */
    private void onUploadEvents() {
        preferenceDataStore.put(LAST_SEND_KEY, System.currentTimeMillis());

        final int eventCount = dataManager.getEventCount();

        if (airship.getPushManager().getChannelId() == null) {
            Logger.debug("AnalyticsIntentHandler - No channel ID, skipping analytics send.");
            return;
        }

        if (eventCount <= 0) {
            Logger.debug("AnalyticsIntentHandler - No events to send. Ending analytics upload.");
            return;
        }

        final int avgSize = dataManager.getDatabaseSize() / eventCount;

        //pull enough events to fill a batch (roughly)
        int batchEventCount = Math.min(MAX_BATCH_EVENT_COUNT, preferenceDataStore.getInt(MAX_BATCH_SIZE_KEY, EventResponse.MAX_BATCH_SIZE_BYTES) / avgSize);
        Map<String, String> events = dataManager.getEvents(batchEventCount);

        EventResponse response = apiClient.sendEvents(airship, events.values());

        boolean isSuccess = response != null && response.getStatus() == 200;

        if (isSuccess) {
            Logger.info("Analytic events uploaded successfully.");

            dataManager.deleteEvents(events.keySet());
            backoffMs = 0;
        } else {

            if (backoffMs == 0) {
                backoffMs = preferenceDataStore.getInt(MIN_BATCH_INTERVAL_KEY, EventResponse.MIN_BATCH_INTERVAL_MS);
            } else {
                backoffMs = Math.min(backoffMs * 2, preferenceDataStore.getInt(MAX_WAIT_KEY, EventResponse.MAX_WAIT_MS));
            }

            Logger.debug("Analytic events failed to send. Will retry in " + backoffMs + "ms.");
        }

        // If there are still events left, schedule the next send
        if (!isSuccess || eventCount - events.size() > 0) {
            Logger.debug("AnalyticsIntentHandler - Scheduling next event batch upload.");
            scheduleEventUpload(getNextSendDelay());
        }

        if (response != null) {
            preferenceDataStore.put(MAX_TOTAL_DB_SIZE_KEY, response.getMaxTotalSize());
            preferenceDataStore.put(MAX_BATCH_SIZE_KEY, response.getMaxBatchSize());
            preferenceDataStore.put(MAX_WAIT_KEY, response.getMaxWait());
            preferenceDataStore.put(MIN_BATCH_INTERVAL_KEY, response.getMinBatchInterval());
        }
    }

    /**
     * Gets the next upload delay in milliseconds.
     *
     * @return A delay in ms for the time the events should be sent.
     */
    private long getNextSendDelay() {
        long nextSendTime = preferenceDataStore.getLong(LAST_SEND_KEY, 0) + preferenceDataStore.getInt(MIN_BATCH_INTERVAL_KEY, EventResponse.MIN_BATCH_INTERVAL_MS) + backoffMs;
        return Math.max(nextSendTime - System.currentTimeMillis(), 0);
    }

    /**
     * Schedule a batch event upload at a given time in the future.
     *
     * @param milliseconds The milliseconds from the current time to schedule the event upload.
     */
    private void scheduleEventUpload(final long milliseconds) {
        long sendTime = System.currentTimeMillis() + milliseconds;

        AlarmManager alarmManager = (AlarmManager) context
                .getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, AirshipService.class);
        intent.setAction(ACTION_SEND);

        long previousScheduledTime = preferenceDataStore.getLong(SCHEDULED_SEND_TIME, 0);

        // Check if we should reschedule - previousAlarmTime is older than now or greater then the new send time
        boolean reschedule = previousScheduledTime < System.currentTimeMillis() || previousScheduledTime > sendTime;

        // Schedule the alarm if we need to either reschedule or an existing pending intent does not exist
        if (reschedule || PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_NO_CREATE) == null) {
            Logger.verbose("AnalyticsIntentHandler - Scheduling event uploads in " + milliseconds + "ms.");

            PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            try {
                // Reschedule the intent
                alarmManager.set(AlarmManager.RTC, sendTime, pendingIntent);
                preferenceDataStore.put(SCHEDULED_SEND_TIME, sendTime);
            } catch (SecurityException e) {
                Logger.error("AnalyticsIntentHandler - Failed to schedule event uploads.", e);
                long scheduledSendTime = -1;
                preferenceDataStore.put(SCHEDULED_SEND_TIME, scheduledSendTime);
            }

        } else {
            Logger.verbose("AnalyticsIntentHandler - Alarm already scheduled for an earlier time.");
        }
    }

}
