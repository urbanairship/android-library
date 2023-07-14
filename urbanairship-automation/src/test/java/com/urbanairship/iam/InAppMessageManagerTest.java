package com.urbanairship.iam;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.urbanairship.ShadowAirshipExecutorsLegacy;
import com.urbanairship.StubbedActionRunRequest;
import com.urbanairship.TestApplication;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ActionRunRequestFactory;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.automation.AutomationDriver;
import com.urbanairship.experiment.ExperimentResult;
import com.urbanairship.iam.assets.AssetManager;
import com.urbanairship.iam.assets.Assets;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.iam.events.InAppReportingEvent;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.RetryingExecutor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.urbanairship.iam.events.InAppReportingEvent.TYPE_RESOLUTION;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link InAppMessageManager}.
 */
@Config(
        sdk = 28,
        shadows = { ShadowAirshipExecutorsLegacy.class },
        application = TestApplication.class
)
@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(AndroidJUnit4.class)
public class InAppMessageManagerTest {

    private InAppMessageManager manager;
    private Analytics mockAnalytics;
    private InAppMessageAdapter mockAdapter;
    private ActionRunRequestFactory actionRunRequestFactory;
    private DisplayCoordinator mockCoordinator;
    private AssetManager mockAssetManager;
    private InAppMessageManager.Delegate mockDelegate;
    private InAppMessageListener mockListener;

    private InAppMessage message;
    private String scheduleId;

    @Before
    public void setup() {
        message = InAppMessage.newBuilder()
                              .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                              .setSource(InAppMessage.SOURCE_REMOTE_DATA)
                              .build();

        scheduleId = UUID.randomUUID().toString();

        mockAssetManager = mock(AssetManager.class);
        Assets assets = mock(Assets.class);
        when(mockAssetManager.getAssets(anyString())).thenReturn(assets);
        mockAdapter = mock(InAppMessageAdapter.class);
        mockAnalytics = mock(Analytics.class);
        actionRunRequestFactory = mock(ActionRunRequestFactory.class);
        mockCoordinator = mock(DisplayCoordinator.class);
        when(mockCoordinator.isReady()).thenReturn(true);

        mockListener = mock(InAppMessageListener.class);
        mockDelegate = mock(InAppMessageManager.Delegate.class);

        ActionRunRequest actionRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(actionRunRequestFactory.createActionRequest("action_name")).thenReturn(actionRunRequest);

        RetryingExecutor executor = new RetryingExecutor(new Handler(Looper.getMainLooper()), new Executor() {
            @Override
            public void execute(@NonNull Runnable runnable) {
                runnable.run();
            }
        });

        manager = new InAppMessageManager(TestApplication.getApplication(),
                TestApplication.getApplication().preferenceDataStore, mockAnalytics,
                executor, actionRunRequestFactory, mockAssetManager, mockDelegate);

        manager.setAdapterFactory(InAppMessage.TYPE_CUSTOM, new InAppMessageAdapter.Factory() {
            @NonNull
            @Override
            public InAppMessageAdapter createAdapter(@NonNull InAppMessage message) {
                return mockAdapter;
            }
        });

        manager.setOnRequestDisplayCoordinatorCallback(new OnRequestDisplayCoordinatorCallback() {
            @Nullable
            @Override
            public DisplayCoordinator onRequestDisplayCoordinator(@NonNull InAppMessage message) {
                return mockCoordinator;
            }
        });
        manager.onAirshipReady();
        manager.addListener(mockListener);
    }

    @Test
    public void testOnCheckExecutionReadiness() {
        when(mockAssetManager.onPrepare(scheduleId, message)).thenReturn(AssetManager.PREPARE_RESULT_OK);
        when(mockAdapter.onPrepare(any(Context.class), any(Assets.class))).thenReturn(InAppMessageAdapter.OK);
        when(mockAdapter.isReady(any(Context.class))).thenReturn(true);

        // Prepare the schedule
        AutomationDriver.PrepareScheduleCallback mockCallback = mock(AutomationDriver.PrepareScheduleCallback.class);
        manager.onPrepare(scheduleId, null, null, message, null, mockCallback);
        verify(mockAdapter).onPrepare(any(Context.class), any(Assets.class));
        verify(mockCallback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        // Verify the schedule is ready
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, manager.onCheckExecutionReadiness(scheduleId));
    }

