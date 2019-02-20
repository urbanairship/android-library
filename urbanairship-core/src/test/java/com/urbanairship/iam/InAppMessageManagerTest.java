package com.urbanairship.iam;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.AirshipLoopers;
import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ActionRunRequestFactory;
import com.urbanairship.actions.StubbedActionRunRequest;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.automation.AutomationDriver;
import com.urbanairship.automation.AutomationEngine;
import com.urbanairship.automation.Triggers;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.iam.tags.TagGroupManager;
import com.urbanairship.iam.tags.TagGroupResult;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.reactive.Subject;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.remotedata.RemoteDataPayload;
import com.urbanairship.util.RetryingExecutor;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static com.urbanairship.iam.tags.TestUtils.tagSet;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link InAppMessageManager}.
 */
public class InAppMessageManagerTest extends BaseTestCase {

    private InAppMessageManager manager;

    private InAppMessageDriver mockDriver;
    private AutomationEngine<InAppMessageSchedule> mockEngine;
    private InAppMessageDriver.Listener driverListener;
    private Analytics mockAnalytics;
    private RemoteData mockRemoteData;

    private InAppMessageSchedule schedule;

    private InAppMessageAdapter mockAdapter;
    private ShadowLooper mainLooper;
    private ActionRunRequestFactory actionRunRequestFactory;
    private InAppMessageListener mockListener;
    private TagGroupManager mockTagManager;
    private DisplayCoordinator mockCoordinator;
    private TestInAppRemoteDataObserver testObserver;

