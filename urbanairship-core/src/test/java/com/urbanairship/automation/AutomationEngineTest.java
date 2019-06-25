/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.os.Looper;
import androidx.annotation.NonNull;

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
import java.util.concurrent.ExecutionException;
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

    private ActionScheduleInfo scheduleInfo;

    @Before
    public void setUp() {
        scheduleInfo = ActionScheduleInfo.newBuilder()
                                         .addTrigger(Triggers.newCustomEventTriggerBuilder()
                                                             .setCountGoal(1)
                                                             .setEventName("event")
                                                             .build())
                                         .addAction("test_action", JsonValue.wrap("action_value"))
                                         .setGroup("group")
                                         .build();
        activityMonitor = new TestActivityMonitor();

        mockMetrics = mock(ApplicationMetrics.class);
        TestApplication.getApplication().setApplicationMetrics(mockMetrics);

        OperationScheduler scheduler = new OperationScheduler() {
            @Override
            public void schedule(long delay, @NonNull CancelableOperation operation) {
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
    }

    @Test
    public void testSchedule() throws Exception {
        JsonMap metadata = JsonMap.newBuilder().putOpt("cool", "story").build();
        Future<ActionSchedule> pendingResult = automationEngine.schedule(scheduleInfo, metadata);
        runLooperTasks();

        // Verify the pendingResult
        assertTrue(pendingResult.isDone());
        assertFalse(pendingResult.isCancelled());
        assertNotNull(pendingResult.get());

        ScheduleEntry entry = automationDataManager.getScheduleEntry(pendingResult.get().getId());
        assertNotNull(entry);
        assertEquals(metadata, entry.metadata);
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
                RegionEvent event = RegionEvent.newBuilder()
                                               .setRegionId("region_id")
                                               .setSource("test_source")
                                               .setBoundaryEvent(RegionEvent.BOUNDARY_EVENT_ENTER)
                                               .build();
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
                RegionEvent event = RegionEvent.newBuilder()
                                               .setRegionId("region_id")
                                               .setSource("test_source")
                                               .setBoundaryEvent(RegionEvent.BOUNDARY_EVENT_EXIT)
                                               .build();
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

        verifyDelay(delay, null);
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
                RegionEvent event = RegionEvent.newBuilder()
                                               .setRegionId("region_id")
                                               .setSource("test_source")
                                               .setBoundaryEvent(RegionEvent.BOUNDARY_EVENT_EXIT)
                                               .build();
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

            assertEquals(scheduleInfo.getPriority(), priority);
            schedules.add(schedule(scheduleInfo));
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
    public void testExpiryListener() throws ExecutionException, InterruptedException {
        final Trigger trigger = Triggers.newCustomEventTriggerBuilder()
                                        .setCountGoal(1)
                                        .setEventName("name")
                                        .build();

        final ActionScheduleInfo expiredScheduleInfo = ActionScheduleInfo.newBuilder()
                                                                         .addTrigger(trigger)
                                                                         .addAction("test_action", JsonValue.wrap("action_value"))
                                                                         .setEnd(System.currentTimeMillis() - 1)
                                                                         .build();

        AutomationEngine.ScheduleListener<ActionSchedule> expiryListener = mock(AutomationEngine.ScheduleListener.class);
        automationEngine.setScheduleListener(expiryListener);

        // Schedule it
        schedule(expiredScheduleInfo);

        // Trigger the schedules
        CustomEvent.newBuilder("name")
                   .build()
                   .track();

        runLooperTasks();

        // Verify the listener was called
        verify(expiryListener).onScheduleExpired(any(ActionSchedule.class));
    }

    @Test
    public void testNewScheduleListener() throws ExecutionException, InterruptedException {
        AutomationEngine.ScheduleListener<ActionSchedule> listener = mock(AutomationEngine.ScheduleListener.class);
        automationEngine.setScheduleListener(listener);

        ActionSchedule schedule = schedule(scheduleInfo);

        // Verify the listener was called
        verify(listener).onNewSchedule(schedule);
    }

    @Test
    public void testCancelScheduleGroupListener() throws ExecutionException, InterruptedException {
        AutomationEngine.ScheduleListener<ActionSchedule> listener = mock(AutomationEngine.ScheduleListener.class);
        automationEngine.setScheduleListener(listener);

        ActionSchedule schedule = schedule(scheduleInfo);
        automationEngine.cancelGroup(scheduleInfo.getGroup());
        runLooperTasks();

        // Verify the listener was called
        verify(listener).onScheduleCancelled(any(ActionSchedule.class));
    }

    @Test
    public void testCancelScheduleListener() throws ExecutionException, InterruptedException {
        AutomationEngine.ScheduleListener<ActionSchedule> listener = mock(AutomationEngine.ScheduleListener.class);
        automationEngine.setScheduleListener(listener);

        ActionSchedule schedule = schedule(scheduleInfo);
        automationEngine.cancel(Collections.singleton(schedule.getId()));
        runLooperTasks();

        // Verify the listener was called
        verify(listener).onScheduleCancelled(any(ActionSchedule.class));
    }

    @Test
    public void testReachLimitScheduleListener() throws Exception {
        AutomationEngine.ScheduleListener<ActionSchedule> listener = mock(AutomationEngine.ScheduleListener.class);
        automationEngine.setScheduleListener(listener);

        final Trigger trigger = Triggers.newCustomEventTriggerBuilder()
                                        .setCountGoal(1)
                                        .setEventName("name")
                                        .build();

        verifyTrigger(trigger, new Runnable() {
            @Override
            public void run() {
                // Trigger the schedules
                CustomEvent.newBuilder("name")
                           .build()
                           .track();
            }
        });

        // Verify the listener was called
        verify(listener).onScheduleLimitReached(any(ActionSchedule.class));
    }

    @Test
    public void testPause() throws Exception {
        // Pause
        automationEngine.setPaused(true);

        ActionSchedule schedule = schedule(scheduleInfo);

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
        assertFalse(driver.executionCallbackMap.containsKey(schedule.getId()));

        // Resume
        automationEngine.setPaused(false);
        runLooperTasks();

        // Verify it's still idle
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_IDLE);
        assertFalse(driver.executionCallbackMap.containsKey(schedule.getId()));

        // Actually trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();
        runLooperTasks();

        // Verify it started preparing the schedule
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_PREPARING_SCHEDULE);
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

        ActionSchedule schedule = schedule(scheduleInfo);

        // Trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        // Finish preparing and executing the schedule
        driver.prepareCallbackMap.get(schedule.getId()).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        runLooperTasks();
        driver.executionCallbackMap.get(schedule.getId()).onFinish();
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

        Future<ActionSchedule> future = automationEngine.editSchedule(schedule.getId(), edits);
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

        ActionSchedule schedule = schedule(scheduleInfo);

        // Verify its idle
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_IDLE);

        // Update the schedule
        final ActionScheduleEdits edits = ActionScheduleEdits.newBuilder()
                                                             .setEnd(0)
                                                             .build();

        Future<ActionSchedule> future = automationEngine.editSchedule(schedule.getId(), edits);
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

        ActionSchedule schedule = schedule(scheduleInfo);

        // Trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        // Finish preparing and executing the schedule
        driver.prepareCallbackMap.get(schedule.getId()).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        runLooperTasks();
        driver.executionCallbackMap.get(schedule.getId()).onFinish();
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
            public void schedule(long delay, @NonNull CancelableOperation operation) {
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

    @Test
    public void testCancelPrepareResult() throws ExecutionException, InterruptedException {
        ActionSchedule schedule = schedule(scheduleInfo);

        // Trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        // Finish preparing and executing the schedule
        driver.prepareCallbackMap.get(schedule.getId()).onFinish(AutomationDriver.PREPARE_RESULT_CANCEL);
        runLooperTasks();

        // Verify it's cancelled (Deleted)
        assertNull(automationDataManager.getScheduleEntry(schedule.getId()));
    }

    @Test
    public void testSkipIgnorePrepareResult() throws ExecutionException, InterruptedException {
        ActionSchedule schedule = schedule(scheduleInfo);

        // Trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        // Finish preparing and executing the schedule
        driver.prepareCallbackMap.get(schedule.getId()).onFinish(AutomationDriver.PREPARE_RESULT_SKIP);
        runLooperTasks();

        // Verify it's idle
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_IDLE);
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getCount(), 0);
    }

    @Test
    public void testSkipPenalizePrepareResult() throws ExecutionException, InterruptedException {
        final ActionScheduleInfo scheduleInfo = ActionScheduleInfo.newBuilder()
                                                                  .addTrigger(Triggers.newCustomEventTriggerBuilder()
                                                                                      .setCountGoal(1)
                                                                                      .setEventName("event")
                                                                                      .build())
                                                                  .addAction("test_action", JsonValue.wrap("action_value"))
                                                                  .setInterval(10, TimeUnit.SECONDS)
                                                                  .setLimit(2)
                                                                  .build();

        ActionSchedule schedule = schedule(scheduleInfo);

        // Trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        // Finish preparing and executing the schedule
        driver.prepareCallbackMap.get(schedule.getId()).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
        runLooperTasks();

        // Verify it's finished
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_PAUSED);
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getCount(), 1);
    }

    @Test
    public void testInvalidatePrepareResult() throws ExecutionException, InterruptedException {
        final ActionScheduleInfo scheduleInfo = ActionScheduleInfo.newBuilder()
                                                                  .addTrigger(Triggers.newCustomEventTriggerBuilder()
                                                                                      .setCountGoal(1)
                                                                                      .setEventName("event")
                                                                                      .build())
                                                                  .addAction("test_action", JsonValue.wrap("action_value"))
                                                                  .setInterval(10, TimeUnit.SECONDS)
                                                                  .setLimit(2)
                                                                  .build();

        ActionSchedule schedule = schedule(scheduleInfo);

        // Trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        // Edit the schedule
        final ActionScheduleEdits edits = ActionScheduleEdits.newBuilder()
                                                             .setPriority(300)
                                                             .build();

        automationEngine.editSchedule(schedule.getId(), edits);
        driver.prepareCallbackMap.get(schedule.getId()).onFinish(AutomationDriver.PREPARE_RESULT_INVALIDATE);
        runLooperTasks();

        // Verify the updated schedule is being prepared
        ActionSchedule updated = driver.preparedSchedulesMap.get(schedule.getId());
        assertEquals(300, updated.getInfo().getPriority());
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

        ActionSchedule schedule = schedule(scheduleInfo);

        // Verify it was saved
        ScheduleEntry entry = automationDataManager.getScheduleEntry(schedule.getId());
        assertEquals(entry.scheduleId, schedule.getId());

        // Trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        // Resolve delay seconds
        if (delay.getSeconds() > 0) {
            assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_TIME_DELAYED);

            // Verify the pending date is set
            entry = automationDataManager.getScheduleEntry(schedule.getId());
            assertTrue(entry.getDelayFinishDate() > System.currentTimeMillis());

            // Set the pending date to now
            entry.setDelayFinishDate(System.currentTimeMillis());
            automationDataManager.saveSchedules(Collections.singletonList(entry));

            runLooperTasks();
            assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_PREPARING_SCHEDULE);
        }

        // Preparing schedule
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_PREPARING_SCHEDULE);
        driver.prepareCallbackMap.get(schedule.getId()).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        runLooperTasks();

        // Waiting on conditions - the rest of the delay
        if (resolveDelay != null) {
            assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_WAITING_SCHEDULE_CONDITIONS);

            // Resolve delay
            resolveDelay.run();
            runLooperTasks();
        } else {
            // Straight to executing
            assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_EXECUTING);
        }

        // Verify it started executing the schedule
        assertTrue(driver.executionCallbackMap.containsKey(schedule.getId()));
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_EXECUTING);

        // Finish executing the schedule
        driver.executionCallbackMap.get(schedule.getId()).onFinish();
        runLooperTasks();

        // Schedule should be deleted
        assertNull(automationDataManager.getScheduleEntry(schedule.getId()));
    }

    private void verifyTrigger(Trigger trigger, Runnable generateEvents) throws Exception {
        final ActionScheduleInfo scheduleInfo = ActionScheduleInfo.newBuilder()
                                                                  .addTrigger(trigger)
                                                                  .addAction("test_action", JsonValue.wrap("action_value"))
                                                                  .setGroup("group")
                                                                  .setLimit(2)
                                                                  .build();

        ActionSchedule schedule = schedule(scheduleInfo);

        // Verify it was saved
        ScheduleEntry entry = automationDataManager.getScheduleEntry(schedule.getId());
        assertEquals(entry.scheduleId, schedule.getId());

        // Trigger the schedule
        if (generateEvents != null) {
            generateEvents.run();
        }

        runLooperTasks();

        // Verify it started preparing the schedule
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_PREPARING_SCHEDULE);
        driver.prepareCallbackMap.get(schedule.getId()).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        runLooperTasks();

        // Verify it started executing the schedule
        assertTrue(driver.executionCallbackMap.containsKey(schedule.getId()));
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_EXECUTING);

        // Finish executing the schedule
        driver.executionCallbackMap.get(schedule.getId()).onFinish();
        runLooperTasks();

        // Verify it's back to idle and progress is set
        assertTrue(driver.executionCallbackMap.containsKey(schedule.getId()));
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getExecutionState(), ScheduleEntry.STATE_IDLE);
        assertEquals(automationDataManager.getScheduleEntry(schedule.getId()).getCount(), 1);

        // Trigger it again
        if (generateEvents != null) {
            generateEvents.run();
        }

        runLooperTasks();
        driver.executionCallbackMap.get(schedule.getId()).onFinish();
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

    private ActionSchedule schedule(ActionScheduleInfo scheduleInfo) throws ExecutionException, InterruptedException {
        PendingResult<ActionSchedule> future = automationEngine.schedule(scheduleInfo, JsonMap.EMPTY_MAP);
        runLooperTasks();
        return future.get();
    }

    private static class TestActionScheduleDriver extends ActionAutomationDriver {

        Map<String, ExecutionCallback> executionCallbackMap = new HashMap<>();
        Map<String, PrepareScheduleCallback> prepareCallbackMap = new HashMap<>();
        Map<String, ActionSchedule> preparedSchedulesMap = new HashMap<>();

        ArrayList<Integer> priorityList = new ArrayList<>();

        @Override
        public void onExecuteTriggeredSchedule(@NonNull ActionSchedule schedule, @NonNull ExecutionCallback finishCallback) {
            executionCallbackMap.put(schedule.getId(), finishCallback);
        }

        @Override
        public void onPrepareSchedule(@NonNull ActionSchedule schedule, @NonNull PrepareScheduleCallback prepareCallback) {
            prepareCallbackMap.put(schedule.getId(), prepareCallback);
            priorityList.add(schedule.getInfo().getPriority());
            preparedSchedulesMap.put(schedule.getId(), schedule);
        }

    }

}
