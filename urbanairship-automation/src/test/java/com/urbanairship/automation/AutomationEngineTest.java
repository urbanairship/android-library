/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;

import com.urbanairship.ApplicationMetrics;
import com.urbanairship.CancelableOperation;
import com.urbanairship.PendingResult;
import com.urbanairship.ShadowAirshipExecutorsLegacy;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.analytics.location.RegionEvent;
import com.urbanairship.automation.actions.Actions;
import com.urbanairship.automation.alarms.OperationScheduler;
import com.urbanairship.automation.storage.AutomationDao;
import com.urbanairship.automation.storage.AutomationDatabase;
import com.urbanairship.automation.storage.FullSchedule;
import com.urbanairship.automation.storage.LegacyDataMigrator;
import com.urbanairship.automation.storage.ScheduleState;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.json.ValueMatcher;
import com.urbanairship.util.VersionUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Config(
        sdk = 28,
        shadows = { ShadowAirshipExecutorsLegacy.class },
        application = TestApplication.class
)
@RunWith(AndroidJUnit4.class)
@LooperMode(LooperMode.Mode.LEGACY)
public class AutomationEngineTest {

    private TestDriver driver;
    private LegacyDataMigrator mockDataMigrator;
    private AutomationEngine automationEngine;
    private TestActivityMonitor activityMonitor;
    private ApplicationMetrics mockMetrics;
    private Schedule<Actions> schedule;
    private Context context;
    private AutomationDatabase automationDatabase;
    private AutomationDao dao;

    final OperationScheduler handlerScheduler = new OperationScheduler() {
        @Override
        public void schedule(long delay, @NonNull Runnable runnable) {
            Handler handler;
            if (runnable instanceof CancelableOperation) {
                handler = ((CancelableOperation) runnable).getHandler();
            } else {
                handler = new Handler(Looper.getMainLooper());
            }

            handler.postDelayed(runnable, delay);
        }
    };

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();

        schedule = Schedule.newBuilder(new Actions(JsonMap.newBuilder()
                                                          .put("test_action", JsonValue.wrap("action_value"))
                                                          .build()))
                           .addTrigger(Triggers.newCustomEventTriggerBuilder()
                                               .setCountGoal(1)
                                               .setEventName("event")
                                               .build())
                           .setGroup("group")
                           .setMetadata(JsonMap.newBuilder()
                                               .putOpt("cool", "story")
                                               .build())
                           .build();

        activityMonitor = new TestActivityMonitor();

        mockMetrics = mock(ApplicationMetrics.class);
        TestApplication.getApplication().setApplicationMetrics(mockMetrics);

        driver = new TestDriver();
        mockDataMigrator = mock(LegacyDataMigrator.class);
        automationDatabase = Room.inMemoryDatabaseBuilder(context, AutomationDatabase.class)
                                 .allowMainThreadQueries()
                                 .build();
        dao = automationDatabase.getScheduleDao();
        automationEngine = new AutomationEngine(UAirship.shared().getAnalytics(), activityMonitor, handlerScheduler, dao, mockDataMigrator);

