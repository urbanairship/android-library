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
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.location.RegionEvent;
import com.urbanairship.push.PushManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.verification.Times;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowAlarmManager.ScheduledAlarm;
import org.robolectric.shadows.ShadowPendingIntent;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class EventServiceTest extends BaseTestCase {

    EventService service;
    EventApiClient client;
    EventDataManager dataManager;
    AnalyticsPreferences preferences;
    PushManager pushManager;
    String channelId;

    @Before
    public void setUp() {
        pushManager = mock(PushManager.class);
        preferences = mock(AnalyticsPreferences.class);
        dataManager = mock(EventDataManager.class);

        Analytics analytics = mock(Analytics.class);
        when(analytics.getDataManager()).thenReturn(dataManager);
        when(analytics.getPreferences()).thenReturn(preferences);

        TestApplication.getApplication().setAnalytics(analytics);
        TestApplication.getApplication().setPushManager(pushManager);

        Mockito.when(pushManager.getChannelId()).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                return channelId;
            }
        });
        channelId = "some channel ID";

        client = mock(EventApiClient.class);

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
     * Tests adding an event from an intent passed the next send time adds the event and schedules
     * a send in 10 seconds.
     */
    @Test
    public void testAddEventAfterNextSendTime() {
        Intent intent = new Intent(EventService.ACTION_ADD);
        intent.putExtra(EventService.EXTRA_EVENT_TYPE, "some-type");
        intent.putExtra(EventService.EXTRA_EVENT_ID, "event id");
        intent.putExtra(EventService.EXTRA_EVENT_TIME_STAMP, "100");
        intent.putExtra(EventService.EXTRA_EVENT_DATA, "DATA!");
        intent.putExtra(EventService.EXTRA_EVENT_SESSION_ID, "session id");
        intent.putExtra(EventService.EXTRA_EVENT_PRIORITY, Event.NORMAL_PRIORITY);

        service.onHandleIntent(intent);
        // Verify it was added to the data manager
        Mockito.verify(dataManager).insertEvent("some-type", "DATA!", "event id", "session id", "100");

        // Verify we add an event.
        Mockito.verify(dataManager, new Times(1)).insertEvent("some-type", "DATA!", "event id", "session id", "100");


        // Reset last send time, should schedule the event in 10 seconds
        when(preferences.getLastSendTime()).thenReturn(0l);

        // Check it schedules an upload
        AlarmManager alarmManager = (AlarmManager) RuntimeEnvironment.application.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = Shadows.shadowOf(alarmManager);
        ScheduledAlarm alarm = shadowAlarmManager.getNextScheduledAlarm();

        ShadowPendingIntent shadowPendingIntent = Shadows.shadowOf(alarm.operation);
        assertTrue(shadowPendingIntent.isServiceIntent());
        assertEquals(EventService.ACTION_SEND, shadowPendingIntent.getSavedIntent().getAction());
        assertNotNull("Alarm should be scheduled when region event is added", alarm);

        // Verify the alarm is within 10 second
        assertTrue(alarm.triggerAtTime <= System.currentTimeMillis() + 10000);
    }

    /**
     * Tests adding an event from an intent passed the next send time adds the event and schedules
     * a send for the next send time.
     */
    @Test
    public void testAddEventBeforeNextSendTime() {
        Intent intent = new Intent(EventService.ACTION_ADD);
        intent.putExtra(EventService.EXTRA_EVENT_TYPE, "some-type");
        intent.putExtra(EventService.EXTRA_EVENT_ID, "event id");
        intent.putExtra(EventService.EXTRA_EVENT_TIME_STAMP, "100");
        intent.putExtra(EventService.EXTRA_EVENT_DATA, "DATA!");
        intent.putExtra(EventService.EXTRA_EVENT_SESSION_ID, "session id");
        intent.putExtra(EventService.EXTRA_EVENT_PRIORITY, Event.NORMAL_PRIORITY);

        service.onHandleIntent(intent);
        // Verify it was added to the data manager
        Mockito.verify(dataManager).insertEvent("some-type", "DATA!", "event id", "session id", "100");

        // Verify we add an event.
        Mockito.verify(dataManager, new Times(1)).insertEvent("some-type", "DATA!", "event id", "session id", "100");


        // Set the last send time to the current time so the next send time is minBatchInterval
        when(preferences.getLastSendTime()).thenReturn(System.currentTimeMillis());

        // Set the minBatchInterval to 20 seconds
        when(preferences.getMinBatchInterval()).thenReturn(20000);


        // Check it schedules an upload
        AlarmManager alarmManager = (AlarmManager) RuntimeEnvironment.application.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = Shadows.shadowOf(alarmManager);
        ScheduledAlarm alarm = shadowAlarmManager.getNextScheduledAlarm();

        ShadowPendingIntent shadowPendingIntent = Shadows.shadowOf(alarm.operation);
        assertTrue(shadowPendingIntent.isServiceIntent());
        assertEquals(EventService.ACTION_SEND, shadowPendingIntent.getSavedIntent().getAction());
        assertNotNull("Alarm should be scheduled when region event is added", alarm);

        // Verify the alarm is within 20 second
        assertTrue(alarm.triggerAtTime <= System.currentTimeMillis() + 20000);
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
        AlarmManager alarmManager = (AlarmManager) RuntimeEnvironment.application.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = Shadows.shadowOf(alarmManager);
        ScheduledAlarm alarm = shadowAlarmManager.getNextScheduledAlarm();
        assertNotNull("Alarm should be schedule for more uploads", alarm);
    }

    /**
     * Test sending events when there's no channel ID present
     */
    @Test
    public void testSendingWithNoChannelID() {

        // Return null when channel ID is expected
        Mockito.when(pushManager.getChannelId()).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                return null;
            }
        });

        Map<String, String> events = new HashMap<>();
        events.put("firstEvent", "{ 'firstEventBody' }");

        // Satisfy event count check to avoid early return.
        when(dataManager.getEventCount()).thenReturn(1);
        // Return the event when it asks for 1
        when(dataManager.getEvents(1)).thenReturn(events);

        // Start the upload process
        Intent intent = new Intent(EventService.ACTION_SEND);
        service.onHandleIntent(intent);

        // Verify uploadEvents returns early when no channel ID is present.
        Mockito.verify(client, never()).sendEvents(events.values());
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
        AlarmManager alarmManager = (AlarmManager) RuntimeEnvironment.application.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = Shadows.shadowOf(alarmManager);
        ScheduledAlarm alarm = shadowAlarmManager.getNextScheduledAlarm();
        assertNotNull("Alarm should be scheduled when upload fails", alarm);
    }

    /**
     * Test adding a region event results in a scheduled alarm
     */
    @Test
    public void testAddingHighPriorityEvents() {
        Intent intent = new Intent(EventService.ACTION_ADD);
        intent.putExtra(EventService.EXTRA_EVENT_TYPE, RegionEvent.TYPE);
        intent.putExtra(EventService.EXTRA_EVENT_ID, "event id");
        intent.putExtra(EventService.EXTRA_EVENT_TIME_STAMP, "100");
        intent.putExtra(EventService.EXTRA_EVENT_DATA, "Region Event Data");
        intent.putExtra(EventService.EXTRA_EVENT_SESSION_ID, "session id");
        intent.putExtra(EventService.EXTRA_EVENT_PRIORITY, Event.HIGH_PRIORITY);

        // Set last send time to year 3005 so we don't upload immediately
        when(preferences.getLastSendTime()).thenReturn(32661446400000l);

        service.onHandleIntent(intent);

        // Check it schedules an upload
        AlarmManager alarmManager = (AlarmManager) RuntimeEnvironment.application.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = Shadows.shadowOf(alarmManager);
        ScheduledAlarm alarm = shadowAlarmManager.getNextScheduledAlarm();

        ShadowPendingIntent shadowPendingIntent = Shadows.shadowOf(alarm.operation);
        assertTrue(shadowPendingIntent.isServiceIntent());
        assertEquals(EventService.ACTION_SEND, shadowPendingIntent.getSavedIntent().getAction());
        assertNotNull("Alarm should be scheduled when region event is added", alarm);

        // Verify the alarm is within a second
        assertTrue(alarm.triggerAtTime <= System.currentTimeMillis() + 1000);
    }

    /**
     * Test DELETE_ALL intent action deletes all events.
     */
    @Test
    public void testDeleteAll() {
        Intent intent = new Intent(EventService.ACTION_DELETE_ALL);
        service.onHandleIntent(intent);
        Mockito.verify(dataManager).deleteAllEvents();
    }
}