    @Test
    public void testDisplayAdapterNotReady() {
        when(mockAssetManager.onPrepare(scheduleId, message)).thenReturn(AssetManager.PREPARE_RESULT_OK);
        when(mockAdapter.onPrepare(any(Context.class), any(Assets.class))).thenReturn(InAppMessageAdapter.OK);
        when(mockAdapter.isReady(any(Context.class))).thenReturn(false);

        // Prepare the schedule
        AutomationDriver.PrepareScheduleCallback mockCallback = mock(AutomationDriver.PrepareScheduleCallback.class);
        manager.onPrepare(scheduleId, null, null, message, null, mockCallback);
        verify(mockAdapter).onPrepare(any(Context.class), any(Assets.class));
        verify(mockCallback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        // Verify the schedule is not ready
        assertEquals(AutomationDriver.READY_RESULT_NOT_READY, manager.onCheckExecutionReadiness(scheduleId));
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

        when(mockAssetManager.onPrepare(scheduleId, message)).thenReturn(AssetManager.PREPARE_RESULT_OK);
        when(mockAdapter.onPrepare(any(Context.class), any(Assets.class))).thenReturn(InAppMessageAdapter.OK);
        when(mockAdapter.isReady(any(Activity.class))).thenReturn(true);

        // Prepare the schedule
        AutomationDriver.PrepareScheduleCallback mockCallback = mock(AutomationDriver.PrepareScheduleCallback.class);
        manager.onPrepare(scheduleId, null, null, message, null, mockCallback);
        verify(mockAdapter).onPrepare(any(Context.class), any(Assets.class));
        verify(mockCallback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        assertEquals(AutomationDriver.READY_RESULT_NOT_READY, manager.onCheckExecutionReadiness(scheduleId));
    }

    @Test
    public void testExecute() {
        when(mockAssetManager.onPrepare(scheduleId, message)).thenReturn(AssetManager.PREPARE_RESULT_OK);
        when(mockAdapter.isReady(any(Context.class))).thenReturn(true);
        when(mockAdapter.onPrepare(any(Context.class), any(Assets.class))).thenReturn(InAppMessageAdapter.OK);

        ActionRunRequest actionRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(actionRunRequestFactory.createActionRequest("action-name")).thenReturn(actionRunRequest);

        InAppMessage extended = InAppMessage.newBuilder(message)
                                            .addAction("action-name", JsonValue.wrapOpt("some-value"))
                                            .build();

        // Prepare the schedule
        AutomationDriver.PrepareScheduleCallback mockPrepareCallback = mock(AutomationDriver.PrepareScheduleCallback.class);
        manager.onPrepare(scheduleId, null, null, extended, null, mockPrepareCallback);
        verify(mockAdapter).onPrepare(any(Context.class), any(Assets.class));
        verify(mockPrepareCallback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        // Make sure it's ready
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, manager.onCheckExecutionReadiness(scheduleId));

        // Display the message
        AutomationDriver.ExecutionCallback mockExecuteCallback = mock(AutomationDriver.ExecutionCallback.class);
        manager.onExecute(scheduleId, mockExecuteCallback);
        verify(mockListener).onMessageDisplayed(scheduleId, extended);

        // Finish displaying the message
        ResolutionInfo resolutionInfo = ResolutionInfo.dismissed();
        manager.onResolution(scheduleId, resolutionInfo, 100);
        manager.onDisplayFinished(scheduleId, resolutionInfo);
        verify(mockListener).onMessageFinished(scheduleId, extended, resolutionInfo);
        verify(mockAnalytics).addEvent(argThat(EventMatchers.isResolution()));
        verify(mockCoordinator).onDisplayFinished(extended);
        verify(mockAdapter).onFinish(any(Context.class));
        verify(mockAssetManager).onDisplayFinished(scheduleId, extended);
        verify(mockExecuteCallback).onFinish();

        // Verify the display actions ran
        verify(actionRunRequest).run();
    }

    @Test
    public void testDisplayDelegate() {
        when(mockAssetManager.onPrepare(scheduleId, message)).thenReturn(AssetManager.PREPARE_RESULT_OK);
        when(mockAdapter.isReady(any(Context.class))).thenReturn(true);
        when(mockAdapter.onPrepare(any(Context.class), any(Assets.class))).thenReturn(InAppMessageAdapter.OK);

        // Prepare the schedule
        AutomationDriver.PrepareScheduleCallback mockPrepareCallback = mock(AutomationDriver.PrepareScheduleCallback.class);
        manager.onPrepare(scheduleId, null, null, message, null, mockPrepareCallback);
        verify(mockAdapter).onPrepare(any(Context.class), any(Assets.class));
        verify(mockPrepareCallback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        manager.setDisplayDelegate(message -> false);
        assertEquals(AutomationDriver.READY_RESULT_NOT_READY, manager.onCheckExecutionReadiness(scheduleId));

        manager.setDisplayDelegate(message -> true);
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, manager.onCheckExecutionReadiness(scheduleId));
    }

    @Test
    public void testNotifyDisplayConditionsChanged() {
        manager.notifyDisplayConditionsChanged();
        verify(mockDelegate).onReadinessChanged();
    }

    @Test
    public void testMessageFinishedReportingDisabled() {
        when(mockAssetManager.onPrepare(scheduleId, message)).thenReturn(AssetManager.PREPARE_RESULT_OK);
        when(mockAdapter.isReady(any(Context.class))).thenReturn(true);
        when(mockAdapter.onPrepare(any(Context.class), any(Assets.class))).thenReturn(InAppMessageAdapter.OK);

        InAppMessage extended = InAppMessage.newBuilder(message)
                                            .setReportingEnabled(false)
                                            .build();

        // Prepare the schedule
        AutomationDriver.PrepareScheduleCallback mockPrepareCallback = mock(AutomationDriver.PrepareScheduleCallback.class);
        manager.onPrepare(scheduleId, null, null, extended, null, mockPrepareCallback);
        verify(mockAdapter).onPrepare(any(Context.class), any(Assets.class));
        verify(mockPrepareCallback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        // Make sure it's ready
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, manager.onCheckExecutionReadiness(scheduleId));

        // Display the schedule
        AutomationDriver.ExecutionCallback mockExecuteCallback = mock(AutomationDriver.ExecutionCallback.class);
        manager.onExecute(scheduleId, mockExecuteCallback);

        // Finish displaying the in-app message
        ResolutionInfo resolutionInfo = ResolutionInfo.dismissed();
        manager.onResolution(scheduleId, resolutionInfo, 100);
        manager.onDisplayFinished(scheduleId, resolutionInfo);
        verify(mockExecuteCallback).onFinish();

        verifyNoInteractions(mockAnalytics);
    }

    @Test
    public void testDisplayException() {
        when(mockAssetManager.onPrepare(scheduleId, message)).thenReturn(AssetManager.PREPARE_RESULT_OK);
        when(mockAdapter.isReady(any(Context.class))).thenReturn(true);
        when(mockAdapter.onPrepare(any(Context.class), any(Assets.class))).thenReturn(InAppMessageAdapter.OK);

        // Prepare the schedule
        AutomationDriver.PrepareScheduleCallback mockPrepareCallback = mock(AutomationDriver.PrepareScheduleCallback.class);
        manager.onPrepare(scheduleId, null, null, message, null, mockPrepareCallback);
        verify(mockAdapter).onPrepare(any(Context.class), any(Assets.class));
        verify(mockPrepareCallback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        // Make sure it's ready
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, manager.onCheckExecutionReadiness(scheduleId));

        // Throw an exception when displaying
        doThrow(new RuntimeException("COOL"))
                .when(mockAdapter)
                .onDisplay(any(Context.class), any(DisplayHandler.class));

        // Display the schedule
        AutomationDriver.ExecutionCallback mockExecuteCallback = mock(AutomationDriver.ExecutionCallback.class);
        manager.onExecute(scheduleId, mockExecuteCallback);

        // Verify the adapter onDisplay was called
        verify(mockAdapter).onDisplay(any(Context.class), any(DisplayHandler.class));
        verify(mockAdapter).onFinish(any(Context.class));

        // Verify the coordinator was not notified
        verify(mockCoordinator, never()).onDisplayStarted(eq(message));

        // Verify the schedule was finished
        verify(mockExecuteCallback).onFinish();
    }

    @Test
    public void testRetryAdapterPrepareMessage() {
        when(mockAssetManager.onPrepare(scheduleId, message)).thenReturn(AssetManager.PREPARE_RESULT_OK);
        when(mockAdapter.isReady(any(Context.class))).thenReturn(true);
        when(mockAdapter.onPrepare(any(Context.class), any(Assets.class))).thenReturn(InAppMessageAdapter.RETRY);

        // Prepare the schedule
        AutomationDriver.PrepareScheduleCallback mockPrepareCallback = mock(AutomationDriver.PrepareScheduleCallback.class);
        manager.onPrepare(scheduleId, null, null, message, null, mockPrepareCallback);
        verify(mockAdapter).onPrepare(any(Context.class), any(Assets.class));

        // Should call it once, but a runnable should be dispatched on the main thread with a delay to retry
        verify(mockAdapter, times(1)).onPrepare(any(Context.class), any(Assets.class));

        // Advance the looper
        Looper mainLooper = Looper.getMainLooper();
        Shadows.shadowOf(mainLooper).runToEndOfTasks();

        // Verify it was called again
        verify(mockAdapter, times(2)).onPrepare(any(Context.class), any(Assets.class));

        verifyNoInteractions(mockPrepareCallback);
    }

    @Test
    public void testRetryAssetsPrepare() {
        when(mockAssetManager.onPrepare(scheduleId, message)).thenReturn(AssetManager.PREPARE_RESULT_RETRY);

        // Prepare the schedule
        AutomationDriver.PrepareScheduleCallback mockPrepareCallback = mock(AutomationDriver.PrepareScheduleCallback.class);
        manager.onPrepare(scheduleId, null, null, message, null, mockPrepareCallback);

        // Should call it once, but a runnable should be dispatched on the main thread with a delay to retry
        verify(mockAssetManager, times(1)).onPrepare(scheduleId, message);

        // Advance the looper
        Looper mainLooper = Looper.getMainLooper();
        Shadows.shadowOf(mainLooper).runToEndOfTasks();

        // Verify it was called again
        verify(mockAssetManager, times(2)).onPrepare(scheduleId, message);

        verifyNoInteractions(mockPrepareCallback);
    }

    @Test
    public void testCancelAdapterPrepare() {
        when(mockAssetManager.onPrepare(scheduleId, message)).thenReturn(AssetManager.PREPARE_RESULT_OK);
        when(mockAdapter.onPrepare(any(Context.class), any(Assets.class))).thenReturn(InAppMessageAdapter.CANCEL);

        // Start preparing
        AutomationDriver.PrepareScheduleCallback mockPrepareCallback = mock(AutomationDriver.PrepareScheduleCallback.class);
        manager.onPrepare(scheduleId, null, null, message, null, mockPrepareCallback);
        verify(mockAdapter).onPrepare(any(Context.class), any(Assets.class));

        // Should call it once
        verify(mockAdapter, times(1)).onPrepare(any(Context.class), any(Assets.class));

        // Return cancel result
        verify(mockPrepareCallback).onFinish(AutomationDriver.PREPARE_RESULT_CANCEL);
    }

    @Test
    public void testCancelAssetsPrepare() {
        when(mockAssetManager.onPrepare(scheduleId, message)).thenReturn(AssetManager.PREPARE_RESULT_CANCEL);
        when(mockAdapter.onPrepare(any(Context.class), any(Assets.class))).thenReturn(InAppMessageAdapter.OK);

        // Start preparing
        AutomationDriver.PrepareScheduleCallback mockPrepareCallback = mock(AutomationDriver.PrepareScheduleCallback.class);
        manager.onPrepare(scheduleId, null, null, message, null, mockPrepareCallback);

        // Should call it once
        verify(mockAssetManager, times(1)).onPrepare(scheduleId, message);

        // Return cancel result
        verify(mockPrepareCallback).onFinish(AutomationDriver.PREPARE_RESULT_CANCEL);
    }

    @Test
    public void testMessageExtending() {
        manager.setMessageExtender(new InAppMessageExtender() {
            @NonNull
            @Override
            public InAppMessage extend(@NonNull InAppMessage message) {
                return InAppMessage.newBuilder(message).setName("extended").build();
            }
        });

        InAppMessageAdapter.Factory factory = mock(InAppMessageAdapter.Factory.class);
        manager.setAdapterFactory(message.getType(), factory);

        // Prepare the message
        AutomationDriver.PrepareScheduleCallback mockPrepareCallback = mock(AutomationDriver.PrepareScheduleCallback.class);
        when(mockAdapter.onPrepare(any(Context.class), any(Assets.class))).thenReturn(InAppMessageAdapter.OK);
        manager.onPrepare(scheduleId, null, null, message, null, mockPrepareCallback);

        verify(factory).createAdapter(argThat(new ArgumentMatcher<InAppMessage>() {
            @Override
            public boolean matches(InAppMessage argument) {
                return argument.getName().equals("extended");
            }
        }));
    }

    @Test
    public void testNotifyAssetManagerNewSchedule() {
        final InAppMessage extended = InAppMessage.newBuilder(message).setName("extended").build();
        manager.setMessageExtender(new InAppMessageExtender() {
            @NonNull
            @Override
            public InAppMessage extend(@NonNull InAppMessage message) {
                return extended;
            }
        });

        manager.onNewMessageSchedule(scheduleId, extended);
        verify(mockAssetManager).onSchedule(eq(scheduleId), argThat(new ArgumentMatcher<Callable<InAppMessage>>() {
            @Override
            public boolean matches(Callable<InAppMessage> argument) {
                // Verify it produces the extended message
                try {
                    return argument.call().equals(extended);
                } catch (Exception e) {
                    return false;
                }
            }
        }));
    }

    @Test
    public void testNotifyScheduleFinished() {
        manager.onMessageScheduleFinished(scheduleId);
        verify(mockAssetManager, times(1)).onFinish(scheduleId);
    }

    @Test
    public void testHoldoutGroupControlEvent() {
        JsonMap reportingContext = JsonMap.newBuilder()
                                          .put("some", "data")
                                          .build();
        JsonValue campaigns = new JsonList(
                Arrays.asList(JsonValue.wrap("one"), JsonValue.wrap("two")))
                .toJsonValue();
        JsonValue context = JsonMap.newBuilder()
                                   .put("report", "context")
                                   .build()
                                   .toJsonValue();

        List<JsonMap> experimentsContext = Collections.singletonList(reportingContext);

        ExperimentResult experiment = new ExperimentResult("channel-id", "contact-id",
                "experiment-id", true, experimentsContext);

        String senderId = "mock-sender-id";
        when(mockAnalytics.getConversionSendId()).thenReturn(senderId);
        String conversionMetadata = "mock-conversion-metadata";
        when(mockAnalytics.getConversionMetadata()).thenReturn(conversionMetadata);

        manager.onPrepare("schedule-id", campaigns, context, message,
                experiment, result -> {
                });
        manager.onExecute("schedule-id", () -> {
        });

        JsonMap expected = JsonMap
                .newBuilder()
                .put("id", JsonMap
                        .newBuilder()
                        .put("message_id", "schedule-id")
                        .put("campaigns", campaigns)
                        .build())
                .put("source", "urban-airship")
                .put("conversion_send_id", senderId)
                .put("conversion_metadata", conversionMetadata)
                .put("context", JsonMap
                        .newBuilder()
                        .putOpt("reporting_context", context)
                        .putOpt("experiments", experimentsContext)
                        .build()
                )
                .put("device", JsonMap
                        .newBuilder()
                        .put("channel_identifier", "channel-id")
                        .put("contact_identifier", "contact-id")
                        .build()
                )
                .put("resolution", JsonMap.newBuilder().put("type", "control").build())
                .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(TYPE_RESOLUTION, expected)));
    }

