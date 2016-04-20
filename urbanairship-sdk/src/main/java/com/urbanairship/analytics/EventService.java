/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.analytics;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.support.annotation.NonNull;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.util.UAStringUtil;

import java.io.IOException;
import java.util.Map;

/**
 * The EventService is an IntentService designed to handle periodic analytics event
 * uploads and saving events to be uploaded.
 */
public class EventService extends IntentService {

    /**
     * Intent action to send an event.
     *
     * @hide
     */
    public static final String ACTION_SEND = "com.urbanairship.analytics.SEND";

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


    /**
     * Batch delay for low priority events in milliseconds.
     */
    private static final long LOW_PRIORITY_BATCH_DELAY = 30000; // 30s


    private static long backoffMs = 0;

    private final EventApiClient eventClient;

    public EventService() {
        this("EventService");
    }

    public EventService(String serviceName) {
        this(serviceName, new EventApiClient());
    }

    EventService(String serviceName, EventApiClient eventClient) {
        super(serviceName);
        this.eventClient = eventClient;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Autopilot.automaticTakeOff(getApplicationContext());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        Logger.verbose("EventService - Received intent: " + intent.getAction());

        switch (intent.getAction()) {
            case ACTION_DELETE_ALL:
                Logger.info("Deleting all analytic events.");
                UAirship.shared().getAnalytics().getDataManager().deleteAllEvents();
                break;
            case ACTION_ADD:
                addEventFromIntent(intent);
                break;
            case ACTION_SEND:
                uploadEvents();
                break;
            case ACTION_UPDATE_ADVERTISING_ID:
                updateAdvertisingId();
                break;
            default:
                Logger.warn("EventService - Unrecognized intent action: " + intent.getAction());
                break;
        }
    }

    /**
     * Adds an event from an intent to the database.
     *
     * @param intent An intent containing the event's content values to be added
     * to the database.
     */
    private void addEventFromIntent(@NonNull Intent intent) {
        AnalyticsPreferences preferences = UAirship.shared().getAnalytics().getPreferences();
        EventDataManager dataManager = UAirship.shared().getAnalytics().getDataManager();

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
        if (dataManager.getDatabaseSize() > preferences.getMaxTotalDbSize()) {
            Logger.info("Event database size exceeded. Deleting oldest session.");
            String oldestSessionId = dataManager.getOldestSessionId();
            if (oldestSessionId != null && oldestSessionId.length() > 0) {
                dataManager.deleteSession(oldestSessionId);
            }
        }

        if (dataManager.insertEvent(eventType, eventData, eventId, sessionId, eventTimeStamp) <= 0) {
            Logger.error("EventService - Unable to insert event into database.");
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
                if (UAirship.shared().getAnalytics().isAppInForeground()) {
                    scheduleEventUpload(Math.max(getNextSendDelay(), LOW_PRIORITY_BATCH_DELAY));
                } else {
                    long currentTime = System.currentTimeMillis();
                    long lastSendTime = preferences.getLastSendTime();
                    long sendDelta = currentTime - lastSendTime;
                    long throttleDelta = UAirship.shared().getAirshipConfigOptions().backgroundReportingIntervalMS;
                    long minimumWait = Math.max(throttleDelta - sendDelta, getNextSendDelay());
                    scheduleEventUpload(Math.max(minimumWait, LOW_PRIORITY_BATCH_DELAY));
                }
                break;
        }

    }

    /**
     * Uploads events.
     */
    private void uploadEvents() {
        AnalyticsPreferences preferences = UAirship.shared().getAnalytics().getPreferences();
        EventDataManager dataManager = UAirship.shared().getAnalytics().getDataManager();

        preferences.setLastSendTime(System.currentTimeMillis());

        final int eventCount = dataManager.getEventCount();

        if (UAirship.shared().getPushManager().getChannelId() == null) {
            Logger.debug("EventService - No channel ID, skipping analytics send.");
            return;
        }

        if (eventCount <= 0) {
            Logger.debug("EventService - No events to send. Ending analytics upload.");
            return;
        }

        final int avgSize = dataManager.getDatabaseSize() / eventCount;

        //pull enough events to fill a batch (roughly)
        int batchEventCount = Math.min(MAX_BATCH_EVENT_COUNT, preferences.getMaxBatchSize() / avgSize);
        Map<String, String> events = dataManager.getEvents(batchEventCount);

        EventResponse response = eventClient.sendEvents(events.values());

        boolean isSuccess = response != null && response.getStatus() == 200;

        if (isSuccess) {
            Logger.info("Analytic events uploaded successfully.");

            dataManager.deleteEvents(events.keySet());
            backoffMs = 0;
        } else {

            if (backoffMs == 0) {
                backoffMs = preferences.getMinBatchInterval();
            } else {
                backoffMs = Math.min(backoffMs * 2, preferences.getMaxWait());
            }

            Logger.debug("Analytic events failed to send. Will retry in " + backoffMs + "ms.");
        }

        // If there are still events left, schedule the next send
        if (!isSuccess || eventCount - events.size() > 0) {
            Logger.debug("EventService - Scheduling next event batch upload.");
            scheduleEventUpload(getNextSendDelay());
        }

        if (response != null) {
            preferences.setMaxTotalDbSize(response.getMaxTotalSize());
            preferences.setMaxBatchSize(response.getMaxBatchSize());
            preferences.setMaxWait(response.getMaxWait());
            preferences.setMinBatchInterval(response.getMinBatchInterval());
        }
    }