        automationEngine.start(driver);
        runLooperTasks();
    }

    @After
    public void teardown() {
        automationEngine.stop();
        automationDatabase.close();
    }

    @Test
    public void testSchedule() throws Exception {
        Future<Boolean> pendingResult = automationEngine.schedule(schedule);
        runLooperTasks();

        // Verify the pendingResult
        assertTrue(pendingResult.isDone());
        assertFalse(pendingResult.isCancelled());
        assertTrue(pendingResult.get());

        FullSchedule entity = dao.getSchedule(schedule.getId());
        assertNotNull(entity);

        assertEquals(schedule, ScheduleConverters.convert(entity));
    }

    @Test
    public void testGetScheduleWrongType() throws Exception {
        Future<Boolean> pendingResult = automationEngine.schedule(schedule);
        runLooperTasks();
        assertTrue(pendingResult.get());

        // Wrong type
        PendingResult<Schedule<Actions>> actionSchedulePendingResult = automationEngine.getSchedule(schedule.getId(), Schedule.TYPE_IN_APP_MESSAGE);
        PendingResult<Collection<Schedule<Actions>>> actionSchedulesByTypePendingResult = automationEngine.getSchedulesByType(Schedule.TYPE_IN_APP_MESSAGE);
        PendingResult<Collection<Schedule<Actions>>> actionSchedulesByGroupPendingResult = automationEngine.getSchedules(schedule.getGroup(), Schedule.TYPE_IN_APP_MESSAGE);
        runLooperTasks();
        assertNull(actionSchedulePendingResult.get());
        assertTrue(actionSchedulesByGroupPendingResult.get().isEmpty());
        assertTrue(actionSchedulesByTypePendingResult.get().isEmpty());

        // Proper type
        actionSchedulePendingResult = automationEngine.getSchedule(schedule.getId(), Schedule.TYPE_ACTION);
        actionSchedulesByTypePendingResult = automationEngine.getSchedulesByType(Schedule.TYPE_ACTION);
        actionSchedulesByGroupPendingResult = automationEngine.getSchedules(schedule.getGroup(), Schedule.TYPE_ACTION);
        runLooperTasks();
        assertEquals(schedule, actionSchedulePendingResult.get());
        assertTrue(actionSchedulesByTypePendingResult.get().contains(schedule));
        assertTrue(actionSchedulesByGroupPendingResult.get().contains(schedule));
    }

    @Test
    public void testMigrateOnStart() {
        automationEngine.stop();
        clearInvocations(mockDataMigrator);

        automationEngine.start(driver);
        runLooperTasks();
        verify(mockDataMigrator).migrateData(dao);
    }

    @Test
    public void testCustomEvent() throws Exception {
        Trigger trigger = Triggers.newCustomEventTriggerBuilder()
                                  .setCountGoal(1)
                                  .setEventName("name")
                                  .build();

        final CustomEvent event = CustomEvent.newBuilder("name").build();
        verifyTrigger(trigger, new Runnable() {
            @Override
            public void run() {
                event.track();
            }
        }, event.toJsonValue());
    }

    @Test
    public void testCustomEventValue() throws Exception {
        Trigger trigger = Triggers.newCustomEventTriggerBuilder()
                                  .setValueGoal(2.0)
                                  .setEventName("some name")
                                  .build();

        final CustomEvent event = CustomEvent.newBuilder("some name")
                                             .setEventValue(2.0)
                                             .build();

        verifyTrigger(trigger, new Runnable() {
            @Override
            public void run() {
                event.track();
            }
        }, event.toJsonValue());
    }

    @Test
    public void testEnterRegionEvent() throws Exception {
        Trigger trigger = Triggers.newEnterRegionTriggerBuilder()
                                  .setRegionId("region_id")
                                  .setGoal(1.0)
                                  .build();

        final RegionEvent event = RegionEvent.newBuilder()
                                             .setRegionId("region_id")
                                             .setSource("test_source")
                                             .setBoundaryEvent(RegionEvent.BOUNDARY_EVENT_ENTER)
                                             .build();

        verifyTrigger(trigger, new Runnable() {
            @Override
            public void run() {
                UAirship.shared().getAnalytics().addEvent(event);
            }
        }, event.toJsonValue());
    }

    @Test
    public void testExitRegionEvent() throws Exception {
        Trigger trigger = Triggers.newExitRegionTriggerBuilder()
                                  .setRegionId("region_id")
                                  .setGoal(1.0)
                                  .build();

        final RegionEvent event = RegionEvent.newBuilder()
                                             .setRegionId("region_id")
                                             .setSource("test_source")
                                             .setBoundaryEvent(RegionEvent.BOUNDARY_EVENT_EXIT)
                                             .build();
        verifyTrigger(trigger, new Runnable() {
            @Override
            public void run() {
                UAirship.shared().getAnalytics().addEvent(event);
            }
        }, event.toJsonValue());
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
        }, JsonValue.NULL);
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
        }, JsonValue.NULL);
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
        }, JsonValue.NULL);
    }

    @Test
    public void testActiveSessionPaused() throws Exception {
        Trigger trigger = Triggers.newActiveSessionTriggerBuilder()
                                  .setGoal(1)
                                  .build();

        verifyTrigger(trigger, new Runnable() {
            @Override
            public void run() {
                automationEngine.setPaused(true);
                runLooperTasks();
                activityMonitor.startActivity();
                runLooperTasks();
                automationEngine.setPaused(false);
                runLooperTasks();
            }
        }, JsonValue.NULL);
    }

    @Test
    public void testActiveSessionLateSubscription() throws Exception {
        Trigger trigger = Triggers.newActiveSessionTriggerBuilder()
                                  .setGoal(1)
                                  .build();

        activityMonitor.startActivity();

        verifyTrigger(trigger, null, JsonValue.NULL);
    }

    @Test
    public void testVersion() throws Exception {
        when(mockMetrics.getAppVersionUpdated()).thenReturn(true);
        when(mockMetrics.getCurrentAppVersion()).thenReturn(2l);

        Trigger trigger = Triggers.newVersionTriggerBuilder(ValueMatcher.newNumberRangeMatcher(2.0, 4.0))
                                  .setGoal(1)
                                  .build();

        verifyTrigger(trigger, null, VersionUtils.createVersionObject(2l).toJsonValue());
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
        }, JsonValue.wrap("screen"));
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
    public void testSortSchedule() throws Exception {
        Pair<Integer, Long>[] prioritiesAndTriggeredTimes = new Pair[] {
                new Pair<>(5, 500L),
                new Pair<>(2, 200L),
                new Pair<>(4, 200L),
                new Pair<>(6, 500L)
        };

        ArrayList<Pair<Integer, Long>> expectedExecutionOrder = new ArrayList<>(Arrays.asList(prioritiesAndTriggeredTimes));
        Collections.sort(expectedExecutionOrder, (o1, o2) -> {
            if (o1.second.equals(o2.second)) {
                if (o1.first.equals(o2.first)) {
                    return 0;
                } else {
                    return o1.first > o2.first ? 1 : -1;
                }
            } else {
                return o1.second > o2.second ? 1 : -1;
            }
        });

        // Add schedules out of order
        for (Pair<Integer, Long> sortParameters : prioritiesAndTriggeredTimes) {
            final Schedule<Actions> schedule = Schedule.newBuilder(new Actions(JsonMap.EMPTY_MAP))
                                                       .addTrigger(Triggers.newCustomEventTriggerBuilder()
                                                                           .setCountGoal(1)
                                                                           .setEventName("event")
                                                                           .build())
                                                       .setPriority(sortParameters.first)
                                                       .setTriggeredTime(sortParameters.second)
                                                       .build();

            schedule(schedule);
        }

        // Trigger the schedules
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        // Verify the schedules were executed by triggered time and then priority order
        assertEquals(expectedExecutionOrder, driver.priorityAndTriggeredTimeList);
    }

    @Test
    public void testExpiryListener() throws ExecutionException, InterruptedException {
        Schedule<Actions> schedule = Schedule.newBuilder(new Actions(JsonMap.EMPTY_MAP))
                                             .addTrigger(Triggers.newCustomEventTriggerBuilder()
                                                                 .setCountGoal(1)
                                                                 .setEventName("name")
                                                                 .build())
                                             .setEnd(System.currentTimeMillis() - 1)
                                             .build();

        AutomationEngine.ScheduleListener expiryListener = mock(AutomationEngine.ScheduleListener.class);
        automationEngine.setScheduleListener(expiryListener);

        // Schedule it
        schedule(schedule);

        // Trigger the schedules
        CustomEvent.newBuilder("name")
                   .build()
                   .track();

        runLooperTasks();

        // Verify the listener was called
        verify(expiryListener).onScheduleExpired(schedule);
    }

    @Test
    public void testNewScheduleListener() throws ExecutionException, InterruptedException {
        AutomationEngine.ScheduleListener listener = mock(AutomationEngine.ScheduleListener.class);
        automationEngine.setScheduleListener(listener);

        schedule(schedule);

        // Verify the listener was called
        verify(listener).onNewSchedule(schedule);
    }

    @Test
    public void testCancelScheduleGroupListener() throws ExecutionException, InterruptedException {
        AutomationEngine.ScheduleListener listener = mock(AutomationEngine.ScheduleListener.class);
        automationEngine.setScheduleListener(listener);

        schedule(schedule);
        automationEngine.cancelGroup(schedule.getGroup());
        runLooperTasks();

        // Verify the listener was called
        verify(listener).onScheduleCancelled(schedule);
    }

    @Test
    public void testCancelScheduleListener() throws ExecutionException, InterruptedException {
        AutomationEngine.ScheduleListener listener = mock(AutomationEngine.ScheduleListener.class);
        automationEngine.setScheduleListener(listener);

        schedule(schedule);
        automationEngine.cancel(Collections.singleton(schedule.getId()));
        runLooperTasks();

        // Verify the listener was called
        verify(listener).onScheduleCancelled(schedule);
    }

    @Test
    public void testReachLimitScheduleListener() throws Exception {
        Schedule<Actions> schedule = Schedule.newBuilder(new Actions(JsonMap.EMPTY_MAP))
                                             .addTrigger(Triggers.newCustomEventTriggerBuilder()
                                                                 .setCountGoal(1)
                                                                 .setEventName("name")
                                                                 .build())
                                             .setLimit(1)
                                             .build();

        // Schedule it
        schedule(schedule);

        AutomationEngine.ScheduleListener listener = mock(AutomationEngine.ScheduleListener.class);
        automationEngine.setScheduleListener(listener);

        // Trigger the schedules
        CustomEvent.newBuilder("name")
                   .build()
                   .track();
        runLooperTasks();

        // Prepare
        driver.prepareCallbackMap.get(schedule.getId()).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        runLooperTasks();

        // Execute
        driver.executionCallbackMap.get(schedule.getId()).onFinish();
        runLooperTasks();

        // Verify the listener was called
        verify(listener).onScheduleLimitReached(argThat(s -> schedule.getId().equals(s.getId())));
    }

    @Test
    public void testPause() throws Exception {
        // Pause
        automationEngine.setPaused(true);

        schedule(schedule);

        // Verify it was saved
        FullSchedule entry = dao.getSchedule(schedule.getId());
        assertEquals(entry.schedule.scheduleId, schedule.getId());

        // Try to trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        // Verify it's still idle
        verifyState(schedule, ScheduleState.IDLE);
        assertFalse(driver.executionCallbackMap.containsKey(schedule.getId()));

        // Resume
        automationEngine.setPaused(false);
        runLooperTasks();

        // Verify it's still idle
        verifyState(schedule, ScheduleState.IDLE);
        assertFalse(driver.executionCallbackMap.containsKey(schedule.getId()));

        // Actually trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();
        runLooperTasks();

        // Verify it started preparing the schedule
        verifyState(schedule, ScheduleState.PREPARING_SCHEDULE);
    }

    @Test
    public void testEditSchedule() throws Exception {
        final Schedule<Actions> schedule = Schedule.newBuilder(this.schedule)
                                                   .setEditGracePeriod(100, TimeUnit.SECONDS)
                                                   .build();

        schedule(schedule);

        // Trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        // Finish preparing and executing the schedule
        driver.prepareCallbackMap.get(schedule.getId())
                                 .onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        runLooperTasks();
        driver.executionCallbackMap.get(schedule.getId()).onFinish();
        runLooperTasks();

        // Verify it's finished
        verifyState(this.schedule, ScheduleState.FINISHED);

        // Update the schedule with a end time set to the next day
        long end = System.currentTimeMillis() + 1000 * 60 * 60 * 24;

        List<String> constraintIds = new ArrayList<>();
        constraintIds.add("foo");
        constraintIds.add("bar");

        final ScheduleEdits<Actions> edits = ScheduleEdits.newBuilder(new Actions(JsonMap.newBuilder()
                                                                                         .put("another_action", "COOL")
                                                                                         .build()))
                                                          .setLimit(2)
                                                          .setStart(10)
                                                          .setEnd(end)
                                                          .setPriority(300)
                                                          .setCampaigns(JsonValue.wrapOpt("campaigns"))
                                                          .setFrequencyConstraintIds(constraintIds)
                                                          .build();

        Future<Boolean> future = automationEngine.editSchedule(schedule.getId(), edits);
        runLooperTasks();
        assertEquals(Boolean.TRUE, future.get());

        Future<Schedule<Actions>> updatedFuture = automationEngine.getSchedule(schedule.getId(), Schedule.TYPE_ACTION);
        runLooperTasks();

        Schedule<Actions> updated = updatedFuture.get();

        // Verify it's now idle
        verifyState(this.schedule, ScheduleState.IDLE);

        // Verify it was updated
        assertEquals(edits.getLimit().intValue(), updated.getLimit());
        assertEquals(edits.getStart().longValue(), updated.getStart());
        assertEquals(edits.getEnd().longValue(), updated.getEnd());
        assertEquals(edits.getPriority().intValue(), updated.getPriority());
        assertEquals("COOL", updated.getData().getActionsMap().get("another_action").getString());
        assertEquals(constraintIds, updated.getFrequencyConstraintIds());
        assertEquals(JsonValue.wrapOpt("campaigns"), updated.getCampaigns());
    }

    @Test
    public void testEditScheduleEndZero() throws Exception {
        final Schedule<Actions> scheduleInfo = Schedule.newBuilder(this.schedule)
                                                       .setEditGracePeriod(100, TimeUnit.SECONDS)
                                                       .build();

        schedule(scheduleInfo);
        runLooperTasks();

        // Verify its idle
        verifyState(scheduleInfo, ScheduleState.IDLE);

        // Update the schedule
        final ScheduleEdits<?> edits = ScheduleEdits.newBuilder().setEnd(System.currentTimeMillis()).build();

        Future<Boolean> future = automationEngine.editSchedule(scheduleInfo.getId(), edits);
        runLooperTasks();
        assertEquals(Boolean.TRUE, future.get());

        Future<Schedule<Actions>> updatedFuture = automationEngine.getSchedule(scheduleInfo.getId(), Schedule.TYPE_ACTION);
        runLooperTasks();
        Schedule<Actions> updated = updatedFuture.get();

        // Verify it's finished
        verifyState(scheduleInfo, ScheduleState.FINISHED);

        // Verify it was updated
        assertEquals(edits.getEnd().longValue(), updated.getEnd());
    }

    @Test
    public void testInterval() throws Exception {
        final Schedule schedule = Schedule.newBuilder(this.schedule)
                                          .setInterval(10, TimeUnit.SECONDS)
                                          .setLimit(2)
                                          .build();
        schedule(schedule);

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
        verifyState(schedule, ScheduleState.PAUSED);

        // Advance the scheduler
        advanceAutomationLooperScheduler(TimeUnit.SECONDS.toMillis(10));

        // Verify its now idle
        verifyState(schedule, ScheduleState.IDLE);
    }

    @Test
    public void testRestoreInterval() throws Exception {
        final Schedule schedule = Schedule.newBuilder(this.schedule)
                                          .setInterval(10, TimeUnit.SECONDS)
                                          .setLimit(2)
                                          .build();

        schedule(schedule);

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
        verifyState(schedule, ScheduleState.PAUSED);

        // Restart the engine
        automationEngine.stop();
        automationEngine.start(driver);
        runLooperTasks();

        // Verify its still paused
        verifyState(schedule, ScheduleState.PAUSED);

        // Advance the scheduler
        advanceAutomationLooperScheduler(TimeUnit.SECONDS.toMillis(10));

        // Verify its now idle
        verifyState(schedule, ScheduleState.IDLE);
    }

    @Test
    public void testOnScheduleChangeBeforeEngineStarts() {
        automationEngine = new AutomationEngine(UAirship.shared().getAnalytics(), activityMonitor,
                handlerScheduler, dao, mockDataMigrator);

        // Should not crash
        automationEngine.checkPendingSchedules();
    }

    @Test
    public void testCancelPrepareResult() throws ExecutionException, InterruptedException {
        schedule(schedule);

        // Trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        // Finish preparing and executing the schedule
        driver.prepareCallbackMap.get(schedule.getId()).onFinish(AutomationDriver.PREPARE_RESULT_CANCEL);
        runLooperTasks();

        // Verify it's cancelled (Deleted)
        assertNull(dao.getSchedule(schedule.getId()));
    }

    @Test
    public void testSkipIgnorePrepareResult() throws ExecutionException, InterruptedException {
        schedule(schedule);

        // Trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        // Finish preparing and executing the schedule
        driver.prepareCallbackMap.get(schedule.getId()).onFinish(AutomationDriver.PREPARE_RESULT_SKIP);
        runLooperTasks();

        // Verify it's idle
        verifyState(schedule, ScheduleState.IDLE);
        assertEquals(dao.getSchedule(schedule.getId()).schedule.count, 0);
    }

    @Test
    public void testSkipPenalizePrepareResult() throws ExecutionException, InterruptedException {
        Schedule<Actions> schedule = Schedule.newBuilder(this.schedule)
                                             .setInterval(10, TimeUnit.SECONDS)
                                             .setLimit(2)
                                             .build();

        schedule(schedule);

        // Trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        // Finish preparing and executing the schedule
        driver.prepareCallbackMap.get(schedule.getId()).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
        runLooperTasks();

        // Verify it's finished
        verifyState(this.schedule, ScheduleState.PAUSED);
        assertEquals(dao.getSchedule(this.schedule.getId()).schedule.count, 1);
    }

    @Test
    public void testInvalidatePrepareResult() throws ExecutionException, InterruptedException {
        Schedule<Actions> schedule = Schedule.newBuilder(this.schedule)
                                             .setInterval(10, TimeUnit.SECONDS)
                                             .setLimit(2)
                                             .build();

        schedule(schedule);

        // Trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        // Edit the schedule
        ScheduleEdits<? extends ScheduleData> edits = ScheduleEdits.newBuilder()
                                                                   .setPriority(300)
                                                                   .build();

        automationEngine.editSchedule(schedule.getId(), edits);
        driver.prepareCallbackMap.get(schedule.getId()).onFinish(AutomationDriver.PREPARE_RESULT_INVALIDATE);
        runLooperTasks();

        // Verify the updated schedule is being prepared
        Schedule<Actions> updated = driver.preparedSchedulesMap.get(schedule.getId());
        assertEquals(300, updated.getPriority());
    }

    @Test
    public void testInterrupted() throws ExecutionException, InterruptedException {
        Schedule<Actions> schedule = Schedule.newBuilder(this.schedule)
                                             .setLimit(2)
                                             .build();

        schedule(schedule);

        // Trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        // Preparing schedule
        verifyState(schedule, ScheduleState.PREPARING_SCHEDULE);
        driver.prepareCallbackMap.get(schedule.getId()).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        runLooperTasks();

        // Verify it started executing the schedule
        assertTrue(driver.executionCallbackMap.containsKey(schedule.getId()));
        verifyState(schedule, ScheduleState.EXECUTING);

        automationEngine.stop();
        automationEngine.start(driver);
        runLooperTasks();

        assertEquals(schedule.getId(), driver.interrupted.get(schedule.getId()).getId());
        verifyState(schedule, ScheduleState.IDLE);
    }

    @Test
    public void testSkipReadyResult() throws ExecutionException, InterruptedException {
        schedule(schedule);

        // Trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        driver.onCheckExecutionReadinessResult = AutomationDriver.READY_RESULT_SKIP;

        // Preparing schedule
        verifyState(schedule, ScheduleState.PREPARING_SCHEDULE);
        driver.prepareCallbackMap.get(schedule.getId()).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        runLooperTasks();

        // Verify it's idle
        verifyState(schedule, ScheduleState.IDLE);
        assertEquals(dao.getSchedule(schedule.getId()).schedule.count, 0);
    }

    @Test
    public void testResumedActivitiesCheckExecutionReadiness() throws ExecutionException, InterruptedException {
        schedule(schedule);

        // Trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        driver.onCheckExecutionReadinessResult = AutomationDriver.READY_RESULT_NOT_READY;

        // Preparing schedule
        verifyState(schedule, ScheduleState.PREPARING_SCHEDULE);
        driver.prepareCallbackMap.get(schedule.getId()).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        runLooperTasks();

        // Verify it's waiting
        verifyState(schedule, ScheduleState.WAITING_SCHEDULE_CONDITIONS);

        // Resume an activity to verify schedules get checked again
        driver.onCheckExecutionReadinessResult = AutomationDriver.READY_RESULT_CONTINUE;
        Activity activity = new Activity();
        activityMonitor.resumeActivity(activity);
        runLooperTasks();

        // Verify it's now executing
        verifyState(schedule, ScheduleState.EXECUTING);
    }

    private void verifyDelay(ScheduleDelay delay, Runnable resolveDelay) throws Exception {
        final Schedule<Actions> schedule = Schedule.newBuilder(this.schedule)
                                                   .setDelay(delay)
                                                   .build();

        schedule(schedule);

        // Verify it was saved
        FullSchedule entity = dao.getSchedule(schedule.getId());
        assertEquals(entity.schedule.scheduleId, schedule.getId());

        // Trigger the schedule
        CustomEvent.newBuilder("event")
                   .build()
                   .track();

        runLooperTasks();

        // Resolve delay seconds
        if (delay.getSeconds() > 0) {
            verifyState(schedule, ScheduleState.TIME_DELAYED);
            runLooperTasks();
            verifyState(schedule, ScheduleState.PREPARING_SCHEDULE);
        }

        // Preparing schedule
        verifyState(schedule, ScheduleState.PREPARING_SCHEDULE);
        driver.prepareCallbackMap.get(schedule.getId()).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        runLooperTasks();

        // Waiting on conditions - the rest of the delay
        if (resolveDelay != null) {
            verifyState(schedule, ScheduleState.WAITING_SCHEDULE_CONDITIONS);

            // Resolve delay
            resolveDelay.run();
            runLooperTasks();
        } else {
            // Straight to executing
            verifyState(schedule, ScheduleState.EXECUTING);
        }

        // Verify it started executing the schedule
        assertTrue(driver.executionCallbackMap.containsKey(schedule.getId()));
        verifyState(schedule, ScheduleState.EXECUTING);

        // Finish executing the schedule
        driver.executionCallbackMap.get(schedule.getId()).onFinish();
        runLooperTasks();

        // Schedule should be deleted
        assertNull(dao.getSchedule(schedule.getId()));
    }

    private void verifyTrigger(Trigger trigger, Runnable generateEvents, JsonValue expectedEvent) throws Exception {
        final Schedule<Actions> schedule = Schedule.newBuilder(new Actions(JsonMap.EMPTY_MAP))
                                                   .addTrigger(trigger)
                                                   .setGroup("group")
                                                   .setLimit(2)
                                                   .build();

        schedule(schedule);

        // Trigger the schedule
        if (generateEvents != null) {
            generateEvents.run();
        }

        runLooperTasks();

        // Verify the trigger context
        TriggerContext triggerContext = driver.preparedTriggerContextMap.get(schedule.getId());
        assertEquals(trigger, triggerContext.getTrigger());
        assertEquals(expectedEvent, triggerContext.getEvent());

        // Verify it started preparing the schedule
        verifyState(schedule, ScheduleState.PREPARING_SCHEDULE);
        driver.prepareCallbackMap.get(schedule.getId()).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        runLooperTasks();

        // Verify it started executing the schedule
        assertTrue(driver.executionCallbackMap.containsKey(schedule.getId()));
        verifyState(schedule, ScheduleState.EXECUTING);

        // Finish executing the schedule
        driver.executionCallbackMap.get(schedule.getId()).onFinish();
        runLooperTasks();

        // Verify it's back to idle and progress is set
        assertTrue(driver.executionCallbackMap.containsKey(schedule.getId()));
        verifyState(schedule, ScheduleState.IDLE);
        assertEquals(dao.getSchedule(schedule.getId()).schedule.count, 1);

        // Trigger it again
        if (generateEvents != null) {
            generateEvents.run();
        }

        runLooperTasks();
        driver.executionCallbackMap.get(schedule.getId()).onFinish();
        runLooperTasks();

        // Schedule should be deleted
        assertNull(dao.getSchedule(schedule.getId()));
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

    private void schedule(Schedule<?> schedule) throws ExecutionException, InterruptedException {
        PendingResult<Boolean> future = automationEngine.schedule(schedule);
        runLooperTasks();
        assertTrue(future.get());
    }

    private void verifyState(Schedule<?> schedule, int state) {
        assertEquals(dao.getSchedule(schedule.getId()).schedule.executionState, state);
    }

    private static class TestDriver implements AutomationDriver {

        Map<String, ExecutionCallback> executionCallbackMap = new HashMap<>();
        Map<String, PrepareScheduleCallback> prepareCallbackMap = new HashMap<>();
        Map<String, Schedule> preparedSchedulesMap = new HashMap<>();
        Map<String, TriggerContext> preparedTriggerContextMap = new HashMap<>();
        Map<String, Schedule> interrupted = new HashMap<>();
        ArrayList<Pair<Integer, Long>> priorityAndTriggeredTimeList = new ArrayList<>();

        int onCheckExecutionReadinessResult = READY_RESULT_CONTINUE;

        @Override
        public void onExecuteTriggeredSchedule(@NonNull Schedule schedule, @NonNull ExecutionCallback finishCallback) {
            executionCallbackMap.put(schedule.getId(), finishCallback);
        }

        @Override
        public void onPrepareSchedule(@NonNull Schedule schedule, @Nullable TriggerContext triggerContext, @NonNull PrepareScheduleCallback prepareCallback) {
            prepareCallbackMap.put(schedule.getId(), prepareCallback);
            priorityAndTriggeredTimeList.add(new Pair<>(schedule.getPriority(), schedule.getTriggeredTime()));
            preparedSchedulesMap.put(schedule.getId(), schedule);
            preparedTriggerContextMap.put(schedule.getId(), triggerContext);
        }

        @Override
        public int onCheckExecutionReadiness(@NonNull Schedule schedule) {
            return onCheckExecutionReadinessResult;
        }

        @Override
        public void onScheduleExecutionInterrupted(Schedule<? extends ScheduleData> schedule) {
            interrupted.put(schedule.getId(), schedule);
        }

    }

}
