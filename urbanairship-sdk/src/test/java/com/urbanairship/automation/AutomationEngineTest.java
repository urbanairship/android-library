/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.os.Looper;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PendingResult;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.json.JsonValue;
import com.urbanairship.location.RegionEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

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

public class AutomationEngineTest extends BaseTestCase {

    private TestActionScheduleDriver driver;
    private AutomationDataManager automationDataManager;
    private AutomationEngine<ActionSchedule> automationEngine;
    private TestActivityMonitor activityMonitor;


    @Before
    public void setUp() {
        activityMonitor = new TestActivityMonitor();
        activityMonitor.register();

        driver = new TestActionScheduleDriver();
        automationDataManager = new AutomationDataManager(TestApplication.getApplication(), "appKey", "AutomationEngineTest");
        automationEngine = new AutomationEngine.Builder<ActionSchedule>()
                .setAnalytics(UAirship.shared().getAnalytics())
                .setDataManager(automationDataManager)
                .setActivityMonitor(activityMonitor)
                .setDriver(driver)
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
                new CustomEvent.Builder("name")
                        .create()
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
                new CustomEvent.Builder("some name")
                        .setEventValue(2.0)
                        .create()
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
    public void testAsap() throws Exception {
        Trigger trigger = Triggers.newAsapTriggerBuilder().build();
        verifyTrigger(trigger, null);
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
        new CustomEvent.Builder("event")
                .create()
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

        while (mainLooper.getScheduler().areAnyRunnable() || automationLooper.getScheduler().areAnyRunnable()) {
            mainLooper.runToEndOfTasks();
            automationLooper.runToEndOfTasks();
        }
    }

    private void advanceAutomationLooperScheduler(long millis) {
        ShadowLooper automationLooper = Shadows.shadowOf(automationEngine.backgroundThread.getLooper());
        automationLooper.getScheduler().advanceBy(millis, TimeUnit.MILLISECONDS);
    }

    private static class TestActionScheduleDriver extends ActionAutomationDriver {

        Map<String, Callback> callbackMap = new HashMap<>();


        @Override
        public void onExecuteTriggeredSchedule(ActionSchedule schedule, Callback finishCallback) {
            callbackMap.put(schedule.getId(), finishCallback);
        }
    }
}