    @Before
    public void setup() {
        mockDriver = mock(InAppMessageDriver.class);
        mockAdapter = mock(InAppMessageAdapter.class);
        mockAnalytics = mock(Analytics.class);
        mockListener = mock(InAppMessageListener.class);
        mainLooper = Shadows.shadowOf(Looper.getMainLooper());
        actionRunRequestFactory = mock(ActionRunRequestFactory.class);
        mockTagManager = mock(TagGroupManager.class);
        mockCoordinator = mock(DisplayCoordinator.class);
        testObserver = new TestInAppRemoteDataObserver(TestApplication.getApplication().preferenceDataStore);

        when(mockCoordinator.isReady()).thenReturn(true);

        ActionRunRequest actionRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(actionRunRequestFactory.createActionRequest("action_name")).thenReturn(actionRunRequest);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                driverListener = invocation.getArgument(0);
                return null;
            }
        }).when(mockDriver).setListener(any(InAppMessageDriver.Listener.class));

        mockEngine = mock(AutomationEngine.class);
        mockRemoteData = mock(RemoteData.class);
        Subject<RemoteDataPayload> subject = Subject.create();
        when(mockRemoteData.payloadsForType(any(String.class))).thenReturn(subject);


        RetryingExecutor executor = new RetryingExecutor(new Handler(Looper.getMainLooper()), new Executor() {
            @Override
            public void execute(@NonNull Runnable runnable) {
                runnable.run();
            }
        });

        manager = new InAppMessageManager(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore, mockAnalytics, new TestActivityMonitor(),
                executor, mockDriver, mockEngine, mockRemoteData, UAirship.shared().getPushManager(), actionRunRequestFactory, mockTagManager, testObserver);

        InAppMessageScheduleInfo info = InAppMessageScheduleInfo.newBuilder()
                                                                .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                                .setMessage(InAppMessage.newBuilder()
                                                                                        .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                                        .setId("message id")
                                                                                        .addAction("action_name", JsonValue.wrap("action_value"))
                                                                                        .build())
                                                                .build();
        schedule = new InAppMessageSchedule("schedule id", JsonMap.EMPTY_MAP, info);

        manager.setAdapterFactory(InAppMessage.TYPE_CUSTOM, new InAppMessageAdapter.Factory() {
            @NonNull
            @Override
            public InAppMessageAdapter createAdapter(@NonNull InAppMessage message) {
                return mockAdapter;
            }
        });

        manager.init();
        manager.setOnRequestDisplayCoordinatorCallback(new OnRequestDisplayCoordinatorCallback() {
            @Nullable
            @Override
            public DisplayCoordinator onRequestDisplayCoordinator(@NonNull InAppMessage message) {
                return mockCoordinator;
            }
        });
        manager.onAirshipReady(UAirship.shared());
        manager.addListener(mockListener);

        // Finish init on the main thread
        mainLooper.runToEndOfTasks();
    }

    @Test
    public void testIsScheduleReady() {
        when(mockAdapter.onPrepare(any(Context.class))).thenReturn(InAppMessageAdapter.OK);
        when(mockAdapter.isReady(any(Context.class))).thenReturn(true);

        // Prepare the schedule
        driverListener.onPrepareSchedule(schedule);
        verify(mockAdapter).onPrepare(any(Context.class));
        verify(mockDriver).schedulePrepared(schedule.getId(), AutomationDriver.PREPARE_RESULT_CONTINUE);

        // Verify the schedule is ready
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, driverListener.onCheckExecutionReadiness(schedule));
    }

    @Test
    public void testDisplayAdapterNotReady() {
        when(mockAdapter.onPrepare(any(Context.class))).thenReturn(InAppMessageAdapter.OK);
        when(mockAdapter.isReady(any(Context.class))).thenReturn(false);

        // Prepare the schedule
        driverListener.onPrepareSchedule(schedule);
        verify(mockAdapter).onPrepare(any(Context.class));
        verify(mockDriver).schedulePrepared(schedule.getId(), AutomationDriver.PREPARE_RESULT_CONTINUE);

        // Verify the schedule is not ready
        assertEquals(AutomationDriver.READY_RESULT_NOT_READY, driverListener.onCheckExecutionReadiness(schedule));
    }

    @Test
    public void testCoordinatorNotReady() {
        final DisplayCoordinator mockDisplayCoordinator = mock(DisplayCoordinator.class);
        when(mockDisplayCoordinator.isReady()).thenReturn(false);
        manager.setOnRequestDisplayCoordinatorCallback(new OnRequestDisplayCoordinatorCallback() {
            @Nullable
            @Override
            public DisplayCoordinator onRequestDisplayCoordinator(@NonNull InAppMessage message) {
                return mockDisplayCoordinator;
            }
        });

        when(mockAdapter.onPrepare(any(Context.class))).thenReturn(InAppMessageAdapter.OK);
        when(mockAdapter.isReady(any(Activity.class))).thenReturn(true);

        // Prepare the schedule
        driverListener.onPrepareSchedule(schedule);

        assertEquals(AutomationDriver.READY_RESULT_NOT_READY, driverListener.onCheckExecutionReadiness(schedule));
    }

    @Test
    public void testIsPaused() {
        // Pause display
        manager.setPaused(true);

        // Prepare the schedule
        when(mockAdapter.onPrepare(any(Context.class))).thenReturn(InAppMessageAdapter.OK);
        assertEquals(AutomationDriver.READY_RESULT_NOT_READY, driverListener.onCheckExecutionReadiness(schedule));

        // Paused = message is unable to be ready
        assertEquals(AutomationDriver.READY_RESULT_NOT_READY, driverListener.onCheckExecutionReadiness(schedule));
    }

    @Test
    public void testMessageFinished() {
        ActionRunRequest actionRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(actionRunRequestFactory.createActionRequest("action_name")).thenReturn(actionRunRequest);

        // Prepare the schedule
        when(mockAdapter.onPrepare(any(Context.class))).thenReturn(InAppMessageAdapter.OK);
        driverListener.onPrepareSchedule(schedule);

        // Make sure it's ready
        when(mockAdapter.isReady(any(Context.class))).thenReturn(true);
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, driverListener.onCheckExecutionReadiness(schedule));

        // Display the schedule
        driverListener.onExecuteSchedule(schedule);
        verify(mockListener).onMessageDisplayed(schedule.getId(), schedule.getInfo().getInAppMessage());

        // Finish displaying the in-app message
        ResolutionInfo resolutionInfo = ResolutionInfo.dismissed();
        manager.messageFinished(schedule.getId(), resolutionInfo, 100);
        verify(mockListener).onMessageFinished(schedule.getId(), schedule.getInfo().getInAppMessage(), resolutionInfo);
        verify(mockAnalytics).addEvent(any(ResolutionEvent.class));
        verify(mockCoordinator).onDisplayFinished(schedule.getInfo().getInAppMessage());
        verify(mockAdapter).onFinish(any(Context.class));

        // Verify the display actions ran
        verify(actionRunRequest).run();
    }

    @Test
    public void testOnExecuteSchedule() {
        // Prepare the schedule
        when(mockAdapter.onPrepare(any(Context.class))).thenReturn(InAppMessageAdapter.OK);
        driverListener.onPrepareSchedule(schedule);

        // Make sure it's ready
        when(mockAdapter.isReady(any(Context.class))).thenReturn(true);
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, driverListener.onCheckExecutionReadiness(schedule));

        // Execute the schedule
        driverListener.onExecuteSchedule(schedule);

        // Verify a display event was added
        verify(mockAnalytics).addEvent(any(DisplayEvent.class));

        // Verify the adapter onDisplay was called
        verify(mockAdapter).onDisplay(any(Context.class), any(DisplayHandler.class));
    }

    @Test
    public void testDisplayException() {
        // Prepare the schedule
        when(mockAdapter.onPrepare(any(Context.class))).thenReturn(InAppMessageAdapter.OK);
        driverListener.onPrepareSchedule(schedule);

        // Make sure it's ready
        when(mockAdapter.isReady(any(Context.class))).thenReturn(true);
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, driverListener.onCheckExecutionReadiness(schedule));

        // Throw an exception when displaying
        doThrow(new RuntimeException("COOL"))
                .when(mockAdapter)
                .onDisplay(any(Context.class), any(DisplayHandler.class));

        driverListener.onExecuteSchedule(schedule);

        // Verify the adapter onDisplay was called
        verify(mockAdapter).onDisplay(any(Context.class), any(DisplayHandler.class));
        verify(mockAdapter).onFinish(any(Context.class));

        // Verify the coordinator was not notified
        verify(mockCoordinator, never()).onDisplayStarted(eq(schedule.getInfo().getInAppMessage()));

        // Verify the schedule was finished
        verify(mockDriver).scheduleExecuted(schedule.getId());
    }

    @Test
    public void testSchedule() {
        manager.scheduleMessage(schedule.getInfo());
        verify(mockEngine).schedule(schedule.getInfo(), JsonMap.EMPTY_MAP);
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

        // Prepare the adapter
        driverListener.onPrepareSchedule(schedule);

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

        // Start preparing
        driverListener.onPrepareSchedule(schedule);

        // Should call it once
        verify(mockAdapter, times(1)).onPrepare(any(Context.class));
        verifyNoMoreInteractions(mockAdapter);

        // Return cancel result
        verify(mockDriver).schedulePrepared(schedule.getId(), AutomationDriver.PREPARE_RESULT_CANCEL);

        // Advance the looper to make sure its not called again
        ShadowLooper mainLooper = Shadows.shadowOf(Looper.getMainLooper());
        mainLooper.runToEndOfTasks();
    }

    @Test
    public void testAudienceConditionsCheckDefaultMissBehavior() {
        InAppMessageScheduleInfo info = InAppMessageScheduleInfo.newBuilder()
                                                                .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                                .setMessage(InAppMessage.newBuilder()
                                                                                        .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                                        .setId("message id")
                                                                                        .setAudience(Audience.newBuilder()
                                                                                                             .setNotificationsOptIn(true)
                                                                                                             .build())
                                                                                        .build())
                                                                .build();

        // Schedule that requires notification opt-in to be true
        schedule = new InAppMessageSchedule("schedule id", JsonMap.EMPTY_MAP, info);

        // Prepare the schedule
        driverListener.onPrepareSchedule(schedule);

        verify(mockDriver).schedulePrepared(schedule.getId(), AutomationDriver.PREPARE_RESULT_PENALIZE);
    }

    @Test
    public void testAudienceConditionsCheckMissBehaviorCancel() {
        InAppMessageScheduleInfo info = InAppMessageScheduleInfo.newBuilder()
                                                                .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                                .setMessage(InAppMessage.newBuilder()
                                                                                        .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                                        .setId("message id")
                                                                                        .setAudience(Audience.newBuilder()
                                                                                                             .setNotificationsOptIn(true)
                                                                                                             .setMissBehavior("cancel")
                                                                                                             .build())
                                                                                        .build())
                                                                .build();

        // Schedule that requires notification opt-in to be true
        schedule = new InAppMessageSchedule("schedule id", JsonMap.EMPTY_MAP, info);

        // Prepare the schedule
        driverListener.onPrepareSchedule(schedule);

        verify(mockDriver).schedulePrepared(schedule.getId(), AutomationDriver.PREPARE_RESULT_CANCEL);
    }

    @Test
    public void testAudienceConditionsCheckMissBehaviorSkip() {
        InAppMessageScheduleInfo info = InAppMessageScheduleInfo.newBuilder()
                                                                .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                                .setMessage(InAppMessage.newBuilder()
                                                                                        .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                                        .setId("message id")
                                                                                        .setAudience(Audience.newBuilder()
                                                                                                             .setNotificationsOptIn(true)
                                                                                                             .setMissBehavior("skip")
                                                                                                             .build())
                                                                                        .build())
                                                                .build();

        // Schedule that requires notification opt-in to be true
        schedule = new InAppMessageSchedule("schedule id", JsonMap.EMPTY_MAP, info);

        // Prepare the schedule
        driverListener.onPrepareSchedule(schedule);

        verify(mockDriver).schedulePrepared(schedule.getId(), AutomationDriver.PREPARE_RESULT_SKIP);
    }

    @Test
    public void testAudienceConditionsCheckMissBehaviorPenalize() {
        InAppMessageScheduleInfo info = InAppMessageScheduleInfo.newBuilder()
                                                                .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                                .setMessage(InAppMessage.newBuilder()
                                                                                        .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                                        .setId("message id")
                                                                                        .setAudience(Audience.newBuilder()
                                                                                                             .setNotificationsOptIn(true)
                                                                                                             .setMissBehavior(Audience.MISS_BEHAVIOR_PENALIZE)
                                                                                                             .build())
                                                                                        .build())
                                                                .build();

        // Schedule that requires notification opt-in to be true
        schedule = new InAppMessageSchedule("schedule id", JsonMap.EMPTY_MAP, info);

        // Prepare the schedule
        driverListener.onPrepareSchedule(schedule);

        verify(mockDriver).schedulePrepared(schedule.getId(), AutomationDriver.PREPARE_RESULT_PENALIZE);
    }

    @Test
    public void testAudienceConditionCheckWithTagGroups() {
        when(mockAdapter.onPrepare(any(Context.class))).thenReturn(InAppMessageAdapter.OK);

        Map<String, Set<String>> tagGroups = new HashMap<>();
        tagGroups.put("expected group", tagSet("expected tag"));

        Audience audience = Audience.newBuilder()
                                    .setTagSelector(TagSelector.tag("expected tag", "expected group"))
                                    .build();


        InAppMessageScheduleInfo info = InAppMessageScheduleInfo.newBuilder()
                                                                .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                                .setMessage(InAppMessage.newBuilder()
                                                                                        .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                                        .setId("message id")
                                                                                        .setAudience(audience)
                                                                                        .build())
                                                                .build();

        schedule = new InAppMessageSchedule("schedule id", JsonMap.EMPTY_MAP, info);


        when(mockTagManager.getTags(tagGroups)).thenReturn(new TagGroupResult(true, tagGroups));

        // Prepare the schedule
        driverListener.onPrepareSchedule(schedule);

        // Verify its prepared
        verify(mockDriver).schedulePrepared(schedule.getId(), AutomationDriver.PREPARE_RESULT_CONTINUE);
    }

    @Test
    public void testMessageExtending() {
        manager.setMessageExtender(new InAppMessageExtender() {
            @NonNull
            @Override
            public InAppMessage extend(@NonNull InAppMessage message) {
                return InAppMessage.newBuilder(message).setId("some other id").build();
            }
        });

        InAppMessageAdapter.Factory factory = mock(InAppMessageAdapter.Factory.class);
        manager.setAdapterFactory(schedule.getInfo().getInAppMessage().getType(), factory);

        // Prepare the message
        when(mockAdapter.onPrepare(any(Context.class))).thenReturn(InAppMessageAdapter.OK);
        driverListener.onPrepareSchedule(schedule);

        verify(factory).createAdapter(argThat(new ArgumentMatcher<InAppMessage>() {
            @Override
            public boolean matches(InAppMessage argument) {
                return argument.getId().equals("some other id");
            }
        }));
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

    @Test
    public void testNewConfig() {
        JsonList config = new JsonList(Arrays.asList(
                JsonMap.newBuilder()
                       .put("tag_groups", JsonMap.newBuilder()
                                                 .put("enabled", true)
                                                 .put("cache_max_age_seconds", 100)
                                                 .put("cache_stale_read_age_seconds", 11)
                                                 .put("cache_prefer_local_until_seconds", 1)
                                                 .build())
                       .build().toJsonValue(),
                JsonMap.newBuilder()
                       .put("tag_groups", JsonMap.newBuilder()
                                                 .put("enabled", true)
                                                 .put("cache_max_age_seconds", 1)
                                                 .put("cache_stale_read_age_seconds", 11)
                                                 .put("cache_prefer_local_until_seconds", 200)
                                                 .build())
                       .build().toJsonValue()));


        manager.onNewConfig(config);

        verify(mockTagManager).setEnabled(true);
        verify(mockTagManager).setCacheMaxAgeTime(100, TimeUnit.SECONDS);
        verify(mockTagManager).setCacheStaleReadTime(11, TimeUnit.SECONDS);
        verify(mockTagManager).setPreferLocalTagDataTime(200, TimeUnit.SECONDS);
    }

    @Test
    public void testInvalidScheduleOnExecution() {
        JsonMap metadata = JsonMap.newBuilder()
                                  .putOpt("cool", "story")
                                  .build();

        testObserver.setLastPayloadMetadata(metadata);
        when(mockRemoteData.getLastMetadata()).thenReturn(JsonMap.EMPTY_MAP);
        when(mockRemoteData.isLastMetadataCurrent()).thenReturn(true);

        InAppMessageScheduleInfo info = InAppMessageScheduleInfo.newBuilder()
                                                                .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                                .setMessage(InAppMessage.newBuilder()
                                                                                        .setSource(InAppMessage.SOURCE_REMOTE_DATA)
                                                                                        .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                                        .setId("message id")
                                                                                        .addAction("action_name", JsonValue.wrap("action_value"))
                                                                                        .build())
                                                                .build();

        InAppMessageSchedule schedule = new InAppMessageSchedule("Some-ID", JsonMap.EMPTY_MAP, info);

        // Prepare the schedule
        driverListener.onPrepareSchedule(schedule);

        // Verify the schedule is prepared
        verify(mockDriver).schedulePrepared(schedule.getId(), AutomationDriver.PREPARE_RESULT_CONTINUE);

        // Change the metadata
        when(mockRemoteData.getLastMetadata()).thenReturn(metadata);

        // Verify it returns an invalidate result
        assertEquals(AutomationDriver.READY_RESULT_INVALIDATE, driverListener.onCheckExecutionReadiness(schedule));
    }

    @Test
    public void testInvalidScheduleOnPrepare() {
        JsonMap metadata = JsonMap.newBuilder()
                                  .putOpt("cool", "story")
                                  .build();

        testObserver.setLastPayloadMetadata(metadata);
        when(mockRemoteData.getLastMetadata()).thenReturn(metadata);

        InAppMessageScheduleInfo info = InAppMessageScheduleInfo.newBuilder()
                                                                .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                                .setMessage(InAppMessage.newBuilder()
                                                                                        .setSource(InAppMessage.SOURCE_REMOTE_DATA)
                                                                                        .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                                        .setId("message id")
                                                                                        .addAction("action_name", JsonValue.wrap("action_value"))
                                                                                        .build())
                                                                .build();

        InAppMessageSchedule schedule = new InAppMessageSchedule("Some-ID", JsonMap.EMPTY_MAP, info);

        // Prepare the schedule
        driverListener.onPrepareSchedule(schedule);
        runLooperTasks();

        // Verify the schedule is invalidated
        verify(mockDriver).schedulePrepared(schedule.getId(), AutomationDriver.PREPARE_RESULT_INVALIDATE);
    }

    /**
     * Helper method to run all the looper tasks.
     */
    private void runLooperTasks() {
        ShadowLooper mainLooper = Shadows.shadowOf(Looper.getMainLooper());
        ShadowLooper backgroundLooper = Shadows.shadowOf(AirshipLoopers.getBackgroundLooper());

        do {
            mainLooper.runToEndOfTasks();
            backgroundLooper.runToEndOfTasks();
        }
        while (mainLooper.getScheduler().areAnyRunnable() || backgroundLooper.getScheduler().areAnyRunnable());
    }

    public static class TestInAppRemoteDataObserver extends InAppRemoteDataObserver {

        private JsonMap metadata;

        /**
         * Default constructor.
         *
         * @param preferenceDataStore The preference data store.
         */
        TestInAppRemoteDataObserver(@NonNull PreferenceDataStore preferenceDataStore) {
            super(preferenceDataStore);
        }

        void setLastPayloadMetadata(JsonMap metadata) {
            this.metadata = metadata;
        }

        @Override
        public JsonMap getLastPayloadMetadata() {
            return metadata;
        }
    }
}