    @Test
    public void testHoldoutGroupControlEventNullContext() {
        JsonMap reportingContext = JsonMap.newBuilder()
                                          .put("some", "data")
                                          .build();

        List<JsonMap> experimentsContext = Collections.singletonList(reportingContext);

        ExperimentResult experiment = new ExperimentResult("channel-id", "contact-id",
                "experiment-id", true, experimentsContext);

        manager.onPrepare("schedule-id", null, null, message,
                experiment, result -> {
                });
        manager.onExecute("schedule-id", () -> {
        });

        JsonMap expected = JsonMap
                .newBuilder()
                .put("id", JsonMap
                        .newBuilder()
                        .put("message_id", "schedule-id")
                        .build())
                .put("source", "urban-airship")
                .put("context", JsonMap
                        .newBuilder()
                        .putOpt("experiments", experimentsContext)
                        .build()
                )
                .put("device", JsonMap
                        .newBuilder()
                        .put("channel_identifier", "channel-id")
                        .put("contact_identifier", "contact-id")
                        .build()
                )
                .put("resolution", JsonMap.newBuilder().put("type", "control").build())
                .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(TYPE_RESOLUTION, expected)));
    }

    @Test
    public void testHoldoutGroupControlEventJsonNull() {
        JsonMap reportingContext = JsonMap.newBuilder()
                                          .put("some", "data")
                                          .build();
        JsonValue campaigns = JsonValue.NULL;
        JsonValue context = JsonValue.NULL;

        List<JsonMap> experimentsContext = Collections.singletonList(reportingContext);

        ExperimentResult experiment = new ExperimentResult("channel-id", "contact-id",
                "experiment-id", true, experimentsContext);

        manager.onPrepare("schedule-id", null, null, message,
                experiment, result -> {
                });
        manager.onExecute("schedule-id", () -> {
        });

        JsonMap expected = JsonMap
                .newBuilder()
                .put("id", JsonMap
                        .newBuilder()
                        .put("message_id", "schedule-id")
                        .build())
                .put("source", "urban-airship")
                .put("context", JsonMap
                        .newBuilder()
                        .putOpt("experiments", experimentsContext)
                        .build()
                )
                .put("device", JsonMap
                        .newBuilder()
                        .put("channel_identifier", "channel-id")
                        .put("contact_identifier", "contact-id")
                        .build()
                )
                .put("resolution", JsonMap.newBuilder().put("type", "control").build())
                .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(TYPE_RESOLUTION, expected)));
    }

}
