package com.urbanairship.iam;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ActionRunRequestFactory;
import com.urbanairship.actions.StubbedActionRunRequest;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.automation.AutomationEngine;
import com.urbanairship.automation.Triggers;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonValue;
import com.urbanairship.reactive.Subject;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.remotedata.RemoteDataPayload;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.util.Collections;
import java.util.concurrent.Executor;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link InAppMessageManager}.
 */
public class InAppMessageManagerTest extends BaseTestCase {

    private InAppMessageManager manager;

    private TestActivityMonitor activityMonitor;
    private InAppMessageDriver mockDriver;
    private AutomationEngine<InAppMessageSchedule> mockEngine;
    private InAppMessageDriver.Callbacks driverCallbacks;
    private Analytics mockAnalytics;
    private RemoteData mockRemoteData;

    private InAppMessageSchedule schedule;

    private InAppMessageAdapter mockAdapter;
    private ShadowLooper mainLooper;
    private ActionRunRequestFactory actionRunRequestFactory;
    private InAppMessageListener mockListener;

    @Before
    public void setup() {
        activityMonitor = new TestActivityMonitor();
        mockDriver = mock(InAppMessageDriver.class);
        mockAdapter = mock(InAppMessageAdapter.class);
        mockAnalytics = mock(Analytics.class);
        mockListener = mock(InAppMessageListener.class);
        mainLooper = Shadows.shadowOf(Looper.getMainLooper());
        actionRunRequestFactory = mock(ActionRunRequestFactory.class);

        ActionRunRequest actionRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(actionRunRequestFactory.createActionRequest("action_name")).thenReturn(actionRunRequest);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                driverCallbacks = invocation.getArgument(0);
                return null;
            }
        }).when(mockDriver).setCallbacks(any(InAppMessageDriver.Callbacks.class));

        mockEngine = mock(AutomationEngine.class);
        mockRemoteData = mock(RemoteData.class);
        Subject<RemoteDataPayload> subject = Subject.create();
        when(mockRemoteData.payloadsForType(any(String.class))).thenReturn(subject);


        manager = new InAppMessageManager(TestApplication.getApplication().preferenceDataStore, mockAnalytics, activityMonitor, new Executor() {
            @Override
            public void execute(@NonNull Runnable runnable) {
                runnable.run();
            }
        }, mockDriver, mockEngine, mockRemoteData, UAirship.shared().getPushManager(), actionRunRequestFactory);


        schedule = new InAppMessageSchedule("schedule id", InAppMessageScheduleInfo.newBuilder()
                                                                                   .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                                                   .setMessage(InAppMessage.newBuilder()
                                                                                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                                                           .setId("message id")
                                                                                                           .addAction("action_name", JsonValue.wrap("action_value"))
                                                                                                           .build())
                                                                                   .build());

        manager.setAdapterFactory(InAppMessage.TYPE_CUSTOM, new InAppMessageAdapter.Factory() {
            @Override
            public InAppMessageAdapter createAdapter(InAppMessage message) {
                return mockAdapter;
            }
        });

        manager.init();
        manager.onAirshipReady(UAirship.shared());
        manager.addListener(mockListener);

        // Finish init on the main thread
        mainLooper.runToEndOfTasks();
    }


    @Test
    public void testIsMessageReady() {
        when(mockAdapter.onPrepare(any(Context.class))).thenReturn(InAppMessageAdapter.OK);

        // First call should prefetch the assets
        assertFalse(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));
        verify(mockAdapter).onPrepare(any(Context.class));

        // Resumed activity is required
        assertFalse(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));

        // Resume an activity
        Activity activity = new Activity();
        activityMonitor.startActivity(activity);
        activityMonitor.resumeActivity(activity);

        // Verify the in-app message is ready to be displayed
        assertTrue(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));
    }

    @Test
    public void testMessageFinished() {
        ActionRunRequest actionRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(actionRunRequestFactory.createActionRequest("action_name")).thenReturn(actionRunRequest);

        // Resume an activity
        Activity activity = new Activity();
        activityMonitor.startActivity(activity);
        activityMonitor.resumeActivity(activity);

        // Prepare the schedule
        when(mockAdapter.onPrepare(any(Context.class))).thenReturn(InAppMessageAdapter.OK);
        assertFalse(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));
        assertTrue(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));

        // Display the schedule
        when(mockAdapter.onDisplay(eq(activity), eq(false), any(DisplayHandler.class))).thenReturn(true);
        driverCallbacks.onDisplay(schedule.getId());
        verify(mockListener).onMessageDisplayed(schedule.getId(), schedule.getInfo().getInAppMessage());

        // Verify schedules are no longer ready to be displayed
        assertFalse(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));

        // Finish displaying the in-app message
        ResolutionInfo resolutionInfo = ResolutionInfo.dismissed(100);
        manager.messageFinished(schedule.getId(), resolutionInfo);
        verify(mockListener).onMessageFinished(schedule.getId(), schedule.getInfo().getInAppMessage(), resolutionInfo);
        verify(mockAnalytics).addEvent(any(ResolutionEvent.class));

        // Verify schedules are still not displayed due to the display interval
        assertFalse(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));
        assertFalse(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));

        // Advance the looper to free up the display lock
        mainLooper.runToEndOfTasks();

        // Verify in-app message is ready to be displayed
        assertTrue(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));

        // Verify the display actions ran
        verify(actionRunRequest).run();
    }

    @Test
    public void testScheduleDataFetched() {
        clearInvocations(mockEngine);
        when(mockAdapter.onPrepare(any(Context.class))).thenReturn(InAppMessageAdapter.OK);
        assertFalse(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));
        verify(mockEngine).checkPendingSchedules();
    }

    @Test
    public void testOnDisplay() {
        // Resume an activity
        Activity activity = new Activity();
        activityMonitor.startActivity(activity);
        activityMonitor.resumeActivity(activity);

        // Prepare the message
        when(mockAdapter.onPrepare(any(Context.class))).thenReturn(InAppMessageAdapter.OK);
        assertFalse(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));
        assertTrue(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));

        // Display it
        when(mockAdapter.onDisplay(any(Activity.class), anyBoolean(), any(DisplayHandler.class))).thenReturn(true);
        driverCallbacks.onDisplay(schedule.getId());

        // Verify a display event was added
        verify(mockAnalytics).addEvent(any(DisplayEvent.class));

        // Verify the adapter onDisplay was called
        verify(mockAdapter).onDisplay(eq(activity), eq(false), any(DisplayHandler.class));
    }

    @Test
    public void testRetryDisplay() {
        // Resume an activity
        Activity activity = new Activity();
        activityMonitor.startActivity(activity);
        activityMonitor.resumeActivity(activity);

        // Prepare the message
        when(mockAdapter.onPrepare(any(Context.class))).thenReturn(InAppMessageAdapter.OK);
        assertFalse(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));
        assertTrue(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));

        // Try to display it
        when(mockAdapter.onDisplay(any(Activity.class), anyBoolean(), any(DisplayHandler.class))).thenReturn(false);
        driverCallbacks.onDisplay(schedule.getId());

        // Verify the adapter onDisplay was called
        verify(mockAdapter).onDisplay(eq(activity), eq(false), any(DisplayHandler.class));

        // Advance the looper
        mainLooper.runToEndOfTasks();

        // Verify the adapter onDisplay was called again
        verify(mockAdapter, times(2)).onDisplay(eq(activity), eq(false), any(DisplayHandler.class));
    }

    @Test
    public void testRedisplay() {
        // Resume an activity
        Activity activity = new Activity();
        activityMonitor.startActivity(activity);
        activityMonitor.resumeActivity(activity);

        // Prepare the message
        when(mockAdapter.onPrepare(any(Context.class))).thenReturn(InAppMessageAdapter.OK);
        assertFalse(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));
        assertTrue(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));

        ActionRunRequest actionRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(actionRunRequestFactory.createActionRequest("action_name")).thenReturn(actionRunRequest);

        // Display it
        when(mockAdapter.onDisplay(any(Activity.class), anyBoolean(), any(DisplayHandler.class))).thenReturn(true);
        driverCallbacks.onDisplay(schedule.getId());

        // Notify the manager to display on next activity
        manager.continueOnNextActivity(schedule.getId());

        // Verify isScheduleReady returns false
        assertFalse(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));

        // Resume a new activity
        activityMonitor.pauseActivity(activity);
        Activity anotherActivity = new Activity();
        activityMonitor.startActivity(anotherActivity);
        activityMonitor.resumeActivity(anotherActivity);

        // Verify the schedule is displayed
        verify(mockAdapter).onDisplay(eq(anotherActivity), eq(true), any(DisplayHandler.class));
    }

    @Test
    public void testActivityStopped() {
        // Resume an activity
        Activity activity = new Activity();
        activityMonitor.startActivity(activity);
        activityMonitor.resumeActivity(activity);

        // Prepare the message
        when(mockAdapter.onPrepare(any(Context.class))).thenReturn(InAppMessageAdapter.OK);
        assertFalse(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));
        assertTrue(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));

        // Display it
        when(mockAdapter.onDisplay(eq(activity), anyBoolean(), any(DisplayHandler.class))).thenReturn(true);
        driverCallbacks.onDisplay(schedule.getId());

        // Stop the activity
        activityMonitor.pauseActivity(activity);
        activityMonitor.stopActivity(activity);

        // Verify schedules are still not displayed due to the display interval
        assertFalse(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));
        assertFalse(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));

        // Advance the looper to free up the display lock
        mainLooper.runToEndOfTasks();

        // Resume another activity
        Activity anotherActivity = new Activity();
        activityMonitor.startActivity(anotherActivity);
        activityMonitor.resumeActivity(anotherActivity);

        // Verify schedules are ready to be displayed
        assertTrue(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));
    }

    @Test
    public void testSchedule() {
        manager.scheduleMessage(schedule.getInfo());
        verify(mockEngine).schedule(schedule.getInfo());
    }

    @Test
    public void testCancelSchedule() {
        manager.cancelSchedule("schedule ID");
        verify(mockEngine).cancel(Collections.singletonList("schedule ID"));
    }

    @Test
    public void testCancelMessage() {
        manager.cancelMessage("message ID");
        verify(mockEngine).cancelGroup("message ID");
    }

    @Test
    public void testRetryPrepareMessage() {
        when(mockAdapter.onPrepare(any(Context.class))).thenReturn(InAppMessageAdapter.RETRY);

        // First check should start downloading the assets
        assertFalse(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));

        // Should call it once, but a runnable should be dispatched on the main thread with a delay to retry
        verify(mockAdapter, times(1)).onPrepare(any(Context.class));

        // Advance the looper
        ShadowLooper mainLooper = Shadows.shadowOf(Looper.getMainLooper());
        mainLooper.runToEndOfTasks();

        // Verify it was called again
        verify(mockAdapter, times(2)).onPrepare(any(Context.class));
    }

    @Test
    public void testCancelPrepare() {
        when(mockAdapter.onPrepare(any(Context.class))).thenReturn(InAppMessageAdapter.CANCEL);

        // First check should start downloading the assets
        assertFalse(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));

        // Should call it once
        verify(mockAdapter, times(1)).onPrepare(any(Context.class));
        verifyNoMoreInteractions(mockAdapter);
        verify(mockEngine).cancel(Collections.singletonList(schedule.getId()));

        // Advance the looper to make sure its not called again
        ShadowLooper mainLooper = Shadows.shadowOf(Looper.getMainLooper());
        mainLooper.runToEndOfTasks();

    }

    @Test
    public void testAudienceConditionsCheck() {
        Activity activity = new Activity();
        activityMonitor.startActivity(activity);
        activityMonitor.resumeActivity(activity);


        // Schedule that requires notification opt-in to be true
        schedule = new InAppMessageSchedule("schedule id", InAppMessageScheduleInfo.newBuilder()
                                                                                   .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                                                   .setMessage(InAppMessage.newBuilder()
                                                                                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                                                           .setId("message id")
                                                                                                           .setAudience(Audience.newBuilder()
                                                                                                                                .setNotificationsOptIn(true)
                                                                                                                                .build())
                                                                                                           .build())
                                                                                   .build());

        assertTrue(driverCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage()));
        driverCallbacks.onDisplay(schedule.getId());

        // Verify the schedule is finished
        verify(mockDriver).displayFinished("schedule id");
        verifyZeroInteractions(mockAdapter);
    }

    @Test
    public void testEnable() {
        clearInvocations(mockEngine);
        manager.setEnabled(true);
        verify(mockEngine).setPaused(false);
    }

    @Test
    public void testDisable() {
        clearInvocations(mockEngine);
        manager.setEnabled(false);
        verify(mockEngine).setPaused(true);
    }
}