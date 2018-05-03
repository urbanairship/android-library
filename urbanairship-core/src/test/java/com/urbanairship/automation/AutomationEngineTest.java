/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.os.Looper;

import com.urbanairship.ApplicationMetrics;
import com.urbanairship.BaseTestCase;
import com.urbanairship.CancelableOperation;
import com.urbanairship.OperationScheduler;
import com.urbanairship.PendingResult;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.json.ValueMatcher;
import com.urbanairship.location.RegionEvent;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AutomationEngineTest extends BaseTestCase {

    private TestActionScheduleDriver driver;
    private AutomationDataManager automationDataManager;
    private AutomationEngine<ActionSchedule> automationEngine;
    private TestActivityMonitor activityMonitor;
    private ApplicationMetrics mockMetrics;

    @Before
    public void setUp() {
        activityMonitor = new TestActivityMonitor();
        activityMonitor.register();

        mockMetrics = mock(ApplicationMetrics.class);
        TestApplication.getApplication().setApplicationMetrics(mockMetrics);

        OperationScheduler scheduler = new OperationScheduler() {
            @Override
            public void schedule(long delay, CancelableOperation operation) {
                operation.getHandler().postDelayed(operation, delay);
            }
        };

        driver = new TestActionScheduleDriver();
        automationDataManager = new AutomationDataManager(TestApplication.getApplication(), "appKey", "AutomationEngineTest");
        automationEngine = new AutomationEngine.Builder<ActionSchedule>()
                .setAnalytics(UAirship.shared().getAnalytics())
                .setDataManager(automationDataManager)
                .setActivityMonitor(activityMonitor)
                .setDriver(driver)
                .setOperationScheduler(scheduler)
                .setScheduleLimit(100)
                .build();

        automationEngine.start();
        runLooperTasks();
    }

    @After
    public void teardown() {
        automationEngine.stop();
        activityMonitor.unregister();
    }

    @Test
    public void testSchedule() throws Exception {
        ActionScheduleInfo scheduleInfo = ActionScheduleInfo.newBuilder()
                                                            .addTrigger(Triggers.newCustomEventTriggerBuilder()
                                                                                .setCountGoal(1)
                                                                                .setEventName("event")
                                                                                .build())
                                                            .addAction("test_action", JsonValue.wrap("action_value"))
                                                            .build();


        Future<ActionSchedule> pendingResult = automationEngine.schedule(scheduleInfo);
        runLooperTasks();

        // Verify the pendingResult
        assertTrue(pendingResult.isDone());
        assertFalse(pendingResult.isCancelled());
        assertNotNull(pendingResult.get());
        assertNotNull(automationDataManager.getScheduleEntry(pendingResult.get().getId()));
    }

    @Test
    public void testCustomEvent() throws Exception {
        Trigger trigger = Triggers.newCustomEventTriggerBuilder()
                                  .setCountGoal(1)
                                  .setEventName("name")
                                  .build();

        verifyTrigger(trigger, new Runnable() {
            @Override
            public void run() {
                CustomEvent.newBuilder("name")
                           .build()
                           .track();
            }
        });
    }


    @Test
    public void testCustomEventValue() throws Exception {
        Trigger trigger = Triggers.newCustomEventTriggerBuilder()
                                  .setValueGoal(2.0)
                                  .setEventName("some name")
                                  .build();

        verifyTrigger(trigger, new Runnable() {
            @Override
            public void run() {
                CustomEvent.newBuilder("some name")
                           .setEventValue(2.0)
                           .build()
                           .track();
            }
        });
    }

    @Test
    public void testEnterRegionEvent() throws Exception {
        Trigger trigger = Triggers.newEnterRegionTriggerBuilder()
                                  .setRegionId("region_id")
                                  .setGoal(1.0)
                                  .build();

        verifyTrigger(trigger, new Runnable() {
            @Override
            public void run() {
                RegionEvent event = new RegionEvent("region_id", "test_source", RegionEvent.BOUNDARY_EVENT_ENTER);
                UAirship.shared().getAnalytics().addEvent(event);
            }
        });
    }

    @Test
    public void testExitRegionEvent() throws Exception {
        Trigger trigger = Triggers.newExitRegionTriggerBuilder()
                                  .setRegionId("region_id")
                                  .setGoal(1.0)
                                  .build();

        verifyTrigger(trigger, new Runnable() {
            @Override
            public void run() {
                RegionEvent event = new RegionEvent("region_id", "test_source", RegionEvent.BOUNDARY_EVENT_EXIT);
                UAirship.shared().getAnalytics().addEvent(event);
            }
        });
    }

    @Test
    public void testAppForegroundEvent() throws Exception {
        Trigger trigger = Triggers.newForegroundTriggerBuilder()
                                  .setGoal(1)
                                  .build();

        verifyTrigger(trigger, new Runnable() {
            @Override
            public void run() {
                activityMonitor.startActivity();
            }
        });
    }

    @Test
    public void testAppBackgroundEvent() throws Exception {
        Trigger trigger = Triggers.newBackgroundTriggerBuilder()
                                  .setGoal(1)
                                  .build();

        verifyTrigger(trigger, new Runnable() {
            @Override
            public void run() {
                activityMonitor.startActivity();
                activityMonitor.stopActivity();
            }
        });
    }

    @Test
    public void testActiveSession() throws Exception {
        Trigger trigger = Triggers.newActiveSessionTriggerBuilder()
                                  .setGoal(1)
                                  .build();

        verifyTrigger(trigger, new Runnable() {
            @Override
            public void run() {
                activityMonitor.startActivity();
            }
        });
    }

    @Test
    public void testActiveSessionLateSubscription() throws Exception {
        Trigger trigger = Triggers.newActiveSessionTriggerBuilder()
                                  .setGoal(1)
                                  .build();

        activityMonitor.startActivity();

        verifyTrigger(trigger, null);
    }

    @Test
    public void testVersion() throws Exception {
        when(mockMetrics.getAppVersionUpdated()).thenReturn(true);
        when(mockMetrics.getCurrentAppVersion()).thenReturn(2);

        Trigger trigger = Triggers.newVersionTriggerBuilder(ValueMatcher.newNumberRangeMatcher(2.0, 4.0))
                                  .setGoal(1)
                                  .build();

        verifyTrigger(trigger, null);
    }

    @Test
    public void testScreenEvent() throws Exception {
        Trigger trigger = Triggers.newScreenTriggerBuilder()
                                  .setGoal(1)
                                  .setScreenName("screen")
                                  .build();

        verifyTrigger(trigger, new Runnable() {
            @Override
            public void run() {
                UAirship.shared().getAnalytics().trackScreen("screen");
            }
        });
    }

    @Test
    public void testSecondsDelay() throws Exception {
        ScheduleDelay delay = ScheduleDelay.newBuilder()
                                           .setSeconds(1)
                                           .build();

        verifyDelay(delay, new Runnable() {
            @Override
            public void run() {
                advanceAutomationLooperScheduler(1000);
            }
        });
    }

    @Test
    public void testAppStateDelay() throws Exception {
        ScheduleDelay delay = ScheduleDelay.newBuilder()
                                           .setAppState(ScheduleDelay.APP_STATE_FOREGROUND)
                                           .build();

        verifyDelay(delay, new Runnable() {
            @Override
            public void run() {
                activityMonitor.startActivity();
            }
        });
    }

    @Test
    public void testScreenDelay() throws Exception {
        ScheduleDelay delay = ScheduleDelay.newBuilder()
                                           .setScreen("some screen")
                                           .build();

        verifyDelay(delay, new Runnable() {
            @Override
            public void run() {
                UAirship.shared().getAnalytics().trackScreen("some screen");
            }
        });
    }

    @Test
    public void testMultipleScreenDelaySome() throws Exception {
        ScheduleDelay delay = ScheduleDelay.newBuilder()
                                           .setScreens(Arrays.asList("some screen", "some other screen"))
                                           .build();

        verifyDelay(delay, new Runnable() {
            @Override
            public void run() {
                UAirship.shared().getAnalytics().trackScreen("some screen");
            }
        });
    }

    @Test
    public void testMultipleScreenDelayOther() throws Exception {
        ScheduleDelay delay = ScheduleDelay.newBuilder()
                                           .setScreens(Arrays.asList("some screen", "some other screen"))
                                           .build();
        verifyDelay(delay, new Runnable() {
            @Override
            public void run() {
                UAirship.shared().getAnalytics().trackScreen("some other screen");
            }
        });
    }

    @Test
    public void testRegionDelay() throws Exception {
        ScheduleDelay delay = ScheduleDelay.newBuilder()
                                           .setRegionId("region_id")
                                           .build();

        verifyDelay(delay, new Runnable() {
            @Override
            public void run() {
                RegionEvent event = new RegionEvent("region_id", "test_source", RegionEvent.BOUNDARY_EVENT_EXIT);
                UAirship.shared().getAnalytics().addEvent(event);
            }
        });
    }

    @Test
    public void testPriority() throws Exception {
        ArrayList<ActionSchedule> schedules = new ArrayList<>();

        Integer[] addedPriorityLevels = new Integer[] { 5, 2, 1, 0, 0, 4, 3, 3, 2 };
        ArrayList<Integer> expectedExecutionOrder = new ArrayList<>(Arrays.asList(addedPriorityLevels));
        Collections.sort(expectedExecutionOrder);

        // Add schedules out of order
        for (int priority : addedPriorityLevels) {
            final Trigger trigger = Triggers.newCustomEventTriggerBuilder()
                                            .setCountGoal(1)
                                            .setEventName("name")
                                            .build();

            final ActionScheduleInfo scheduleInfo = ActionScheduleInfo.newBuilder()
                                                                      .addTrigger(trigger)
                                                                      .addAction("test_action", JsonValue.wrap("action_value"))
                                                                      .setPriority(priority)
                                                                      .build();

            Assert.assertEquals(scheduleInfo.getPriority(), priority);

            PendingResult<ActionSchedule> future = automationEngine.schedule(scheduleInfo);
            runLooperTasks();
            schedules.add(future.get());
        }

        // Trigger the schedules
        CustomEvent.newBuilder("name")
                   .build()
                   .track();

        runLooperTasks();

        // Verify the schedules were executed in ascending priority order
        assertEquals(driver.priorityList, expectedExecutionOrder);
    }

    @Test
    public void testExpiryListener() throws Exception {
        final Trigger trigger = Triggers.newCustomEventTriggerBuilder()
                                        .setCountGoal(1)
                                        .setEventName("name")
                                        .build();

        final ActionScheduleInfo expiredScheduleInfo = ActionScheduleInfo.newBuilder()
                                                                         .addTrigger(trigger)
                                                                         .addAction("test_action", JsonValue.wrap("action_value"))
                                                                         .setEnd(System.currentTimeMillis() - 1)
                                                                         .build();

        AutomationEngine.ScheduleExpiryListener<ActionSchedule> expiryListener = mock(AutomationEngine.ScheduleExpiryListener.class);
        automationEngine.setScheduleExpiryListener(expiryListener);

        // Schedule it
        automationEngine.schedule(expiredScheduleInfo);
        runLooperTasks();

        // Trigger the schedules
        CustomEvent.newBuilder("name")
                   .build()
                   .track();

        runLooperTasks();

        // Verify the listener was called
        verify(expiryListener).onScheduleExpired(any(ActionSchedule.class));
    }


    @Test
    public void testPause() throws Exception {
        // Pause
        automationEngine.setPaused(true);

        final ActionScheduleInfo scheduleInfo = ActionScheduleInfo.newBuilder()
                                                                  .addTrigger(Triggers.newCustomEventTriggerBuilder()
                                                                                      .setCountGoal(1)
                                                                                      .setEventName("event")
                                                                                      .build())
                                                                  .addAction("test_action", JsonValue.wrap("action_value"))
                                                                  .build();

        PendingResult<ActionSchedule> future = automationEngine.schedule(scheduleInfo);
        runLooperTasks();

        ActionSchedule schedule = future.get();

        // Verify it was saved
        ScheduleEntry entry = automationDataManager.getScheduleEntry(schedule.getId());
        assertEquals(entry.scheduleId, schedule.getId());

        // Try to trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        // Verify it's still idle
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_IDLE);
        assertFalse(driver.callbackMap.containsKey(schedule.getId()));

        // Resume
        automationEngine.setPaused(false);
        runLooperTasks();

        // Verify it's still idle
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_IDLE);
        assertFalse(driver.callbackMap.containsKey(schedule.getId()));

        // Actually trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();
        runLooperTasks();

        // Verify it started executing the schedule
        assertTrue(driver.callbackMap.containsKey(schedule.getId()));
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_EXECUTING);

        // Finish executing the schedule
        driver.callbackMap.get(schedule.getId()).onFinish();
        runLooperTasks();

        // Schedule should be deleted
        assertNull(automationDataManager.getScheduleEntry(schedule.getId()));
    }

    @Test
    public void testEditSchedule() throws Exception {
        final ActionScheduleInfo scheduleInfo = ActionScheduleInfo.newBuilder()
                                                                  .addTrigger(Triggers.newCustomEventTriggerBuilder()
                                                                                      .setCountGoal(1)
                                                                                      .setEventName("event")
                                                                                      .build())
                                                                  .addAction("test_action", JsonValue.wrap("action_value"))
                                                                  .setEditGracePeriod(100, TimeUnit.SECONDS)
                                                                  .build();

        PendingResult<ActionSchedule> future = automationEngine.schedule(scheduleInfo);
        runLooperTasks();
        ActionSchedule schedule = future.get();

        // Trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        // Finish executing the schedule
        driver.callbackMap.get(schedule.getId()).onFinish();
        runLooperTasks();

        // Verify it's finished
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_FINISHED);

        // Update the schedule with a end time set to the next day
        long end = System.currentTimeMillis() + 1000 * 60 * 60 * 24;
        final ActionScheduleEdits edits = ActionScheduleEdits.newBuilder()
                                                             .setLimit(2)
                                                             .setStart(10)
                                                             .setEnd(end)
                                                             .setActions(JsonMap.newBuilder()
                                                                                .put("another_action", JsonValue.wrapOpt("COOL")).build().getMap())
                                                             .setPriority(300)
                                                             .build();

        future = automationEngine.editSchedule(schedule.getId(), edits);
        runLooperTasks();
        ActionSchedule updated = future.get();


        // Verify it's now idle
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_IDLE);

        // Verify it was updated
        assertEquals(edits.getLimit().intValue(), updated.getInfo().getLimit());
        assertEquals(edits.getStart().longValue(), updated.getInfo().getStart());
        assertEquals(edits.getEnd().longValue(), updated.getInfo().getEnd());
        assertEquals(edits.getPriority().intValue(), updated.getInfo().getPriority());
        assertEquals("COOL", updated.getInfo().getActions().get("another_action").getString());
    }

    @Test
    public void testEditScheduleEndZero() throws Exception {
        final ActionScheduleInfo scheduleInfo = ActionScheduleInfo.newBuilder()
                                                                  .addTrigger(Triggers.newCustomEventTriggerBuilder()
                                                                                      .setCountGoal(1)
                                                                                      .setEventName("event")
                                                                                      .build())
                                                                  .addAction("test_action", JsonValue.wrap("action_value"))
                                                                  .setEditGracePeriod(100, TimeUnit.SECONDS)
                                                                  .build();

        PendingResult<ActionSchedule> future = automationEngine.schedule(scheduleInfo);
        runLooperTasks();
        ActionSchedule schedule = future.get();

        // Verify its idle
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_IDLE);


        // Update the schedule
        final ActionScheduleEdits edits = ActionScheduleEdits.newBuilder()
                                                             .setEnd(0)
                                                             .build();

        future = automationEngine.editSchedule(schedule.getId(), edits);
        runLooperTasks();
        ActionSchedule updated = future.get();


        // Verify it's finished
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_FINISHED);

        // Verify it was updated
        assertEquals(edits.getEnd().longValue(), updated.getInfo().getEnd());
    }

    @Test
    public void testInterval() throws Exception {
        final ActionScheduleInfo scheduleInfo = ActionScheduleInfo.newBuilder()
                                                                  .addTrigger(Triggers.newCustomEventTriggerBuilder()
                                                                                      .setCountGoal(1)
                                                                                      .setEventName("event")
                                                                                      .build())
                                                                  .addAction("test_action", JsonValue.wrap("action_value"))
                                                                  .setInterval(10, TimeUnit.SECONDS)
                                                                  .setLimit(2)
                                                                  .build();

        PendingResult<ActionSchedule> future = automationEngine.schedule(scheduleInfo);
        runLooperTasks();
        ActionSchedule schedule = future.get();

        // Trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        // Finish executing the schedule
        driver.callbackMap.get(schedule.getId()).onFinish();
        runLooperTasks();

        // Verify it's paused
        assertEquals(ScheduleEntry.STATE_PAUSED, automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState());

        // Advance the scheduler
        advanceAutomationLooperScheduler(TimeUnit.SECONDS.toMillis(10));

        // Verify its now idle
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_IDLE);
    }

    @Test
    public void testOnScheduleChangeBeforeEngineStarts() {
        OperationScheduler scheduler = new OperationScheduler() {
            @Override
            public void schedule(long delay, CancelableOperation operation) {
                operation.getHandler().postDelayed(operation, delay);
            }
        };

        driver = new TestActionScheduleDriver();
        automationDataManager = new AutomationDataManager(TestApplication.getApplication(), "appKey", "AutomationEngineTest");
        automationEngine = new AutomationEngine.Builder<ActionSchedule>()
                .setAnalytics(UAirship.shared().getAnalytics())
                .setDataManager(automationDataManager)
                .setActivityMonitor(activityMonitor)
                .setDriver(driver)
                .setOperationScheduler(scheduler)
                .setScheduleLimit(100)
                .build();

        // Should not crash
        automationEngine.checkPendingSchedules();
    }

    private void verifyDelay(ScheduleDelay delay, Runnable resolveDelay) throws Exception {
        final ActionScheduleInfo scheduleInfo = ActionScheduleInfo.newBuilder()
                                                                  .addTrigger(Triggers.newCustomEventTriggerBuilder()
                                                                                      .setCountGoal(1)
                                                                                      .setEventName("event")
                                                                                      .build())
                                                                  .addAction("test_action", JsonValue.wrap("action_value"))
                                                                  .setDelay(delay)
                                                                  .build();

        PendingResult<ActionSchedule> future = automationEngine.schedule(scheduleInfo);
        runLooperTasks();

        ActionSchedule schedule = future.get();

        // Verify it was saved
        ScheduleEntry entry = automationDataManager.getScheduleEntry(schedule.getId());
        assertEquals(entry.scheduleId, schedule.getId());

        // Trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        // Verify it's now pending execution
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_PENDING_EXECUTION);
        assertFalse(driver.callbackMap.containsKey(schedule.getId()));

        if (delay.getSeconds() > 0) {
            // Verify the pending date is set
            entry = automationDataManager.getScheduleEntry(schedule.getId());
            assertTrue(entry.getPendingExecutionDate() > System.currentTimeMillis());

            // Set the pending date to now
            entry.setPendingExecutionDate(System.currentTimeMillis());
            automationDataManager.saveSchedules(Collections.singletonList(entry));
        }

        // Resolve delay
        resolveDelay.run();
        runLooperTasks();

        // Verify it started executing the schedule
        assertTrue(driver.callbackMap.containsKey(schedule.getId()));
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_EXECUTING);

        // Finish executing the schedule
        driver.callbackMap.get(schedule.getId()).onFinish();
        runLooperTasks();

        // Schedule should be deleted
        assertNull(automationDataManager.getScheduleEntry(schedule.getId()));
    }

    private void verifyTrigger(Trigger trigger, Runnable generateEvents) throws Exception {
        generateEvents = generateEvents != null ? generateEvents : new Runnable() {
            @Override
            public void run() {}
        };

        final ActionScheduleInfo scheduleInfo = ActionScheduleInfo.newBuilder()
                                                                  .addTrigger(trigger)
                                                                  .addAction("test_action", JsonValue.wrap("action_value"))
                                                                  .setGroup("group")
                                                                  .setLimit(2)
                                                                  .build();

        PendingResult<ActionSchedule> future = automationEngine.schedule(scheduleInfo);
        runLooperTasks();

        ActionSchedule schedule = future.get();

        // Verify it was saved
        ScheduleEntry entry = automationDataManager.getScheduleEntry(schedule.getId());
        assertEquals(entry.scheduleId, schedule.getId());

        // Trigger the schedule
        if (generateEvents != null) {
            generateEvents.run();
        }

        runLooperTasks();

        // Verify it started executing the schedule
        assertTrue(driver.callbackMap.containsKey(schedule.getId()));
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_EXECUTING);

        // Finish executing the schedule
        driver.callbackMap.get(schedule.getId()).onFinish();
        runLooperTasks();

        // Verify it's back to idle and progress is set
        assertTrue(driver.callbackMap.containsKey(schedule.getId()));

        if (generateEvents != null) {
            assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_IDLE);
        } else {
            // ASAP triggers should automatically re-execute if the count has not been met
            assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_EXECUTING);
        }

        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getCount(), 1);

        // Trigger it again
        if (generateEvents != null) {
            generateEvents.run();
        }

        runLooperTasks();
        driver.callbackMap.get(schedule.getId()).onFinish();
        runLooperTasks();

        // Schedule should be deleted
        assertNull(automationDataManager.getScheduleEntry(schedule.getId()));
    }

    /**
     * Helper method to run all the looper tasks.
     */
    private void runLooperTasks() {
        ShadowLooper mainLooper = Shadows.shadowOf(Looper.getMainLooper());
        ShadowLooper automationLooper = Shadows.shadowOf(automationEngine.backgroundThread.getLooper());

        do {
            mainLooper.runToEndOfTasks();
            automationLooper.runToEndOfTasks();
        }
        while (mainLooper.getScheduler().areAnyRunnable() || automationLooper.getScheduler().areAnyRunnable());
    }

    private void advanceAutomationLooperScheduler(long millis) {
        ShadowLooper automationLooper = Shadows.shadowOf(automationEngine.backgroundThread.getLooper());
        automationLooper.getScheduler().advanceBy(millis, TimeUnit.MILLISECONDS);
    }

    private static class TestActionScheduleDriver extends ActionAutomationDriver {

        Map<String, Callback> callbackMap = new HashMap<>();
        ArrayList<Integer> priorityList = new ArrayList<>();

        @Override
        public void onExecuteTriggeredSchedule(ActionSchedule schedule, Callback finishCallback) {
            callbackMap.put(schedule.getId(), finishCallback);
            priorityList.add(schedule.getInfo().getPriority());
        }
    }
}
