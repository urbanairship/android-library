/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

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
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;

import com.urbanairship.RobolectricGradleTestRunner;
import com.urbanairship.TestApplication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.internal.verification.Times;
import org.robolectric.Robolectric;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowAlarmManager.ScheduledAlarm;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)
public class EventServiceTest {

    EventService service;
    EventAPIClient client;
    EventDataManager dataManager;
    AnalyticsPreferences preferences;

    @Before
    public void setUp() {
        preferences = mock(AnalyticsPreferences.class);
        dataManager = mock(EventDataManager.class);

        Analytics analytics = mock(Analytics.class);
        when(analytics.getDataManager()).thenReturn(dataManager);
        when(analytics.getPreferences()).thenReturn(preferences);

        TestApplication.getApplication().setAnalytics(analytics);

        client = mock(EventAPIClient.class);

        // Extend it to make onHandleIntent public so we can call it directly
        service = new EventService("EventService", client) {
            @Override
            public void onHandleIntent(Intent intent) {
                super.onHandleIntent(intent);
            }
        };
        service.onCreate();
    }

    /**
     * Tests adding an event from an intent
     */
    @Test
    public void testAddEvent() {
        Intent intent = new Intent(EventService.ACTION_ADD);
        intent.putExtra(EventService.EXTRA_EVENT_TYPE, "some-type");
        intent.putExtra(EventService.EXTRA_EVENT_ID, "event id");
        intent.putExtra(EventService.EXTRA_EVENT_TIME_STAMP, "100");
        intent.putExtra(EventService.EXTRA_EVENT_DATA, "DATA!");
        intent.putExtra(EventService.EXTRA_EVENT_SESSION_ID, "session id");

        service.onHandleIntent(intent);

        // Verify it was added to the data manager
        Mockito.verify(dataManager).insertEvent("some-type", "DATA!", "event id", "session id",  "100");

        // Verify that it tried to do the upload
        Mockito.verify(preferences).setLastSendTime(Mockito.anyLong());
    }

    /**
     * Tests adding an event from intent no-ops when the event data is empty.
     */
    @Test
    public void testAddEventEmptyData() {
        ContentValues values = new ContentValues();
        values.put("some-key", "some-value");

        Intent intent = new Intent(EventService.ACTION_ADD);

        service.onHandleIntent(intent);

        // Verify we don't add any events.
        Mockito.verify(dataManager, new Times(0)).insertEvent(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    /**
     * Tests sending events
     */
    @Test
    public void testSendingEvents() {
        Map<String, String> events = new HashMap<>();
        events.put("firstEvent", "{ 'firstEventBody' }");

        // Set up data manager to return 2 count for events.
        // Note: we only have one event, but it should only ask for one to upload
        // having it return 2 will make it schedule to upload events in the future
        when(dataManager.getEventCount()).thenReturn(2);

        // Return 200 bytes in size.  It should only be able to do 100 bytes so only
        // the first event.
        when(dataManager.getDatabaseSize()).thenReturn(200);

        // Return the event when it asks for 1
        when(dataManager.getEvents(1)).thenReturn(events);

        // Preferences
        when(preferences.getMaxBatchSize()).thenReturn(100);

        // Set up the response
        EventResponse response = mock(EventResponse.class);
        when(response.getStatus()).thenReturn(200);
        when(response.getMaxTotalSize()).thenReturn(200);
        when(response.getMaxBatchSize()).thenReturn(300);
        when(response.getMaxWait()).thenReturn(400);
        when(response.getMinBatchInterval()).thenReturn(100);

        // Return the response
        when(client.sendEvents(events.values())).thenReturn(response);

        // Start the upload process
        Intent intent = new Intent(EventService.ACTION_SEND);
        service.onHandleIntent(intent);

        // Check clients receives the events
        Mockito.verify(client).sendEvents(events.values());

        // Check data manager deletes events
        Mockito.verify(dataManager).deleteEvents(events.keySet());

        // Verify responses are being saved
        Mockito.verify(preferences).setMaxTotalDbSize(200);
        Mockito.verify(preferences).setMaxBatchSize(300);
        Mockito.verify(preferences).setMaxWait(400);
        Mockito.verify(preferences).setMinBatchInterval(100);

        // Check it schedules another upload
        AlarmManager alarmManager = (AlarmManager) Robolectric.application.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = Robolectric.shadowOf(alarmManager);
        ScheduledAlarm alarm = shadowAlarmManager.getNextScheduledAlarm();
        assertNotNull("Alarm should be schedule for more uploads", alarm);
    }

    /**
     * Test sending events when the upload fails
     */
    @Test
    public void testSendEventsFails() {
        Map<String, String> events = new HashMap<>();
        events.put("firstEvent", "{ 'firstEventBody' }");
        when(dataManager.getEventCount()).thenReturn(1);
        when(dataManager.getDatabaseSize()).thenReturn(100);
        when(dataManager.getEvents(1)).thenReturn(events);
        when(preferences.getMaxBatchSize()).thenReturn(100);

        // Return a null response
        when(client.sendEvents(events.values())).thenReturn(null);

        Intent intent = new Intent(EventService.ACTION_SEND);
        service.onHandleIntent(intent);

        Mockito.verify(client).sendEvents(events.values());

        // If it fails, it should skip deleting events
        Mockito.verify(dataManager, Mockito.never()).deleteEvents(events.keySet());

        // Check it schedules another upload
        AlarmManager alarmManager = (AlarmManager) Robolectric.application.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = Robolectric.shadowOf(alarmManager);
        ScheduledAlarm alarm = shadowAlarmManager.getNextScheduledAlarm();
        assertNotNull("Alarm should be schedule when upload fails", alarm);
    }

    /**
     * Test sending events when nextSendTime is in the future
     */
    @Test
    public void testSendEventsBeforeNextSendTime() {
        Map<String, String> events = new HashMap<>();
        events.put("firstEvent", "{ 'firstEventBody' }");
        when(dataManager.getEventCount()).thenReturn(1);
        when(dataManager.getDatabaseSize()).thenReturn(100);
        when(dataManager.getEvents(1)).thenReturn(events);
        when(preferences.getMaxBatchSize()).thenReturn(100);

        // Make next send time 30 seconds in the future
        when(preferences.getLastSendTime()).thenReturn(System.currentTimeMillis());
        when(preferences.getMinBatchInterval()).thenReturn(30000);

        Intent intent = new Intent(EventService.ACTION_SEND);
        service.onHandleIntent(intent);

        Mockito.verify(client, Mockito.never()).sendEvents(events.values());
        Mockito.verify(dataManager, Mockito.never()).getEvents(Mockito.anyInt());

        // Check it schedules another upload
        AlarmManager alarmManager = (AlarmManager) Robolectric.application.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = Robolectric.shadowOf(alarmManager);
        ScheduledAlarm alarm = shadowAlarmManager.getNextScheduledAlarm();
        assertNotNull("Alarm should be schedule when upload fails", alarm);
    }
}
