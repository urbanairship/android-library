/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

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

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;

import java.util.Map;

/**
 * The EventService is an IntentService designed to handle periodic analytics event
 * uploads and saving events to be uploaded.
 */
public class EventService extends IntentService {

    /**
     * Intent action to send an event.
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

    private static long backoffMs = 0;

    private EventAPIClient eventClient;

    public EventService() {
        this("EventService");
    }

    public EventService(String serviceName) {
        this(serviceName, new EventAPIClient());
    }

    EventService(String serviceName, EventAPIClient eventClient) {
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
        if (intent == null) {
            return;
        }

        Logger.verbose("EventService - Received intent: " + intent.getAction());

        if (ACTION_DELETE_ALL.equals(intent.getAction())) {
            Logger.info("Deleting all analytic events.");
            UAirship.shared().getAnalytics().getDataManager().deleteAllEvents();
            return;
        }

        if (ACTION_ADD.equals(intent.getAction())) {
            addEventFromIntent(intent);
        }

        // If the next send time is in the future, schedule
        // an upload at a later time
        if (getNextSendTime() > System.currentTimeMillis()) {
            scheduleEventUpload(getNextSendTime());
        } else {
            uploadEvents();
        }
    }

    /**
     * Adds an event from an intent to the database.
     *
     * @param intent An intent containing the event's content values to be added
     * to the database.
     */
    private void addEventFromIntent(Intent intent) {
        AnalyticsPreferences preferences = UAirship.shared().getAnalytics().getPreferences();
        EventDataManager dataManager = UAirship.shared().getAnalytics().getDataManager();

        String eventType = intent.getStringExtra(EXTRA_EVENT_TYPE);
        String eventId = intent.getStringExtra(EXTRA_EVENT_ID);
        String eventData = intent.getStringExtra(EXTRA_EVENT_DATA);
        String eventTimeStamp = intent.getStringExtra(EXTRA_EVENT_TIME_STAMP);
        String sessionId = intent.getStringExtra(EXTRA_EVENT_SESSION_ID);

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

        //in the case of a location event
        if (LocationEvent.TYPE.equals(eventType)) {
            long currentTime = System.currentTimeMillis();
            long lastSendTime = preferences.getLastSendTime();
            long sendDelta = currentTime - lastSendTime;
            long throttleDelta = UAirship.shared().getAirshipConfigOptions().backgroundReportingIntervalMS;

            //if we're in the background and we haven't passed the threshold, hold off on uploading
            if (!UAirship.shared().getAnalytics().isAppInForeground() && sendDelta < throttleDelta) {
                long minimumWait = throttleDelta - sendDelta;
                Logger.info("LocationEvent was inserted, but may not be updated until " + minimumWait + " ms have passed");
            }
        }
    }

    /**
     * Uploads events
     */
    private void uploadEvents() {
        AnalyticsPreferences preferences = UAirship.shared().getAnalytics().getPreferences();
        EventDataManager dataManager = UAirship.shared().getAnalytics().getDataManager();

        preferences.setLastSendTime(System.currentTimeMillis());

        final int eventCount = dataManager.getEventCount();

        if (eventCount <= 0) {
            Logger.debug("EventService - No events to send. Ending analytics upload.");
            return;
        }

        final int avgSize = dataManager.getDatabaseSize() / eventCount;

        //pull enough events to fill a batch (roughly)
        Map<String, String> events = dataManager.getEvents(preferences.getMaxBatchSize() / avgSize);

        EventResponse response = eventClient.sendEvents(events.values());

        boolean isSuccess = response != null && response.getStatus() == 200;

        if (isSuccess) {
            Logger.info("Analytic events uploaded succesfully.");
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
            scheduleEventUpload(getNextSendTime());
        }

        if (response != null) {
            preferences.setMaxTotalDbSize(response.getMaxTotalSize());
            preferences.setMaxBatchSize(response.getMaxBatchSize());
            preferences.setMaxWait(response.getMaxWait());
            preferences.setMinBatchInterval(response.getMinBatchInterval());
        }
    }

    /**
     * Gets the next upload send time
     *
     * @return A time in ms for the next time events can be uploaded.
     */
    private long getNextSendTime() {
        AnalyticsPreferences preferences = UAirship.shared().getAnalytics().getPreferences();
        return preferences.getLastSendTime() + preferences.getMinBatchInterval() + backoffMs;
    }

    /**
     * Schedule a batch event upload at a given time in the future.
     *
     * @param nextSendTime The time (in ms) when the next upload should occur.
     */
    private void scheduleEventUpload(final long nextSendTime) {
        Context ctx = UAirship.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);

        // Build a pending intent, cancel previous ones, then set an alarm
        Intent i = new Intent(ctx, EventService.class);
        i.setAction(EventService.ACTION_SEND);
        PendingIntent intent = PendingIntent.getService(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);

        //Set the new alarm
        alarmManager.set(AlarmManager.RTC, nextSendTime, intent);
    }
}