    /**
     * Gets the next upload delay in milliseconds.
     *
     * @return A delay in ms for the time the events should be sent.
     */
    private long getNextSendDelay() {
        AnalyticsPreferences preferences = UAirship.shared().getAnalytics().getPreferences();
        long nextSendTime = preferences.getLastSendTime() + preferences.getMinBatchInterval() + backoffMs;
        return Math.max(nextSendTime - System.currentTimeMillis(), 0);
    }

    /**
     * Schedule a batch event upload at a given time in the future.
     *
     * @param milliseconds The milliseconds from the current time to schedule the event upload.
     */
    private void scheduleEventUpload(final long milliseconds) {

        long sendTime = System.currentTimeMillis() + milliseconds;

        AnalyticsPreferences preferences = UAirship.shared().getAnalytics().getPreferences();
        AlarmManager alarmManager = (AlarmManager) getApplicationContext()
                .getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(getApplicationContext(), EventService.class);
        intent.setAction(EventService.ACTION_SEND);

        long previousScheduledTime = preferences.getScheduledSendTime();

        // Check if we should reschedule - previousAlarmTime is older than now or greater then the new send time
        boolean reschedule = previousScheduledTime < System.currentTimeMillis() || previousScheduledTime > sendTime;

        // Schedule the alarm if we need to either reschedule or an existing pending intent does not exist
        if (reschedule || PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_NO_CREATE) == null) {
            Logger.verbose("EventService - Scheduling event uploads in " + milliseconds + "ms.");

            PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            try {
                // Reschedule the intent
                alarmManager.set(AlarmManager.RTC, sendTime, pendingIntent);
                preferences.setScheduledSendTime(sendTime);
            } catch (SecurityException e) {
                Logger.error("EventService - Failed to schedule event uploads.", e);
                preferences.setScheduledSendTime(-1);
            }

        } else {
            Logger.verbose("EventService - Alarm already scheduled for an earlier time.");
        }
    }

    /**
     * Updates the advertising ID and limited ad tracking preference.
     */
    private void updateAdvertisingId() {
        Analytics analytics = UAirship.shared().getAnalytics();
        AssociatedIdentifiers associatedIdentifiers = analytics.getAssociatedIdentifiers();

        String advertisingId = associatedIdentifiers.getAdvertisingId();
        boolean limitedAdTrackingEnabled = associatedIdentifiers.isLimitAdTrackingEnabled();

        switch (UAirship.shared().getPlatformType()) {
            case UAirship.AMAZON_PLATFORM:
                advertisingId = Settings.Secure.getString(getContentResolver(), "advertising_id");
                limitedAdTrackingEnabled = Settings.Secure.getInt(getContentResolver(), "limit_ad_tracking", -1) == 0;
                break;

            case UAirship.ANDROID_PLATFORM:
                if (!PlayServicesUtils.isGoogleAdsDependencyAvailable()) {
                    break;
                }

                try {
                    AdvertisingIdClient.Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(getApplicationContext());
                    advertisingId = adInfo.getId();
                    limitedAdTrackingEnabled = adInfo.isLimitAdTrackingEnabled();
                } catch (IOException | GooglePlayServicesNotAvailableException | GooglePlayServicesRepairableException e) {
                    Logger.error("EventService - Failed to retrieve and update advertising ID.", e);
                    return;
                }
                break;
        }

        if (!UAStringUtil.equals(associatedIdentifiers.getAdvertisingId(), advertisingId) ||
                associatedIdentifiers.isLimitAdTrackingEnabled() != limitedAdTrackingEnabled) {
            analytics.editAssociatedIdentifiers().setAdvertisingId(advertisingId, limitedAdTrackingEnabled).apply();
        }

    }
}
