package com.urbanairship.iam;

import android.app.Activity;
import android.os.Bundle;
import android.os.Looper;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.automation.AutomationEngine;
import com.urbanairship.automation.Triggers;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.util.Collections;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link InAppMessageManager}.
 */
public class InAppMessageManagerTest extends BaseTestCase {

    private InAppMessageManager manager;

    private TestActivityMonitor activityMonitor;
    private InAppMessageDriver mockDriver;
    private AutomationEngine mockEngine;
    private InAppMessageDriver.Callbacks driverCallbacks;

    private InAppMessageSchedule schedule;
    private InAppMessageSchedule anotherSchedule;

    private InAppMessageAdapter mockAdapter;
    private ShadowLooper mainLooper;

    @Before
    public void setup() {
        activityMonitor = new TestActivityMonitor();
        mockDriver = mock(InAppMessageDriver.class);
        mockAdapter = mock(InAppMessageAdapter.class);
        mainLooper = Shadows.shadowOf(Looper.getMainLooper());

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                driverCallbacks = invocation.getArgument(0);
                return null;
            }
        }).when(mockDriver).setCallbacks(any(InAppMessageDriver.Callbacks.class));

        mockEngine = mock(AutomationEngine.class);

        manager = new InAppMessageManager(activityMonitor, mockDriver, mockEngine);


        schedule = new InAppMessageSchedule("schedule id", InAppMessageScheduleInfo.newBuilder()
                                                                                   .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                                                   .setMessage(InAppMessage.newBuilder()
                                                                                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                                                           .setId("message id")
                                                                                                           .build())
                                                                                   .build());

        anotherSchedule = new InAppMessageSchedule("another schedule id", InAppMessageScheduleInfo.newBuilder()
                                                                                                  .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                                                                  .setMessage(InAppMessage.newBuilder()
                                                                                                                          .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                                                                          .setId("another message id")
                                                                                                                          .build())
                                                                                                  .build());

    }

    @Test
    public void testIsScheduleReady() {
        manager.init();

        // Resumed activity is required
        assertFalse(driverCallbacks.isScheduleReadyToDisplay(schedule));

        // Resume an activity
        Activity activity = new Activity();
        activityMonitor.startActivity(activity);
        activityMonitor.resumeActivity(activity);

        // Verify schedules are ready to be displayed
        assertTrue(driverCallbacks.isScheduleReadyToDisplay(schedule));
        assertTrue(driverCallbacks.isScheduleReadyToDisplay(anotherSchedule));
    }

    @Test
    public void testMessageFinished() {
        manager.init();

        // Resume an activity
        Activity activity = new Activity();
        activityMonitor.startActivity(activity);
        activityMonitor.resumeActivity(activity);

        // Display the schedule
        when(mockAdapter.display(eq(activity), any(DisplayArguments.class))).thenReturn(InAppMessageAdapter.OK);
        driverCallbacks.onDisplay(schedule, mockAdapter, new Bundle());

        // Verify schedules are no longer ready to be displayed
        assertFalse(driverCallbacks.isScheduleReadyToDisplay(schedule));

        // Finish displaying the in-app message
        manager.messageFinished(schedule.getId());

        // Verify schedules are still not displayed due to the display interval
        assertFalse(driverCallbacks.isScheduleReadyToDisplay(schedule));

        // Advance the looper to free up the display lock
        mainLooper.runToEndOfTasks();

        // Verify schedules are ready to be displayed
        assertTrue(driverCallbacks.isScheduleReadyToDisplay(schedule));
        assertTrue(driverCallbacks.isScheduleReadyToDisplay(anotherSchedule));

        // Display it again
        driverCallbacks.onDisplay(schedule, mockAdapter, new Bundle());
    }

    @Test
    public void testScheduleDataFetched() {
        manager.init();
        driverCallbacks.onScheduleDataFetched(schedule);
        verify(mockEngine).checkPendingSchedules();
    }

    @Test
    public void testOnDisplay() {
        manager.init();

        // Resume an activity
        Activity activity = new Activity();
        activityMonitor.startActivity(activity);
        activityMonitor.resumeActivity(activity);

        final Bundle bundle = new Bundle();
        bundle.putString("key", "value");

        // Display it
        driverCallbacks.onDisplay(schedule, mockAdapter, new Bundle());

        verify(mockAdapter).display(eq(activity), argThat(new ArgumentMatcher<DisplayArguments>() {
            @Override
            public boolean matches(DisplayArguments argument) {
                if (!argument.getMessage().getId().equals("message id")) {
                    return false;
                }

                if (!bundle.getString("key").equals("value")) {
                    return false;
                }

                if (argument.isRedisplay()) {
                    return false;
                }

                return true;
            }
        }));
    }

    @Test
    public void testRedisplay() {
        manager.init();

        // Resume an activity
        Activity activity = new Activity();
        activityMonitor.startActivity(activity);
        activityMonitor.resumeActivity(activity);

        // Display it
        when(mockAdapter.display(eq(activity), any(DisplayArguments.class))).thenReturn(InAppMessageAdapter.OK);
        driverCallbacks.onDisplay(schedule, mockAdapter, new Bundle());

        // Notify the manager to display on next activity
        manager.continueOnNextActivity(schedule.getId());

        // Verify isScheduleReady returns false
        assertFalse(driverCallbacks.isScheduleReadyToDisplay(schedule));

        // Resume a new activity
        activityMonitor.pauseActivity(activity);
        Activity anotherActivity = new Activity();
        activityMonitor.startActivity(anotherActivity);
        activityMonitor.resumeActivity(anotherActivity);

        // Verify the schedule is displayed
        verify(mockAdapter).display(eq(anotherActivity), argThat(new ArgumentMatcher<DisplayArguments>() {
            @Override
            public boolean matches(DisplayArguments argument) {
                if (!argument.getMessage().getId().equals("message id")) {
                    return false;
                }

                if (!argument.isRedisplay()) {
                    return false;
                }

                return true;
            }
        }));
    }

    @Test
    public void testActivityStopped() {
        manager.init();

        // Resume an activity
        Activity activity = new Activity();
        activityMonitor.startActivity(activity);
        activityMonitor.resumeActivity(activity);

        // Display it
        when(mockAdapter.display(eq(activity), any(DisplayArguments.class))).thenReturn(InAppMessageAdapter.OK);
        driverCallbacks.onDisplay(schedule, mockAdapter, new Bundle());

        // Stop the activity
        activityMonitor.pauseActivity(activity);
        activityMonitor.stopActivity(activity);

        // Verify schedules are still not displayed due to the display interval
        assertFalse(driverCallbacks.isScheduleReadyToDisplay(anotherSchedule));

        // Advance the looper to free up the display lock
        mainLooper.runToEndOfTasks();

        // Resume another activity
        Activity anotherActivity = new Activity();
        activityMonitor.startActivity(anotherActivity);
        activityMonitor.resumeActivity(anotherActivity);

        // Verify schedules are ready to be displayed
        assertTrue(driverCallbacks.isScheduleReadyToDisplay(anotherSchedule));
    }

    @Test
    public void testSchedule() {
        manager.init();

        manager.scheduleMessage(schedule.getInfo());
        verify(mockEngine).schedule(schedule.getInfo());
    }

    @Test
    public void testCancelSchedule() {
        manager.init();

        manager.cancelSchedule("schedule ID");
        verify(mockEngine).cancel(Collections.singletonList("schedule ID"));
    }

    @Test
    public void testCancelMessage() {
        manager.init();

        manager.cancelMessage("message ID");
        verify(mockEngine).cancelGroup("message ID");
    }
}