/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.os.Looper;
import android.support.annotation.Nullable;
import android.widget.Toast;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PendingResult;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.actions.ToastAction;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.json.JsonValue;
import com.urbanairship.location.CircularRegion;
import com.urbanairship.location.ProximityRegion;
import com.urbanairship.location.RegionEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AutomationTest extends BaseTestCase {

    private AutomationDataManager automationDataManager;
    private Automation automation;
    private TestActivityMonitor activityMonitor;

    private Trigger customEventTrigger;
    private ActionScheduleInfo customEventActionSchedule;

    @Before
    public void setUp() {
        activityMonitor = new TestActivityMonitor();
        activityMonitor.register();

        automationDataManager = mock(AutomationDataManager.class);
        automation = new Automation(UAirship.shared().getAnalytics(), automationDataManager, TestApplication.getApplication().preferenceDataStore, activityMonitor);
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

    }

    @After
    public void takeDown() {
        automation.tearDown();
        activityMonitor.unregister();
    }

    @Test
    public void testCustomEventMatch() throws Exception {
        automation.schedule(customEventActionSchedule).getId();
        verify(automationDataManager).saveSchedules(ArgumentMatchers.<ScheduleEntry>anyCollection());

        Trigger trigger = new Trigger(customEventTrigger.getType(), customEventTrigger.getGoal(), customEventTrigger.getPredicate());
        TriggerEntry triggerEntry = new TriggerEntry(trigger, "schedule id", false);

        List<TriggerEntry> triggerEntries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            triggerEntries.add(new TriggerEntry(new Trigger(Trigger.CUSTOM_EVENT_COUNT, 5, Triggers.newCustomEventTriggerBuilder().setCountGoal(5).setEventName("other name").build().getPredicate()), "id " + i, false));
        }
        triggerEntries.add(triggerEntry);

        ScheduleEntry scheduleEntry = new ScheduleEntry(new ActionSchedule("schedule id", customEventActionSchedule));
        when(automationDataManager.getActiveTriggerEntries(Trigger.CUSTOM_EVENT_COUNT)).thenReturn(triggerEntries);
        when(automationDataManager.getScheduleEntries(Collections.singleton("schedule id"))).thenReturn(Collections.singletonList(scheduleEntry));

        new CustomEvent.Builder("name")
                .create()
                .track();

        runLooperTasks();

        verify(automationDataManager).getActiveTriggerEntries(Trigger.CUSTOM_EVENT_COUNT);
        verify(automationDataManager).saveTriggers(Collections.singletonList(triggerEntry));
    }

    @Test
    public void testCustomEventValueMatch() throws Exception {
        // This will test that if the custom event value gets reduced to an integer in the action parsing process, a proper comparison will still be made in the value matching.
        String json = "{\"actions\":" +
                "{\"test_action\":\"action_value\"}," +
                "\"limit\": 5," +
                "\"group\": \"group\"," +
                "\"triggers\": [" +
                    "{" +
                        "\"type\": \"custom_event_value\"," +
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
        TriggerEntry triggerEntry = new TriggerEntry(trigger, "schedule id", false);

        automation.schedule(actionScheduleInfo);
        verify(automationDataManager).saveSchedules(ArgumentMatchers.<ScheduleEntry>anyCollection());

        ScheduleEntry scheduleEntry = new ScheduleEntry(new ActionSchedule("schedule id", actionScheduleInfo));
        when(automationDataManager.getActiveTriggerEntries(Trigger.CUSTOM_EVENT_VALUE)).thenReturn(Collections.singletonList(triggerEntry));
        when(automationDataManager.getScheduleEntries(Collections.singleton("schedule id"))).thenReturn(Collections.singletonList(scheduleEntry));

        new CustomEvent.Builder("name")
                .setEventValue(5.0)
                .create()
                .track();

        runLooperTasks();

        verify(automationDataManager).getActiveTriggerEntries(Trigger.CUSTOM_EVENT_COUNT);
        assertEquals(scheduleEntry.getCount(), 1);
        verify(automationDataManager).saveSchedules(Collections.singleton(scheduleEntry));
        verify(automationDataManager).saveTriggers(Collections.singletonList(triggerEntry));
    }

    @Test
    public void testRegionEventValueMatch() throws Exception {
        String json = "{\"actions\":" +
                "{\"test_action\":\"action_value\"}," +
                "\"limit\": 5," +
                "\"group\": \"group\"," +
                "\"triggers\": [" +
                    "{" +
                        "\"type\": \"region_enter\"," +
                        "\"goal\": 4.0," +
                        "\"predicate\": {" +
                            "\"and\" : [" +
                                "{\"key\": \"region_id\",\"value\": {\"equals\": \"region_id\"}}," +
                                "{\"key\": \"latitude\",\"scope\":[\"proximity\"],\"value\": {\"equals\": 5.0}}," +
                                "{\"key\": \"source\",\"value\": {\"equals\": \"test_source\"}}" +
                            "]" +
                        "}" +
                    "}" +
                "]}";

        ActionScheduleInfo actionScheduleInfo = ActionScheduleInfo.parseJson(JsonValue.parseString(json));
        Trigger trigger = actionScheduleInfo.getTriggers().get(0);
        TriggerEntry triggerEntry = new TriggerEntry(trigger, "schedule id", false);

        automation.schedule(actionScheduleInfo);
        verify(automationDataManager).saveSchedules(ArgumentMatchers.<ScheduleEntry>anyCollection());


        ScheduleEntry scheduleEntry = new ScheduleEntry(new ActionSchedule("schedule id", actionScheduleInfo));
        when(automationDataManager.getActiveTriggerEntries(Trigger.REGION_ENTER)).thenReturn(Collections.singletonList(triggerEntry));
        when(automationDataManager.getScheduleEntries(Collections.singleton("schedule id"))).thenReturn(Collections.singletonList(scheduleEntry));

        RegionEvent event = new RegionEvent("region_id", "test_source", RegionEvent.BOUNDARY_EVENT_ENTER);
        ProximityRegion proximityRegion = new ProximityRegion("id", 2, 3);
        proximityRegion.setCoordinates(5.0, 6.0);
        CircularRegion circularRegion = new CircularRegion(1.0, 2.0, 3.0);
        event.setProximityRegion(proximityRegion);
        event.setCircularRegion(circularRegion);

        UAirship.shared().getAnalytics().addEvent(event);

        runLooperTasks();

        verify(automationDataManager).getActiveTriggerEntries(Trigger.REGION_ENTER);
        verify(automationDataManager).saveTriggers(Collections.singletonList(triggerEntry));
    }

    @Test
    public void testCustomEventNoMatch() throws Exception {
        automation.schedule(customEventActionSchedule);
        verify(automationDataManager).saveSchedules(ArgumentMatchers.<ScheduleEntry>anyCollection());

        TriggerEntry triggerEntry = new TriggerEntry(customEventTrigger, "schedule id", false);
        ScheduleEntry scheduleEntry = new ScheduleEntry(new ActionSchedule("schedule id", customEventActionSchedule));
        when(automationDataManager.getActiveTriggerEntries(Trigger.CUSTOM_EVENT_COUNT)).thenReturn(Collections.singletonList(triggerEntry));
        when(automationDataManager.getScheduleEntries(Collections.singleton("schedule id"))).thenReturn(Collections.singletonList(scheduleEntry));

        new CustomEvent.Builder("other name")
                .create()
                .track();

        runLooperTasks();

        verify(automationDataManager).getActiveTriggerEntries(Trigger.CUSTOM_EVENT_COUNT);
        verify(automationDataManager, never()).getScheduleEntries(ArgumentMatchers.<String>anySet());
        verify(automationDataManager).saveTriggers(Collections.<TriggerEntry>emptyList());
    }

    @Test
    public void testCustomEventScheduleFulfillment() throws Exception {
        automation.schedule(customEventActionSchedule);
        verify(automationDataManager).saveSchedules(ArgumentMatchers.<ScheduleEntry>anyCollection());

        Trigger customEventTrigger = Triggers.newCustomEventTriggerBuilder()
             .setCountGoal(1)
             .setEventName("name")
             .build();


        TriggerEntry triggerEntry = new TriggerEntry(customEventTrigger, "schedule id", false);
        ScheduleEntry scheduleEntry = new ScheduleEntry(new ActionSchedule("schedule id", customEventActionSchedule));
        when(automationDataManager.getActiveTriggerEntries(Trigger.CUSTOM_EVENT_COUNT)).thenReturn(Collections.singletonList(triggerEntry));
        when(automationDataManager.getScheduleEntries(Collections.singleton("schedule id"))).thenReturn(Collections.singletonList(scheduleEntry));

        new CustomEvent.Builder("name")
                .create()
                .track();

        runLooperTasks();

        verify(automationDataManager).getActiveTriggerEntries(Trigger.CUSTOM_EVENT_COUNT);
        assertEquals(scheduleEntry.getCount(), 1);
        verify(automationDataManager).saveSchedules(Collections.singleton(scheduleEntry));
        verify(automationDataManager).saveTriggers(Collections.singletonList(triggerEntry));
    }

    @Test
    public void testCustomEventScheduleLimitReached() throws Exception {
        automation.schedule(customEventActionSchedule);
        verify(automationDataManager).saveSchedules(ArgumentMatchers.<ScheduleEntry>anyCollection());

        Trigger customEventTrigger = Triggers.newCustomEventTriggerBuilder()
                                             .setCountGoal(1)
                                             .setEventName("name")
                                             .build();


        TriggerEntry triggerEntry = new TriggerEntry(customEventTrigger, "schedule id", false);
        ScheduleEntry scheduleEntry = new ScheduleEntry(new ActionSchedule("schedule id", customEventActionSchedule));
        scheduleEntry.setCount(3);
        when(automationDataManager.getActiveTriggerEntries(Trigger.CUSTOM_EVENT_COUNT)).thenReturn(Collections.singletonList(triggerEntry));
        when(automationDataManager.getScheduleEntries(Collections.singleton("schedule id"))).thenReturn(Collections.singletonList(scheduleEntry));

        new CustomEvent.Builder("name")
                .create()
                .track();

        runLooperTasks();

        verify(automationDataManager).getActiveTriggerEntries(Trigger.CUSTOM_EVENT_COUNT);
        verify(automationDataManager).deleteSchedules(Collections.singleton("schedule id"));
    }

    @Test
    public void testCustomEventNoTriggers() throws Exception {
        automation.schedule(customEventActionSchedule);
        verify(automationDataManager).saveSchedules(ArgumentMatchers.<ScheduleEntry>anyCollection());

        when(automationDataManager.getActiveTriggerEntries(Trigger.CUSTOM_EVENT_COUNT)).thenReturn(Collections.EMPTY_LIST);

        new CustomEvent.Builder("name")
                .create()
                .track();

        runLooperTasks();

        verify(automationDataManager).getActiveTriggerEntries(Trigger.CUSTOM_EVENT_COUNT);
        verify(automationDataManager, never()).saveTriggers(ArgumentMatchers.<TriggerEntry>anyCollection());
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

        automation.schedule(schedule);
        verify(automationDataManager).saveSchedules(ArgumentMatchers.<ScheduleEntry>anyCollection());

        TriggerEntry enterEntry = new TriggerEntry(enter, "schedule id", false);
        when(automationDataManager.getActiveTriggerEntries(Trigger.REGION_ENTER)).thenReturn(Collections.singletonList(enterEntry));

        RegionEvent event = new RegionEvent("region_id", "source", RegionEvent.BOUNDARY_EVENT_ENTER);
        UAirship.shared().getAnalytics().addEvent(event);

        runLooperTasks();

        verify(automationDataManager).getActiveTriggerEntries(Trigger.REGION_ENTER);
        assertEquals(1.0, enterEntry.getProgress());
        verify(automationDataManager).saveTriggers(Collections.singletonList(enterEntry));
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

        automation.schedule(schedule);
        verify(automationDataManager).saveSchedules(ArgumentMatchers.<ScheduleEntry>anyCollection());

        TriggerEntry triggerEntry = new TriggerEntry(trigger, "schedule id", false);
        when(automationDataManager.getActiveTriggerEntries(Trigger.REGION_EXIT)).thenReturn(Collections.singletonList(triggerEntry));

        RegionEvent event = new RegionEvent("region_id", "source", RegionEvent.BOUNDARY_EVENT_EXIT);
        ProximityRegion proximityRegion = new ProximityRegion("id", 2, 3);
        CircularRegion circularRegion = new CircularRegion(1.0, 2.0, 3.0);

        event.setProximityRegion(proximityRegion);
        event.setCircularRegion(circularRegion);
        UAirship.shared().getAnalytics().addEvent(event);

        runLooperTasks();

        verify(automationDataManager).getActiveTriggerEntries(Trigger.REGION_EXIT);
        assertEquals(1.0, triggerEntry.getProgress());
        verify(automationDataManager).saveTriggers(Collections.singletonList(triggerEntry));
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

        automation.schedule(schedule);
        verify(automationDataManager).saveSchedules(ArgumentMatchers.<ScheduleEntry>anyCollection());

        TriggerEntry triggerEntry = new TriggerEntry(trigger, "schedule id", false);
        when(automationDataManager.getActiveTriggerEntries(Trigger.LIFE_CYCLE_FOREGROUND)).thenReturn(Collections.singletonList(triggerEntry));

        activityMonitor.startActivity();
        runLooperTasks();

        verify(automationDataManager).getActiveTriggerEntries(Trigger.LIFE_CYCLE_FOREGROUND);
        assertEquals(1.0, triggerEntry.getProgress());
        verify(automationDataManager).saveTriggers(Collections.singletonList(triggerEntry));
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


        automation.schedule(schedule);
        verify(automationDataManager).saveSchedules(ArgumentMatchers.<ScheduleEntry>anyCollection());

        TriggerEntry triggerEntry = new TriggerEntry(trigger, "schedule id", false);
        when(automationDataManager.getActiveTriggerEntries(Trigger.LIFE_CYCLE_BACKGROUND)).thenReturn(Collections.singletonList(triggerEntry));

        activityMonitor.startActivity();
        activityMonitor.stopActivity();

        runLooperTasks();

        verify(automationDataManager).getActiveTriggerEntries(Trigger.LIFE_CYCLE_BACKGROUND);
        assertEquals(1.0, triggerEntry.getProgress());
        verify(automationDataManager).saveTriggers(Collections.singletonList(triggerEntry));
    }

    @Test
    public void testAppInitEvent() throws Exception {
        Trigger trigger = Triggers.newAppInitTriggerBuilder()
                                  .setGoal(3)
                                  .build();


        ActionScheduleInfo schedule = ActionScheduleInfo.newBuilder()
                                                        .addAction("test_action", JsonValue.wrap("action_value"))
                                                        .addTrigger(trigger)
                                                        .setGroup("group")
                                                        .setLimit(5)
                                                        .build();


        automation.schedule(schedule);
        verify(automationDataManager).saveSchedules(ArgumentMatchers.<ScheduleEntry>anyCollection());

        TriggerEntry triggerEntry = new TriggerEntry(trigger, "schedule id", false);
        when(automationDataManager.getActiveTriggerEntries(Trigger.LIFE_CYCLE_APP_INIT)).thenReturn(Collections.singletonList(triggerEntry));

        automation.tearDown();
        automation = new Automation(UAirship.shared().getAnalytics(), automationDataManager, TestApplication.getApplication().preferenceDataStore, activityMonitor);
        automation.init();

        runLooperTasks();

        verify(automationDataManager).getActiveTriggerEntries(Trigger.LIFE_CYCLE_APP_INIT);
        assertEquals(1.0, triggerEntry.getProgress());
        verify(automationDataManager).saveTriggers(Collections.singletonList(triggerEntry));
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


        automation.schedule(schedule);
        verify(automationDataManager).saveSchedules(ArgumentMatchers.<ScheduleEntry>anyCollection());

        TriggerEntry triggerEntry = new TriggerEntry(trigger, "schedule id", false);
        when(automationDataManager.getActiveTriggerEntries(Trigger.SCREEN_VIEW)).thenReturn(Collections.singletonList(triggerEntry));

        UAirship.shared().getAnalytics().trackScreen("screen");

        runLooperTasks();

        verify(automationDataManager).getActiveTriggerEntries(Trigger.SCREEN_VIEW);
        assertEquals(1.0, triggerEntry.getProgress());
        verify(automationDataManager).saveTriggers(Collections.singletonList(triggerEntry));
    }

    @Test
    public void testExpired() throws Exception {
        Trigger trigger = Triggers.newScreenTriggerBuilder()
                                  .setGoal(1)
                                  .setScreenName("screen")
                                  .build();

        ActionScheduleInfo schedule = ActionScheduleInfo.newBuilder()
                                                        .addAction("test_action", JsonValue.wrap("action_value"))
                                                        .addTrigger(trigger)
                                                        .setGroup("group")
                                                        .setLimit(5)
                                                        .setEnd(System.currentTimeMillis() - 100)
                                                        .build();

        automation.schedule(schedule);
        verify(automationDataManager).saveSchedules(ArgumentMatchers.<ScheduleEntry>anyCollection());

        TriggerEntry triggerEntry = new TriggerEntry(trigger, "schedule id", false);
        ScheduleEntry scheduleEntry = new ScheduleEntry(new ActionSchedule("schedule id", schedule));
        when(automationDataManager.getActiveTriggerEntries(Trigger.SCREEN_VIEW)).thenReturn(Collections.singletonList(triggerEntry));
        when(automationDataManager.getScheduleEntries(Collections.singleton("schedule id"))).thenReturn(Collections.singletonList(scheduleEntry));

        UAirship.shared().getAnalytics().trackScreen("screen");

        runLooperTasks();

        verify(automationDataManager).getActiveTriggerEntries(Trigger.SCREEN_VIEW);
        verify(automationDataManager).deleteSchedules(Collections.singleton("schedule id"));
        verify(automationDataManager).saveSchedules(Collections.<ScheduleEntry>emptySet());
    }

    @Test
    public void testScheduleAsync() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        automation.scheduleAsync(customEventActionSchedule, new PendingResult.ResultCallback<ActionSchedule>() {
            @Override
            public void onResult(@Nullable ActionSchedule result) {
                latch.countDown();
            }
        });

        while (!latch.await(1, TimeUnit.MILLISECONDS)) {
            runLooperTasks();
        }

        ArgumentCaptor<List> scheduleEntryArgumentCaptor = ArgumentCaptor.forClass(List.class);

        verify(automationDataManager).saveSchedules(scheduleEntryArgumentCaptor.capture());
        ScheduleEntry scheduleEntry = (ScheduleEntry) scheduleEntryArgumentCaptor.getValue().get(0);
        assertEquals(scheduleEntry.group, customEventActionSchedule.getGroup());
        assertEquals(scheduleEntry.start, customEventActionSchedule.getStart());
    }

    @Test
    public void testInactivityWithoutSchedules() throws Exception {
        new CustomEvent.Builder("name")
                .create()
                .track();

        runLooperTasks();

        verify(automationDataManager, never()).getActiveTriggerEntries(anyInt());
        automation.schedule(customEventActionSchedule);

        new CustomEvent.Builder("name")
                .create()
                .track();

        runLooperTasks();

        verify(automationDataManager).getActiveTriggerEntries(anyInt());
    }

    @Test
    public void testSecondsDelay() throws Exception {
        Trigger trigger = Triggers.newForegroundTriggerBuilder()
                                  .setGoal(1)
                                  .build();

        Map<String, Object> toastMap = new HashMap<>();
        toastMap.put("length", Toast.LENGTH_LONG);
        toastMap.put("text", "toast");

        ScheduleDelay delay = ScheduleDelay.newBuilder()
                .setSeconds(1)
                .build();

        ActionScheduleInfo schedule = ActionScheduleInfo.newBuilder()
                                                        .addAction(ToastAction.DEFAULT_REGISTRY_NAME, JsonValue.wrap(toastMap))
                                                        .addTrigger(trigger)
                                                        .setDelay(delay)
                                                        .setGroup("group")
                                                        .setLimit(5)
                                                        .build();


        automation.schedule(schedule);
        verify(automationDataManager).saveSchedules(ArgumentMatchers.<ScheduleEntry>anyCollection());

        TriggerEntry triggerEntry = new TriggerEntry(trigger, "schedule id", false);
        ScheduleEntry scheduleEntry = new ScheduleEntry(new ActionSchedule("schedule id", schedule));
        when(automationDataManager.getActiveTriggerEntries(Trigger.LIFE_CYCLE_FOREGROUND)).thenReturn(Collections.singletonList(triggerEntry));
        when(automationDataManager.getScheduleEntries(Collections.singleton("schedule id")))
                .thenReturn(Collections.singletonList(scheduleEntry));

        activityMonitor.startActivity();
        runLooperTasks();

        verify(automationDataManager).getActiveTriggerEntries(Trigger.LIFE_CYCLE_FOREGROUND);
        verify(automationDataManager).saveTriggers(Collections.singletonList(triggerEntry));
        verify(automationDataManager).saveSchedules(Collections.singleton(scheduleEntry));

        // Verify that the toast doesn't happen until after schedule
        assertEquals(null, ShadowToast.getTextOfLatestToast());
        scheduleEntry.setPendingExecutionDate(scheduleEntry.getPendingExecutionDate() - 1000);
        advanceAutomationLooperScheduler(1000);
        assertEquals("toast", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void testSecondsDelayWithAppState() throws Exception {
        Trigger trigger = Triggers.newForegroundTriggerBuilder()
                                  .setGoal(1)
                                  .build();

        Map<String, Object> toastMap = new HashMap<>();
        toastMap.put("length", Toast.LENGTH_LONG);
        toastMap.put("text", "toast");

        ScheduleDelay delay = ScheduleDelay.newBuilder()
                                           .setSeconds(1)
                                           .setAppState(ScheduleDelay.APP_STATE_FOREGROUND)
                                           .build();

        ActionScheduleInfo schedule = ActionScheduleInfo.newBuilder()
                                                        .addAction(ToastAction.DEFAULT_REGISTRY_NAME, JsonValue.wrap(toastMap))
                                                        .addTrigger(trigger)
                                                        .setDelay(delay)
                                                        .setGroup("group")
                                                        .setLimit(5)
                                                        .build();


        automation.schedule(schedule);
        verify(automationDataManager).saveSchedules(ArgumentMatchers.<ScheduleEntry>anyCollection());

        TriggerEntry triggerEntry = new TriggerEntry(trigger, "schedule id", false);
        ScheduleEntry scheduleEntry = new ScheduleEntry(new ActionSchedule("schedule id", schedule));
        when(automationDataManager.getActiveTriggerEntries(Trigger.LIFE_CYCLE_FOREGROUND)).thenReturn(Collections.singletonList(triggerEntry));
        when(automationDataManager.getScheduleEntries(Collections.singleton("schedule id")))
                .thenReturn(Collections.singletonList(scheduleEntry));

        activityMonitor.startActivity();
        runLooperTasks();

        verify(automationDataManager).getActiveTriggerEntries(Trigger.LIFE_CYCLE_FOREGROUND);
        verify(automationDataManager).saveTriggers(Collections.singletonList(triggerEntry));
        verify(automationDataManager).saveSchedules(Collections.singleton(scheduleEntry));

        // Verify that the toast doesn't happen until the time passes + activity resumes.
        assertEquals(null, ShadowToast.getTextOfLatestToast());
        activityMonitor.stopActivity();
        scheduleEntry.setPendingExecutionDate(scheduleEntry.getPendingExecutionDate() - 1000);
        advanceAutomationLooperScheduler(1000);
        assertEquals(null, ShadowToast.getTextOfLatestToast());
        activityMonitor.startActivity();
        runLooperTasks();
        assertEquals("toast", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void testSecondsDelayWithScreen() throws Exception {
        Trigger trigger = Triggers.newForegroundTriggerBuilder()
                                  .setGoal(1)
                                  .build();

        Map<String, Object> toastMap = new HashMap<>();
        toastMap.put("length", Toast.LENGTH_LONG);
        toastMap.put("text", "toast");

        ScheduleDelay delay = ScheduleDelay.newBuilder()
                                           .setSeconds(1)
                                           .setScreen("the-screen")
                                           .build();

        ActionScheduleInfo schedule = ActionScheduleInfo.newBuilder()
                                                        .addAction(ToastAction.DEFAULT_REGISTRY_NAME, JsonValue.wrap(toastMap))
                                                        .addTrigger(trigger)
                                                        .setDelay(delay)
                                                        .setGroup("group")
                                                        .setLimit(5)
                                                        .build();


        automation.schedule(schedule);
        verify(automationDataManager).saveSchedules(ArgumentMatchers.<ScheduleEntry>anyCollection());

        TriggerEntry triggerEntry = new TriggerEntry(trigger, "schedule id", false);
        ScheduleEntry scheduleEntry = new ScheduleEntry(new ActionSchedule("schedule id", schedule));
        when(automationDataManager.getActiveTriggerEntries(Trigger.LIFE_CYCLE_FOREGROUND)).thenReturn(Collections.singletonList(triggerEntry));
        when(automationDataManager.getScheduleEntries(Collections.singleton("schedule id")))
                .thenReturn(Collections.singletonList(scheduleEntry));
        when(automationDataManager.getPendingExecutionSchedules()).thenReturn(Collections.singletonList(scheduleEntry));

        activityMonitor.startActivity();
        runLooperTasks();

        verify(automationDataManager).getActiveTriggerEntries(Trigger.LIFE_CYCLE_FOREGROUND);
        verify(automationDataManager).saveTriggers(Collections.singletonList(triggerEntry));
        verify(automationDataManager).saveSchedules(Collections.singleton(scheduleEntry));

        // Verify that the toast doesn't happen until the time passes + activity resumes.
        assertEquals(null, ShadowToast.getTextOfLatestToast());
        scheduleEntry.setPendingExecutionDate(scheduleEntry.getPendingExecutionDate() - 1000);
        advanceAutomationLooperScheduler(1000);
        assertEquals(null, ShadowToast.getTextOfLatestToast());
        UAirship.shared().getAnalytics().trackScreen("the-screen");
        runLooperTasks();
        assertEquals("toast", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void testSecondsDelayWithRegion() throws Exception {
        Trigger trigger = Triggers.newForegroundTriggerBuilder()
                                  .setGoal(1)
                                  .build();

        Map<String, Object> toastMap = new HashMap<>();
        toastMap.put("length", Toast.LENGTH_LONG);
        toastMap.put("text", "toast");

        ScheduleDelay delay = ScheduleDelay.newBuilder()
                                           .setSeconds(1)
                                           .setRegionId("enter_region")
                                           .build();

        ActionScheduleInfo schedule = ActionScheduleInfo.newBuilder()
                                                        .addAction(ToastAction.DEFAULT_REGISTRY_NAME, JsonValue.wrap(toastMap))
                                                        .addTrigger(trigger)
                                                        .setDelay(delay)
                                                        .setGroup("group")
                                                        .setLimit(5)
                                                        .build();

        automation.schedule(schedule);
        verify(automationDataManager).saveSchedules(ArgumentMatchers.<ScheduleEntry>anyCollection());

        TriggerEntry triggerEntry = new TriggerEntry(trigger, "schedule id", false);
        ScheduleEntry scheduleEntry = new ScheduleEntry(new ActionSchedule("schedule id", schedule));
        when(automationDataManager.getActiveTriggerEntries(Trigger.LIFE_CYCLE_FOREGROUND)).thenReturn(Collections.singletonList(triggerEntry));
        when(automationDataManager.getScheduleEntries(Collections.singleton("schedule id")))
                .thenReturn(Collections.singletonList(scheduleEntry));
        when(automationDataManager.getPendingExecutionSchedules()).thenReturn(Collections.singletonList(scheduleEntry));

        activityMonitor.startActivity();
        runLooperTasks();

        verify(automationDataManager).getActiveTriggerEntries(Trigger.LIFE_CYCLE_FOREGROUND);
        verify(automationDataManager).saveTriggers(Collections.singletonList(triggerEntry));
        verify(automationDataManager).saveSchedules(Collections.singleton(scheduleEntry));

        // Verify that the toast doesn't happen until the time passes + region is entered.
        assertEquals(null, ShadowToast.getTextOfLatestToast());
        scheduleEntry.setPendingExecutionDate(scheduleEntry.getPendingExecutionDate() - 1000);
        advanceAutomationLooperScheduler(1000);
        assertEquals(null, ShadowToast.getTextOfLatestToast());

        RegionEvent event = new RegionEvent("enter_region", "test_source", RegionEvent.BOUNDARY_EVENT_ENTER);
        UAirship.shared().getAnalytics().addEvent(event);
        runLooperTasks();
        assertEquals("toast", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void testRescheduleDelayRemainingExecution() throws Exception {
        Trigger trigger = Triggers.newForegroundTriggerBuilder()
                                  .setGoal(1)
                                  .build();

        Map<String, Object> toastMap = new HashMap<>();
        toastMap.put("length", Toast.LENGTH_LONG);
        toastMap.put("text", "toast");

        ScheduleDelay delay = ScheduleDelay.newBuilder()
                                           .setSeconds(1)
                                           .setAppState(ScheduleDelay.APP_STATE_FOREGROUND)
                                           .build();

        ActionScheduleInfo schedule = ActionScheduleInfo.newBuilder()
                                                        .addAction(ToastAction.DEFAULT_REGISTRY_NAME, JsonValue.wrap(toastMap))
                                                        .addTrigger(trigger)
                                                        .setDelay(delay)
                                                        .setGroup("group")
                                                        .setLimit(5)
                                                        .build();


        automation.schedule(schedule);
        verify(automationDataManager).saveSchedules(ArgumentMatchers.<ScheduleEntry>anyCollection());

        TriggerEntry triggerEntry = new TriggerEntry(trigger, "schedule id", false);
        ScheduleEntry scheduleEntry = new ScheduleEntry(new ActionSchedule("schedule id", schedule));
        when(automationDataManager.getActiveTriggerEntries(Trigger.LIFE_CYCLE_FOREGROUND)).thenReturn(Collections.singletonList(triggerEntry));
        when(automationDataManager.getScheduleEntries(Collections.singleton("schedule id")))
                .thenReturn(Collections.singletonList(scheduleEntry));
        when(automationDataManager.getPendingExecutionSchedules()).thenReturn(Collections.singletonList(scheduleEntry));

        activityMonitor.startActivity();
        runLooperTasks();

        verify(automationDataManager).getActiveTriggerEntries(Trigger.LIFE_CYCLE_FOREGROUND);
        verify(automationDataManager).saveTriggers(Collections.singletonList(triggerEntry));
        verify(automationDataManager).saveSchedules(Collections.singleton(scheduleEntry));

        // Verify that the toast doesn't happen until after schedule
        assertEquals(null, ShadowToast.getTextOfLatestToast());
        scheduleEntry.setPendingExecutionDate(scheduleEntry.getPendingExecutionDate() - 700);
        advanceAutomationLooperScheduler(700);
        assertEquals(null, ShadowToast.getTextOfLatestToast());
        activityMonitor.stopActivity();
        activityMonitor.startActivity();
        assertEquals(null, ShadowToast.getTextOfLatestToast());
        scheduleEntry.setPendingExecutionDate(scheduleEntry.getPendingExecutionDate() - 300);
        advanceAutomationLooperScheduler(300);
        assertEquals("toast", ShadowToast.getTextOfLatestToast());
    }

    /**
     * Helper method to run all the looper tasks.
     */
    private void runLooperTasks() {
        ShadowLooper mainLooper = Shadows.shadowOf(Looper.getMainLooper());
        ShadowLooper automationLooper = Shadows.shadowOf(automation.backgroundThread.getLooper());

        while (mainLooper.getScheduler().areAnyRunnable() || automationLooper.getScheduler().areAnyRunnable()) {
            mainLooper.runToEndOfTasks();
            automationLooper.runToEndOfTasks();
        }
    }

    private void advanceAutomationLooperScheduler(long millis) {
        ShadowLooper automationLooper = Shadows.shadowOf(automation.backgroundThread.getLooper());
        automationLooper.getScheduler().advanceBy(millis, TimeUnit.MILLISECONDS);
    }
}
