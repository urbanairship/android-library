/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.content.Intent;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PendingResult;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.json.JsonValue;
import com.urbanairship.location.RegionEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anySet;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AutomationTest extends BaseTestCase {

    private static final int SLEEP_TIME = 25;
    
    private AutomationDataManager automationDataManager;
    private Automation automation;

    private Trigger customEventTrigger;
    private ActionScheduleInfo customEventActionSchedule;
    private Map<String, List<String>> updatesMap;

    @Before
    public void setUp() {
        automationDataManager = mock(AutomationDataManager.class);
        automation = new Automation(RuntimeEnvironment.application, UAirship.shared().getAnalytics(), automationDataManager, TestApplication.getApplication().preferenceDataStore);
        automation.init();

        customEventTrigger = Triggers.newCustomEventTriggerBuilder()
                                  .setCountGoal(2)
                                  .setEventName("name")
                                  .build();

        customEventActionSchedule = ActionScheduleInfo.newBuilder()
                                                      .addTrigger(customEventTrigger)
                                                      .addAction("test_action", JsonValue.wrap("action_value"))
                                                      .setGroup("group")
                                                      .setLimit(4)
                                                      .setStart(System.currentTimeMillis())
                                                      .setEnd(System.currentTimeMillis() + 10000)
                                                      .build();

        updatesMap = new HashMap<>();
        updatesMap.put(AutomationDataManager.SCHEDULES_TO_DELETE_QUERY, Collections.EMPTY_LIST);
        updatesMap.put(AutomationDataManager.SCHEDULES_TO_INCREMENT_QUERY, Collections.EMPTY_LIST);
        updatesMap.put(AutomationDataManager.TRIGGERS_TO_RESET_QUERY, Collections.EMPTY_LIST);
    }

    @After
    public void takeDown() {
        updatesMap.clear();
        automation.tearDown();
    }

    @Test
    public void testCustomEventMatch() throws Exception {
        when(automationDataManager.insertSchedules(Collections.singletonList(customEventActionSchedule))).thenReturn(Collections.singletonList(new ActionSchedule("automation id", customEventActionSchedule, 0)));
        String id  = automation.schedule(customEventActionSchedule).getId();

        assertEquals("automation id", id);

        TriggerEntry triggerEntry = new TriggerEntry(customEventTrigger.getType(), customEventTrigger.getGoal(), customEventTrigger.getPredicate(), "1", "automation id", 0.0);

        List<TriggerEntry> triggerEntries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            triggerEntries.add(new TriggerEntry(Trigger.CUSTOM_EVENT_COUNT, 5, Triggers.newCustomEventTriggerBuilder().setCountGoal(5).setEventName("other name").build().getPredicate(), String.valueOf(i), "id " + i, 0.0));
        }
        triggerEntries.add(triggerEntry);

        when(automationDataManager.getTriggers(Trigger.CUSTOM_EVENT_COUNT)).thenReturn(triggerEntries);
        when(automationDataManager.getSchedules(anySet())).thenReturn(Collections.singletonList(new ActionSchedule("automation id", customEventActionSchedule, 0)));

        new CustomEvent.Builder("name")
                .create()
                .track();

        Thread.sleep(SLEEP_TIME);

        verify(automationDataManager).getTriggers(anyInt());
        verify(automationDataManager, never()).getSchedules(anySet());

        updatesMap.put(String.format(AutomationDataManager.TRIGGERS_TO_INCREMENT_QUERY, 1.0), Collections.singletonList("1"));
        verify(automationDataManager).updateLists(updatesMap);
    }

    @Test
    public void testCustomEventValueMatch() throws Exception {
        // This will test that if the custom event value gets reduced to an integer in the action parsing process, a proper comparison will still be made in the value matching.
        String json = "{\"actions\":" +
                "{\"test_action\":\"action_value\"}," +
                "\"limit\": 5," +
                "\"group\": \"group\"," +
                "\"triggers\": [" +
                    "{\"type\": \"custom_event_value\"," +
                        "\"goal\": 4.0," +
                        "\"predicate\": {" +
                            "\"and\" : [" +
                                "{\"key\": \"event_name\",\"value\": {\"equals\": \"name\"}}," +
                                "{\"key\": \"event_value\",\"value\": {\"equals\": 5}}" +
                            "]" +
                        "}" +
                    "}" +
                "]}";

        ActionScheduleInfo actionScheduleInfo = ActionScheduleInfo.parseJson(JsonValue.parseString(json));
        Trigger trigger = actionScheduleInfo.getTriggers().get(0);
        TriggerEntry triggerEntry = new TriggerEntry(trigger.getType(), trigger.getGoal(), trigger.getPredicate(), "1", "automation id", 0.0);

        when(automationDataManager.insertSchedules(Collections.singletonList(actionScheduleInfo))).thenReturn(Collections.singletonList(new ActionSchedule("automation id", actionScheduleInfo, 0)));
        automation.schedule(actionScheduleInfo);

        when(automationDataManager.getTriggers(Trigger.CUSTOM_EVENT_VALUE)).thenReturn(Collections.singletonList(triggerEntry));
        when(automationDataManager.getSchedules(anySet())).thenReturn(Collections.singletonList(new ActionSchedule("automation id", actionScheduleInfo, 0)));

        new CustomEvent.Builder("name")
                .setEventValue(5.0)
                .create()
                .track();

        Thread.sleep(SLEEP_TIME);

        verify(automationDataManager, atLeastOnce()).getTriggers(anyInt());
        updatesMap.put(AutomationDataManager.SCHEDULES_TO_INCREMENT_QUERY, Collections.singletonList("automation id"));
        updatesMap.put(String.format(AutomationDataManager.TRIGGERS_TO_INCREMENT_QUERY, 5.0), Collections.EMPTY_LIST);
        updatesMap.put(AutomationDataManager.TRIGGERS_TO_RESET_QUERY, Collections.singletonList("1"));
        verify(automationDataManager).updateLists(updatesMap);
    }

    @Test
    public void testCustomEventNoMatch() throws Exception {
        when(automationDataManager.insertSchedules(Collections.singletonList(customEventActionSchedule))).thenReturn(Collections.singletonList(new ActionSchedule("automation id", customEventActionSchedule, 0)));
        String id  = automation.schedule(customEventActionSchedule).getId();

        assertEquals("automation id", id);

        TriggerEntry triggerEntry = new TriggerEntry(customEventTrigger.getType(), customEventTrigger.getGoal(), customEventTrigger.getPredicate(), "1", "automation id", 0.0);
        when(automationDataManager.getTriggers(Trigger.CUSTOM_EVENT_COUNT)).thenReturn(Collections.singletonList(triggerEntry));
        when(automationDataManager.getSchedules(anySet())).thenReturn(Collections.singletonList(new ActionSchedule("automation id", customEventActionSchedule, 0)));

        new CustomEvent.Builder("other name")
                .create()
                .track();

        Thread.sleep(SLEEP_TIME);

        verify(automationDataManager).getTriggers(anyInt());
        verify(automationDataManager, never()).getSchedules(anySet());

        updatesMap.put(String.format(AutomationDataManager.TRIGGERS_TO_INCREMENT_QUERY, 1.0), Collections.EMPTY_LIST);
        verify(automationDataManager).updateLists(updatesMap);
    }

    @Test
    public void testCustomEventScheduleFulfillment() throws Exception {
        when(automationDataManager.insertSchedules(Collections.singletonList(customEventActionSchedule))).thenReturn(Collections.singletonList(new ActionSchedule("automation id", customEventActionSchedule, 0)));
        String id  = automation.schedule(customEventActionSchedule).getId();

        assertEquals("automation id", id);

        TriggerEntry triggerEntry = new TriggerEntry(customEventTrigger.getType(), customEventTrigger.getGoal(), customEventTrigger.getPredicate(), "1", "customEventActionSchedule id", 1.0);
        when(automationDataManager.getTriggers(Trigger.CUSTOM_EVENT_COUNT)).thenReturn(Collections.singletonList(triggerEntry));
        when(automationDataManager.getSchedules(anySet())).thenReturn(Collections.singletonList(new ActionSchedule("automation id", customEventActionSchedule, 0)));

        new CustomEvent.Builder("name")
                .create()
                .track();

        Thread.sleep(15);

        verify(automationDataManager).getTriggers(anyInt());
        verify(automationDataManager).getSchedules(anySet());

        updatesMap.put(String.format(AutomationDataManager.TRIGGERS_TO_INCREMENT_QUERY, 1.0), Collections.EMPTY_LIST);
        updatesMap.put(AutomationDataManager.SCHEDULES_TO_INCREMENT_QUERY, Collections.singletonList("automation id"));
        updatesMap.put(AutomationDataManager.TRIGGERS_TO_RESET_QUERY, Collections.singletonList("1"));
        verify(automationDataManager).updateLists(updatesMap);
    }

    @Test
    public void testCustomEventScheduleLimitReached() throws Exception {
        when(automationDataManager.insertSchedules(Collections.singletonList(customEventActionSchedule))).thenReturn(Collections.singletonList(new ActionSchedule("automation id", customEventActionSchedule, 0)));
        String id  = automation.schedule(customEventActionSchedule).getId();

        assertEquals("automation id", id);

        TriggerEntry triggerEntry = new TriggerEntry(customEventTrigger.getType(), customEventTrigger.getGoal(), customEventTrigger.getPredicate(), "1", "automation id", 1.0);
        when(automationDataManager.getTriggers(Trigger.CUSTOM_EVENT_COUNT)).thenReturn(Collections.singletonList(triggerEntry));
        when(automationDataManager.getSchedules(anySet())).thenReturn(Collections.singletonList(new ActionSchedule("automation id", customEventActionSchedule, 3)));

        new CustomEvent.Builder("name")
                .create()
                .track();

        Thread.sleep(SLEEP_TIME);

        verify(automationDataManager).getTriggers(anyInt());
        verify(automationDataManager).getSchedules(anySet());

        updatesMap.put(String.format(AutomationDataManager.TRIGGERS_TO_INCREMENT_QUERY, 1.0), Collections.EMPTY_LIST);
        updatesMap.put(AutomationDataManager.SCHEDULES_TO_DELETE_QUERY, Collections.singletonList("automation id"));
        updatesMap.put(AutomationDataManager.TRIGGERS_TO_RESET_QUERY, Collections.EMPTY_LIST);
        verify(automationDataManager).updateLists(updatesMap);
    }

    @Test
    public void testCustomEventNoTriggers() throws Exception {
        when(automationDataManager.insertSchedules(Collections.singletonList(customEventActionSchedule))).thenReturn(Collections.singletonList(new ActionSchedule("automation id", customEventActionSchedule, 0)));
        String id  = automation.schedule(customEventActionSchedule).getId();

        assertEquals("automation id", id);

        when(automationDataManager.getTriggers(Trigger.CUSTOM_EVENT_COUNT)).thenReturn(Collections.EMPTY_LIST);

        new CustomEvent.Builder("name")
                .create()
                .track();

        Thread.sleep(SLEEP_TIME);

        verify(automationDataManager).getTriggers(anyInt());
        verify(automationDataManager, never()).getSchedules(anySet());
        verify(automationDataManager, never()).updateLists(anyMap());
    }

    @Test
    public void testEnterRegionEvent() throws Exception {
        Trigger enter = Triggers.newEnterRegionTriggerBuilder()
                .setRegionId("region_id")
                .setGoal(2)
                .build();

        ActionScheduleInfo schedule = ActionScheduleInfo.newBuilder()
                .setLimit(5)
                .setGroup("group")
                .addTrigger(enter)
                .addAction("test_action", JsonValue.wrap("action_value"))
                .build();

        when(automationDataManager.insertSchedules(Collections.singletonList(schedule))).thenReturn(Collections.singletonList(new ActionSchedule("automation id", schedule, 0)));
        String id  = automation.schedule(schedule).getId();
        assertEquals("automation id", id);

        TriggerEntry enterEntry = new TriggerEntry(enter.getType(), enter.getGoal(), enter.getPredicate(), "1", "automation id", 0.0);
        when(automationDataManager.getTriggers(Trigger.REGION_ENTER)).thenReturn(Collections.singletonList(enterEntry));

        RegionEvent event = new RegionEvent("region_id", "source", RegionEvent.BOUNDARY_EVENT_ENTER);
        UAirship.shared().getAnalytics().addEvent(event);

        Thread.sleep(SLEEP_TIME);

        verify(automationDataManager).getTriggers(Trigger.REGION_ENTER);
        verify(automationDataManager, never()).getTriggers(Trigger.REGION_EXIT);

        updatesMap.put(String.format(AutomationDataManager.TRIGGERS_TO_INCREMENT_QUERY, 1.0), Collections.singletonList("1"));
        verify(automationDataManager).updateLists(updatesMap);
    }

    @Test
    public void testExitRegionEvent() throws Exception {
        Trigger trigger = Triggers.newExitRegionTriggerBuilder()
                               .setRegionId("region_id")
                               .setGoal(2)
                               .build();

        ActionScheduleInfo schedule = ActionScheduleInfo.newBuilder()
                                                        .setLimit(5)
                                                        .setGroup("group")
                                                        .addTrigger(trigger)
                                                        .addAction("test_action", JsonValue.wrap("action_value"))
                                                        .build();

        when(automationDataManager.insertSchedules(Collections.singletonList(schedule))).thenReturn(Collections.singletonList(new ActionSchedule("automation id", schedule, 0)));
        String id  = automation.schedule(schedule).getId();
        assertEquals("automation id", id);

        TriggerEntry triggerEntry = new TriggerEntry(trigger.getType(), trigger.getGoal(), trigger.getPredicate(), "1", "automation id", 0.0);
        when(automationDataManager.getTriggers(Trigger.REGION_EXIT)).thenReturn(Collections.singletonList(triggerEntry));

        RegionEvent event = new RegionEvent("region_id", "source", RegionEvent.BOUNDARY_EVENT_EXIT);
        UAirship.shared().getAnalytics().addEvent(event);

        Thread.sleep(SLEEP_TIME);

        verify(automationDataManager, never()).getTriggers(Trigger.REGION_ENTER);
        verify(automationDataManager).getTriggers(Trigger.REGION_EXIT);

        updatesMap.put(String.format(AutomationDataManager.TRIGGERS_TO_INCREMENT_QUERY, 1.0), Collections.singletonList("1"));
        verify(automationDataManager).updateLists(updatesMap);
    }

    @Test
    public void testAppForegroundEvent() throws Exception {
        Trigger trigger = Triggers.newForegroundTriggerBuilder()
                .setGoal(3)
                .build();

        ActionScheduleInfo schedule = ActionScheduleInfo.newBuilder()
                .addAction("test_action", JsonValue.wrap("action_value"))
                .addTrigger(trigger)
                .setGroup("group")
                .setLimit(5)
                .build();


        when(automationDataManager.insertSchedules(Collections.singletonList(schedule))).thenReturn(Collections.singletonList(new ActionSchedule("automation id", schedule, 0)));
        String id  = automation.schedule(schedule).getId();

        assertEquals("automation id", id);

        TriggerEntry triggerEntry = new TriggerEntry(trigger.getType(), trigger.getGoal(), trigger.getPredicate(), "1", "automation id", 0.0);
        when(automationDataManager.getTriggers(Trigger.LIFE_CYCLE_FOREGROUND)).thenReturn(Collections.singletonList(triggerEntry));
        when(automationDataManager.getSchedules(anySet())).thenReturn(Collections.singletonList(new ActionSchedule("automation id", schedule, 0)));

        LocalBroadcastManager.getInstance(RuntimeEnvironment.application)
                             .sendBroadcast(new Intent(Analytics.ACTION_APP_FOREGROUND));

        Thread.sleep(SLEEP_TIME);

        verify(automationDataManager).getTriggers(anyInt());
        verify(automationDataManager, never()).getSchedules(anySet());

        updatesMap.put(String.format(AutomationDataManager.TRIGGERS_TO_INCREMENT_QUERY, 1.0), Collections.singletonList("1"));
        verify(automationDataManager).updateLists(updatesMap);
    }

    @Test
    public void testAppBackgroundEvent() throws Exception {
        Trigger trigger = Triggers.newBackgroundTriggerBuilder()
                                  .setGoal(3)
                                  .build();

        ActionScheduleInfo schedule = ActionScheduleInfo.newBuilder()
                                                        .addAction("test_action", JsonValue.wrap("action_value"))
                                                        .addTrigger(trigger)
                                                        .setGroup("group")
                                                        .setLimit(5)
                                                        .build();


        when(automationDataManager.insertSchedules(Collections.singletonList(schedule))).thenReturn(Collections.singletonList(new ActionSchedule("automation id", schedule, 0)));
        String id  = automation.schedule(schedule).getId();

        assertEquals("automation id", id);

        TriggerEntry triggerEntry = new TriggerEntry(trigger.getType(), trigger.getGoal(), trigger.getPredicate(), "1", "automation id", 0.0);
        when(automationDataManager.getTriggers(Trigger.LIFE_CYCLE_BACKGROUND)).thenReturn(Collections.singletonList(triggerEntry));
        when(automationDataManager.getSchedules(anySet())).thenReturn(Collections.singletonList(new ActionSchedule("automation id", schedule, 0)));

        LocalBroadcastManager.getInstance(RuntimeEnvironment.application)
                             .sendBroadcast(new Intent(Analytics.ACTION_APP_BACKGROUND));

        Thread.sleep(SLEEP_TIME);

        verify(automationDataManager).getTriggers(anyInt());
        verify(automationDataManager, never()).getSchedules(anySet());

        updatesMap.put(String.format(AutomationDataManager.TRIGGERS_TO_INCREMENT_QUERY, 1.0), Collections.singletonList("1"));
        verify(automationDataManager).updateLists(updatesMap);
    }

    @Test
    public void testScreenEvent() throws Exception {
        Trigger trigger = Triggers.newScreenTriggerBuilder()
                                  .setGoal(3)
                                  .setScreenName("screen")
                                  .build();

        ActionScheduleInfo schedule = ActionScheduleInfo.newBuilder()
                                                        .addAction("test_action", JsonValue.wrap("action_value"))
                                                        .addTrigger(trigger)
                                                        .setGroup("group")
                                                        .setLimit(5)
                                                        .build();


        when(automationDataManager.insertSchedules(Collections.singletonList(schedule))).thenReturn(Collections.singletonList(new ActionSchedule("automation id", schedule, 0)));
        String id  = automation.schedule(schedule).getId();

        assertEquals("automation id", id);

        TriggerEntry triggerEntry = new TriggerEntry(trigger.getType(), trigger.getGoal(), trigger.getPredicate(), "1", "automation id", 0.0);
        when(automationDataManager.getTriggers(Trigger.SCREEN_VIEW)).thenReturn(Collections.singletonList(triggerEntry));
        when(automationDataManager.getSchedules(anySet())).thenReturn(Collections.singletonList(new ActionSchedule("automation id", schedule, 0)));

        UAirship.shared().getAnalytics().trackScreen("screen");

        Thread.sleep(SLEEP_TIME);

        verify(automationDataManager).getTriggers(anyInt());
        verify(automationDataManager, never()).getSchedules(anySet());

        updatesMap.put(String.format(AutomationDataManager.TRIGGERS_TO_INCREMENT_QUERY, 1.0), Collections.singletonList("1"));
        verify(automationDataManager).updateLists(updatesMap);
    }

    @Test
    public void testExpired() throws Exception {
        Trigger trigger = Triggers.newScreenTriggerBuilder()
                                  .setGoal(3)
                                  .setScreenName("screen")
                                  .build();

        ActionScheduleInfo schedule = ActionScheduleInfo.newBuilder()
                                                        .addAction("test_action", JsonValue.wrap("action_value"))
                                                        .addTrigger(trigger)
                                                        .setGroup("group")
                                                        .setLimit(5)
                                                        .setEnd(System.currentTimeMillis() - 100)
                                                        .build();

        when(automationDataManager.insertSchedules(Collections.singletonList(schedule))).thenReturn(Collections.singletonList(new ActionSchedule("automation id", schedule, 0)));
        String id  = automation.schedule(schedule).getId();

        assertEquals("automation id", id);

        TriggerEntry triggerEntry = new TriggerEntry(trigger.getType(), trigger.getGoal(), trigger.getPredicate(), "1", "automation id", 2.0);
        when(automationDataManager.getTriggers(Trigger.SCREEN_VIEW)).thenReturn(Collections.singletonList(triggerEntry));
        when(automationDataManager.getSchedules(anySet())).thenReturn(Collections.singletonList(new ActionSchedule("automation id", schedule, 0)));

        UAirship.shared().getAnalytics().trackScreen("screen");

        Thread.sleep(SLEEP_TIME);

        verify(automationDataManager).getTriggers(anyInt());
        verify(automationDataManager).getSchedules(anySet());

        updatesMap.put(String.format(AutomationDataManager.TRIGGERS_TO_INCREMENT_QUERY, 1.0), Collections.EMPTY_LIST);
        updatesMap.put(AutomationDataManager.SCHEDULES_TO_DELETE_QUERY, Collections.singletonList("automation id"));
        verify(automationDataManager).updateLists(updatesMap);
    }

    @Test
    public void testScheduleAsync() throws Exception {
        when(automationDataManager.insertSchedules(Collections.singletonList(customEventActionSchedule))).thenReturn(Collections.singletonList(new ActionSchedule("automation id", customEventActionSchedule, 0)));

        final CountDownLatch latch = new CountDownLatch(1);
        automation.scheduleAsync(customEventActionSchedule, new PendingResult.ResultCallback<ActionSchedule>() {
            @Override
            public void onResult(@Nullable ActionSchedule result) {
                latch.countDown();
            }
        });

        latch.await();
        verify(automationDataManager).insertSchedules(Collections.singletonList(customEventActionSchedule));
    }

    @Test
    public void testInactivityWithoutSchedules() throws Exception {
        when(automationDataManager.insertSchedules(Collections.singletonList(customEventActionSchedule))).thenReturn(Collections.singletonList(new ActionSchedule("automation id", customEventActionSchedule, 0)));

        new CustomEvent.Builder("name")
                .create()
                .track();

        Thread.sleep(SLEEP_TIME);

        verify(automationDataManager, never()).getTriggers(anyInt());
        automation.schedule(customEventActionSchedule);

        new CustomEvent.Builder("name")
                .create()
                .track();

        Thread.sleep(SLEEP_TIME);

        verify(automationDataManager).getTriggers(anyInt());
    }
}
