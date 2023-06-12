package com.urbanairship.automation;

import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.AirshipLoopers;
import com.urbanairship.PendingResult;
import com.urbanairship.PrivacyManager;
import com.urbanairship.ShadowAirshipExecutorsLegacy;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.audience.AudienceOverridesProvider;
import com.urbanairship.automation.actions.Actions;
import com.urbanairship.automation.deferred.Deferred;
import com.urbanairship.automation.deferred.DeferredScheduleClient;
import com.urbanairship.automation.limits.FrequencyChecker;
import com.urbanairship.automation.limits.FrequencyConstraint;
import com.urbanairship.automation.limits.FrequencyLimitManager;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.AttributeMutation;
import com.urbanairship.channel.TagGroupsMutation;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageManager;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.RetryingExecutor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.urbanairship.automation.tags.TestUtils.tagSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link InAppAutomation}.
 */
@Config(
        sdk = 28,
        shadows = { ShadowAirshipExecutorsLegacy.class },
        application = TestApplication.class
)
@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(AndroidJUnit4.class)
public class InAppAutomationTest {

    private InAppAutomation inAppAutomation;
    private AutomationEngine.ScheduleListener scheduleListener;

    private AutomationDriver driver;
    private AutomationEngine mockEngine;

    private AudienceOverridesProvider audienceOverridesProvider = new AudienceOverridesProvider();
    private InAppRemoteDataObserver mockObserver;
    private InAppMessageManager mockIamManager;
    private AirshipChannel mockChannel;
    private DeferredScheduleClient mockDeferredScheduleClient;
    private InAppMessageScheduleDelegate mockMessageScheduleDelegate;
    private ActionsScheduleDelegate mockActionsScheduleDelegate;
    private FrequencyLimitManager mockFrequencyLimitManager;
    private InAppRemoteDataObserver.Delegate remoteDataObserverDelegate;
    private PrivacyManager privacyManager;
    private RetryingExecutor executor;

    private AirshipConfigOptions config = AirshipConfigOptions.newBuilder().build();
    private AirshipRuntimeConfig mockRuntimeConfig = mock(AirshipRuntimeConfig.class);

    @Before
    public void setup() {
        when(mockRuntimeConfig.getConfigOptions()).thenAnswer((Answer<AirshipConfigOptions>) invocation -> config);
        mockChannel = mock(AirshipChannel.class);
        mockIamManager = mock(InAppMessageManager.class);
        mockObserver = mock(InAppRemoteDataObserver.class);
        mockEngine = mock(AutomationEngine.class);
        mockDeferredScheduleClient = mock(DeferredScheduleClient.class);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                driver = invocation.getArgument(0);
                return null;
            }
        }).when(mockEngine).start(any(AutomationDriver.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                scheduleListener = invocation.getArgument(0);
                return null;
            }
        }).when(mockEngine).setScheduleListener(any(AutomationEngine.ScheduleListener.class));

        executor = new RetryingExecutor(new Handler(Looper.getMainLooper()), new Executor() {
            @Override
            public void execute(@NonNull Runnable runnable) {
                runnable.run();
            }
        });

        mockMessageScheduleDelegate = mock(InAppMessageScheduleDelegate.class);
        mockActionsScheduleDelegate = mock(ActionsScheduleDelegate.class);
        mockFrequencyLimitManager = mock(FrequencyLimitManager.class);
        privacyManager = new PrivacyManager(TestApplication.getApplication().preferenceDataStore, PrivacyManager.FEATURE_ALL);

        inAppAutomation = new InAppAutomation(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore,
                mockRuntimeConfig, privacyManager, mockEngine, mockChannel, mockObserver, mockIamManager, executor, mockDeferredScheduleClient,
                mockActionsScheduleDelegate, mockMessageScheduleDelegate, mockFrequencyLimitManager, audienceOverridesProvider);

        inAppAutomation.init();
        inAppAutomation.onAirshipReady(UAirship.shared());

        ArgumentCaptor<InAppRemoteDataObserver.Delegate> argument = ArgumentCaptor.forClass(InAppRemoteDataObserver.Delegate.class);
        verify(mockObserver).subscribe(argument.capture());
        remoteDataObserverDelegate = argument.getValue();

        runLooperTasks();
    }
    @Test
    public void testAutoPauseEnabled() {
        config = AirshipConfigOptions.newBuilder().setAutoPauseInAppAutomationOnLaunch(true).build();
        inAppAutomation.init();
        assertTrue(inAppAutomation.isPaused());
    }

    @Test
    public void testAutoPauseDisabled() {
        config = AirshipConfigOptions.newBuilder().setAutoPauseInAppAutomationOnLaunch(false).build();
        inAppAutomation.init();
        assertFalse(inAppAutomation.isPaused());
    }

    @Test
    public void testGetMessageSchedules() {
        inAppAutomation.getMessageSchedules();
        verify(mockEngine).getSchedulesByType(Schedule.TYPE_IN_APP_MESSAGE);
    }

    @Test
    public void testGetMessageSchedule() {
        inAppAutomation.getMessageSchedule("some id");
        verify(mockEngine).getSchedule("some id", Schedule.TYPE_IN_APP_MESSAGE);
    }

    @Test
    public void testGetMessageSchedulesByGroup() {
        inAppAutomation.getMessageScheduleGroup("some group");
        verify(mockEngine).getSchedules("some group", Schedule.TYPE_IN_APP_MESSAGE);
    }

    @Test
    public void testGetActionSchedules() {
        inAppAutomation.getActionSchedules();
        verify(mockEngine).getSchedulesByType(Schedule.TYPE_ACTION);
    }

    @Test
    public void testGetActionSchedule() {
        inAppAutomation.getActionSchedule("some id");
        verify(mockEngine).getSchedule("some id", Schedule.TYPE_ACTION);
    }

    @Test
    public void testGetActionSchedulesByGroup() {
        inAppAutomation.getActionScheduleGroup("some group");
        verify(mockEngine).getSchedules("some group", Schedule.TYPE_ACTION);
    }

    @Test
    public void testDelayedStart() {
        privacyManager.disable(PrivacyManager.FEATURE_IN_APP_AUTOMATION);
        inAppAutomation.tearDown();
        clearInvocations(mockEngine);
        clearInvocations(mockObserver);

        inAppAutomation.init();
        inAppAutomation.onAirshipReady(UAirship.shared());

        verify(mockEngine, never()).start(driver);
        verify(mockObserver, never()).subscribe(any(InAppRemoteDataObserver.Delegate.class));

        privacyManager.enable(PrivacyManager.FEATURE_IN_APP_AUTOMATION);
        verify(mockEngine).start(driver);
        verify(mockObserver).subscribe(any(InAppRemoteDataObserver.Delegate.class));
    }

    @Test
    public void testStartsWhenScheduleModified() {
        privacyManager.disable(PrivacyManager.FEATURE_IN_APP_AUTOMATION);
        inAppAutomation.tearDown();

        clearInvocations(mockEngine);
        clearInvocations(mockObserver);

        inAppAutomation.init();
        inAppAutomation.onAirshipReady(UAirship.shared());

        verify(mockEngine, never()).start(driver);
        verify(mockObserver, never()).subscribe(any(InAppRemoteDataObserver.Delegate.class));

        inAppAutomation.cancelSchedule("sweet");

        verify(mockEngine).start(driver);
    }

    @Test
    public void testInAppUpdatesSubscription() {
        privacyManager.disable(PrivacyManager.FEATURE_IN_APP_AUTOMATION);
        inAppAutomation.tearDown();

        clearInvocations(mockEngine);
        clearInvocations(mockObserver);

        inAppAutomation.init();
        inAppAutomation.onAirshipReady(UAirship.shared());

        Cancelable subscription = Mockito.mock(Cancelable.class);
        when(mockObserver.subscribe(any(InAppRemoteDataObserver.Delegate.class))).thenReturn(subscription);

        privacyManager.enable(PrivacyManager.FEATURE_IN_APP_AUTOMATION);
        verify(mockObserver).subscribe(any(InAppRemoteDataObserver.Delegate.class));
        verify(subscription, never()).cancel();

        privacyManager.disable(PrivacyManager.FEATURE_IN_APP_AUTOMATION);
        verify(subscription, atLeastOnce()).cancel();
    }

    @Test
    public void testPrepareSchedule() {
        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .build();

        Schedule<InAppMessage> schedule = Schedule.newBuilder(message)
                                                  .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                  .build();

        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        ArgumentCaptor<AutomationDriver.PrepareScheduleCallback> argumentCaptor = ArgumentCaptor.forClass(AutomationDriver.PrepareScheduleCallback.class);
        verify(mockMessageScheduleDelegate).onPrepareSchedule(eq(schedule), eq(schedule.getData()), argumentCaptor.capture());
        argumentCaptor.getValue().onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
    }

    @Test
    public void testDeferredSchedules() throws RequestException {
        when(mockChannel.getId()).thenReturn("some channel");
        CustomEvent event = CustomEvent.newBuilder("some event").build();
        TriggerContext triggerContext = new TriggerContext(Triggers.newCustomEventTriggerBuilder().build(), event.toJsonValue());

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), true, Deferred.TYPE_IN_APP_MESSAGE);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                                                            .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                                                            .build();

        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .build();

        List<TagGroupsMutation> tagOverrides = new ArrayList<>();
        tagOverrides.add(TagGroupsMutation.newRemoveTagsMutation("foo", tagSet("one", "two")));
        tagOverrides.add(TagGroupsMutation.newSetTagsMutation("bar", tagSet("a")));

        List<AttributeMutation> attributeOverrides = new ArrayList<>();
        attributeOverrides.add(AttributeMutation.newRemoveAttributeMutation("foo", 100));
        attributeOverrides.add(AttributeMutation.newRemoveAttributeMutation("bar", 100));

        audienceOverridesProvider.recordChannelUpdate("some channel", tagOverrides, attributeOverrides, null);

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", triggerContext, tagOverrides, attributeOverrides))
                .thenReturn(new Response<>(200, new DeferredScheduleClient.Result(true, message)));

        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);
        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, triggerContext, callback);
        ArgumentCaptor<AutomationDriver.PrepareScheduleCallback> argumentCaptor = ArgumentCaptor.forClass(AutomationDriver.PrepareScheduleCallback.class);
        verify(mockMessageScheduleDelegate).onPrepareSchedule(eq(schedule), eq(message), argumentCaptor.capture());
        argumentCaptor.getValue().onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        // Readiness
        when(mockObserver.isScheduleValid(eq(schedule))).thenReturn(true);
        when(mockMessageScheduleDelegate.onCheckExecutionReadiness(schedule)).thenReturn(AutomationDriver.READY_RESULT_CONTINUE);
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, driver.onCheckExecutionReadiness(schedule));
        verify(mockMessageScheduleDelegate).onCheckExecutionReadiness(schedule);

        // Execution
        AutomationDriver.ExecutionCallback executeCallback = mock(AutomationDriver.ExecutionCallback.class);
        driver.onExecuteTriggeredSchedule(schedule, executeCallback);
        verify(mockMessageScheduleDelegate).onExecute(schedule, executeCallback);
    }

    @Test
    public void testPrepareDeferredScheduleMissedAudience() throws RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        CustomEvent event = CustomEvent.newBuilder("some event").build();
        TriggerContext triggerContext = new TriggerContext(Triggers.newCustomEventTriggerBuilder().build(), event.toJsonValue());

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), true, Deferred.TYPE_IN_APP_MESSAGE);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                                                            .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                                                            .setAudience(Audience.newBuilder()
                                                                                 .setMissBehavior(Audience.MISS_BEHAVIOR_SKIP)
                                                                                 .build())
                                                            .build();

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", triggerContext, null, null))
                .thenReturn(new Response<>(200, new DeferredScheduleClient.Result(false, null)));
        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, triggerContext, callback);

        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_SKIP);
    }

    @Test
    public void testOnCheckExecutionReadinessMessage() {
        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .build();

        Schedule<InAppMessage> schedule = Schedule.newBuilder(message)
                                                  .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                  .build();

        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);

        // Prepare schedule
        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        ArgumentCaptor<AutomationDriver.PrepareScheduleCallback> argumentCaptor = ArgumentCaptor.forClass(AutomationDriver.PrepareScheduleCallback.class);
        verify(mockMessageScheduleDelegate).onPrepareSchedule(eq(schedule), eq(message), argumentCaptor.capture());
        argumentCaptor.getValue().onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        // Check execution readiness
        when(mockObserver.isScheduleValid(eq(schedule))).thenReturn(true);
        when(mockMessageScheduleDelegate.onCheckExecutionReadiness(schedule)).thenReturn(AutomationDriver.READY_RESULT_CONTINUE);
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, driver.onCheckExecutionReadiness(schedule));
    }

    @Test
    public void testOnCheckExecutionReadinessFrequencyLimits() {
        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .build();

        Schedule<InAppMessage> schedule = Schedule.newBuilder(message)
                                                  .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                  .setFrequencyConstraintIds(Collections.singletonList("foo"))
                                                  .build();

        FrequencyChecker mockFrequencyChecker = mock(FrequencyChecker.class);
        PendingResult<FrequencyChecker> pendingResult = new PendingResult<>();
        pendingResult.setResult(mockFrequencyChecker);
        when(mockFrequencyLimitManager.getFrequencyChecker(schedule.getFrequencyConstraintIds())).thenReturn(pendingResult);
        when(mockFrequencyChecker.checkAndIncrement()).thenReturn(true);
        when(mockFrequencyChecker.isOverLimit()).thenReturn(false);

        // Prepare schedule
        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);
        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        ArgumentCaptor<AutomationDriver.PrepareScheduleCallback> argumentCaptor = ArgumentCaptor.forClass(AutomationDriver.PrepareScheduleCallback.class);
        verify(mockMessageScheduleDelegate).onPrepareSchedule(eq(schedule), eq(message), argumentCaptor.capture());
        argumentCaptor.getValue().onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(mockFrequencyChecker).isOverLimit();

        when(mockObserver.isScheduleValid(eq(schedule))).thenReturn(true);
        when(mockMessageScheduleDelegate.onCheckExecutionReadiness(schedule)).thenReturn(AutomationDriver.READY_RESULT_NOT_READY);
        assertEquals(AutomationDriver.READY_RESULT_NOT_READY, driver.onCheckExecutionReadiness(schedule));

        when(mockMessageScheduleDelegate.onCheckExecutionReadiness(schedule)).thenReturn(AutomationDriver.READY_RESULT_CONTINUE);
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, driver.onCheckExecutionReadiness(schedule));

        verify(mockFrequencyChecker, times(1)).checkAndIncrement();
    }

    @Test
    public void testPrepareDeferredScheduleNoMessage() throws RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), true);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                                                            .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                                                            .setAudience(Audience.newBuilder()
                                                                                 .setMissBehavior(Audience.MISS_BEHAVIOR_SKIP)
                                                                                 .build())
                                                            .build();

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, null, null))
                .thenReturn(new Response<>(200, new DeferredScheduleClient.Result(true, null)));

        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);
        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);

        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
    }

    @Test
    public void testOnCheckExecutionReadinessActions() {
        Schedule<Actions> schedule = Schedule.newBuilder(new Actions(JsonMap.EMPTY_MAP))
                                             .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                             .build();

        when(mockObserver.isRemoteSchedule(schedule)).thenReturn(false);
        when(mockObserver.isScheduleValid(schedule)).thenReturn(false);

        // Prepare schedule
        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);
        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        ArgumentCaptor<AutomationDriver.PrepareScheduleCallback> argumentCaptor = ArgumentCaptor.forClass(AutomationDriver.PrepareScheduleCallback.class);
        verify(mockActionsScheduleDelegate).onPrepareSchedule(eq(schedule), eq(schedule.getData()), argumentCaptor.capture());
        argumentCaptor.getValue().onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        when(mockObserver.isScheduleValid(eq(schedule))).thenReturn(true);
        when(mockActionsScheduleDelegate.onCheckExecutionReadiness(schedule)).thenReturn(AutomationDriver.READY_RESULT_CONTINUE);
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, driver.onCheckExecutionReadiness(schedule));
    }

    @Test
    public void testPrepareDeferredScheduleFailedResponse() throws RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), true);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                                                            .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                                                            .build();

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, null, null))
                .thenReturn(new Response<>(400, null));

        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);

        verifyNoInteractions(callback);

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, null, null))
                .thenReturn(new Response<>(200, new DeferredScheduleClient.Result(true, null)));
        runLooperTasks();

        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
    }

    @Test
    public void testPrepareDeferredScheduleNoResponse() throws RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), true, Deferred.TYPE_IN_APP_MESSAGE);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                                                            .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                                                            .build();

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, null, null))
                .thenThrow(new RequestException("neat"))
                .thenReturn(new Response<>(200, new DeferredScheduleClient.Result(true, null)));

        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);

        verifyNoInteractions(callback);
        runLooperTasks();
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
    }

    @Test
    public void testPrepareDeferredScheduleNoResponseNoRetry() throws RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), false, Deferred.TYPE_IN_APP_MESSAGE);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                                                            .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                                                            .build();

        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, null, null))
                .thenThrow(new RequestException("neat"));

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
    }

    @Test
    public void testPrepareDeferredScheduleNoChannel() {
        when(mockChannel.getId()).thenReturn(null);

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), false, Deferred.TYPE_IN_APP_MESSAGE);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                                                            .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                                                            .build();

        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);
        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        verifyNoInteractions(callback);
    }

    @Test
    public void testPrepareDeferredSchedule409StatusCode() throws RequestException, InterruptedException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), false, Deferred.TYPE_IN_APP_MESSAGE);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                .build();

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, null, null))
                .thenReturn(new Response<>(409, null));

        doAnswer((Answer) invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(mockObserver).refreshOutdated(any(), any());

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);

        runLooperTasks();

        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_INVALIDATE);
        verifyNoMoreInteractions(callback);
    }

    @Test
    public void testPrepareDeferredSchedule429StatusCode() throws RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), false, Deferred.TYPE_IN_APP_MESSAGE);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                .build();

        Map<String, String> headers = new HashMap<String, String>() {{
            put("Location", "https://fakeLocation.com");
            put("Retry-After", "60");
        }};

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, null, null))
                .thenReturn(new Response<>(429, null, null, headers));

        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);
        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);

        verifyNoInteractions(callback);

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://fakeLocation.com"), "some channel", null, null, null))
                .thenReturn(new Response<>(200, new DeferredScheduleClient.Result(true, null)));

        runLooperTasks();

        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
        verifyNoMoreInteractions(callback);
    }

    @Test
    public void testPrepareDeferredSchedule429StatusCodeNoLocationNoRetryTime() throws RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), false, Deferred.TYPE_IN_APP_MESSAGE);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                .build();

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, null, null))
                .thenReturn(new Response<>(429, null))
                .thenReturn(new Response<>(200, new DeferredScheduleClient.Result(true, null)));

        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);
        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);

        runLooperTasks();

        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
        verifyNoMoreInteractions(callback);
    }

    @Test
    public void testPrepareDeferredSchedule307StatusCode() throws RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), false, Deferred.TYPE_IN_APP_MESSAGE);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                .build();

        Map<String, String> headers = new HashMap<String, String>() {{
            put("Location", "https://fakeLocation.com");
            put("Retry-After", "60");
        }};

        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, null, null))
                .thenReturn(new Response<>(307, null, null, headers));

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://fakeLocation.com"), "some channel", null, null, null))
                .thenReturn(new Response<>(200, new DeferredScheduleClient.Result(true, null)));

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);

        verifyNoInteractions(callback);

        runLooperTasks();

        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
        verifyNoMoreInteractions(callback);
    }

    @Test
    public void testPrepareDeferredSchedule307StatusCodeNoLocationNoRetryTime() throws RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), false, Deferred.TYPE_IN_APP_MESSAGE);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                .build();

        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, null, null))
                .thenReturn(new Response<>(307, null))
                .thenReturn(new Response<>(200, new DeferredScheduleClient.Result(true, null)));

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);

        runLooperTasks();

        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
        verifyNoMoreInteractions(callback);
    }

    @Test
    public void testIsPaused() {
        Schedule<Actions> schedule = Schedule.newBuilder(new Actions(JsonMap.EMPTY_MAP))
                                             .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                             .build();

        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);

        // Prepare schedule
        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        ArgumentCaptor<AutomationDriver.PrepareScheduleCallback> argumentCaptor = ArgumentCaptor.forClass(AutomationDriver.PrepareScheduleCallback.class);
        verify(mockActionsScheduleDelegate).onPrepareSchedule(eq(schedule), eq(schedule.getData()), argumentCaptor.capture());
        argumentCaptor.getValue().onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        when(mockActionsScheduleDelegate.onCheckExecutionReadiness(schedule)).thenReturn(AutomationDriver.READY_RESULT_CONTINUE);

        inAppAutomation.setPaused(true);
        assertEquals(AutomationDriver.READY_RESULT_NOT_READY, driver.onCheckExecutionReadiness(schedule));

        clearInvocations(mockEngine);

        inAppAutomation.setPaused(false);
        verify(mockEngine).checkPendingSchedules();
        when(mockObserver.isScheduleValid(eq(schedule))).thenReturn(true);
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, driver.onCheckExecutionReadiness(schedule));
    }

    @Test
    public void testInvalidScheduleOnExecution() {
        JsonMap metadata = JsonMap.newBuilder()
                                  .putOpt("cool", "story")
                                  .build();

        InAppMessage message = InAppMessage.newBuilder()
                                           .setSource(InAppMessage.SOURCE_REMOTE_DATA)
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .addAction("action_name", JsonValue.wrap("action_value"))
                                           .build();

        Schedule<InAppMessage> schedule = Schedule.newBuilder(message)
                                                  .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                  .setMetadata(metadata)
                                                  .build();

        when(mockObserver.isScheduleValid(eq(schedule))).thenReturn(false);

        // Verify it returns an invalidate result
        assertEquals(AutomationDriver.READY_RESULT_INVALIDATE, driver.onCheckExecutionReadiness(schedule));
    }

    @Test
    public void testInvalidScheduleOnPrepare() {
        InAppMessage message = InAppMessage.newBuilder()
                                           .setSource(InAppMessage.SOURCE_REMOTE_DATA)
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .addAction("action_name", JsonValue.wrap("action_value"))
                                           .build();

        Schedule<InAppMessage> schedule = Schedule.newBuilder(message)
                                                  .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                  .build();

        when(mockObserver.refreshAndCheckCurrentSync(any(), any())).thenReturn(false);


        // Prepare the schedule
        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);

        runLooperTasks();

        // Verify the schedule is invalidated
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_INVALIDATE);
    }

    @Test
    public void testForwardMessageScheduleListener() {
        InAppMessage message = InAppMessage.newBuilder()
                                           .setSource(InAppMessage.SOURCE_REMOTE_DATA)
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .addAction("action_name", JsonValue.wrap("action_value"))
                                           .build();

        Schedule<InAppMessage> schedule = Schedule.newBuilder(message)
                                                  .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                  .build();

        scheduleListener.onNewSchedule(schedule);
        verify(mockMessageScheduleDelegate, times(1))
                .onNewSchedule(schedule);

        scheduleListener.onScheduleLimitReached(schedule);
        scheduleListener.onScheduleCancelled(schedule);
        scheduleListener.onScheduleExpired(schedule);
        verify(mockMessageScheduleDelegate, times(3)).onScheduleFinished(schedule);
    }

    @Test
    public void testAudienceConditionsCheckDefaultMissBehavior() {
        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .build();

        Schedule<InAppMessage> schedule = Schedule.newBuilder(message)
                                                  .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                  .setAudience(Audience.newBuilder()
                                                                       .setNotificationsOptIn(true)
                                                                       .build())
                                                  .build();

        // Start preparing
        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);
        AutomationDriver.PrepareScheduleCallback mockPrepareCallback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, mockPrepareCallback);

        // Verify the miss behavior
        verify(mockPrepareCallback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
    }

    @Test
    public void testAudienceConditionsCheckMissBehaviorCancel() {
        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .build();

        Schedule<InAppMessage> schedule = Schedule.newBuilder(message)
                                                  .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                  .setAudience(Audience.newBuilder()
                                                                       .setNotificationsOptIn(true)
                                                                       .setMissBehavior(Audience.MISS_BEHAVIOR_CANCEL)
                                                                       .build())
                                                  .build();

        // Start preparing
        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);
        AutomationDriver.PrepareScheduleCallback mockPrepareCallback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, mockPrepareCallback);

        // Verify the miss behavior
        verify(mockPrepareCallback).onFinish(AutomationDriver.PREPARE_RESULT_CANCEL);
    }

    @Test
    public void testAudienceConditionsCheckMissBehaviorSkip() {
        Schedule<Actions> schedule = Schedule.newBuilder(new Actions(JsonMap.EMPTY_MAP))
                                             .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                             .setAudience(Audience.newBuilder()
                                                                  .setNotificationsOptIn(true)
                                                                  .setMissBehavior(Audience.MISS_BEHAVIOR_SKIP)
                                                                  .build())
                                             .build();

        // Start preparing
        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);
        AutomationDriver.PrepareScheduleCallback mockPrepareCallback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, mockPrepareCallback);

        // Verify the miss behavior
        verify(mockPrepareCallback).onFinish(AutomationDriver.PREPARE_RESULT_SKIP);
    }

    @Test
    public void testAudienceConditionsCheckMissBehaviorPenalize() {
        Schedule<Actions> schedule = Schedule.newBuilder(new Actions(JsonMap.EMPTY_MAP))
                                             .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                             .setAudience(Audience.newBuilder()
                                                                  .setNotificationsOptIn(true)
                                                                  .setMissBehavior(Audience.MISS_BEHAVIOR_PENALIZE)
                                                                  .build())
                                             .build();

        // Start preparing
        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);
        AutomationDriver.PrepareScheduleCallback mockPrepareCallback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, mockPrepareCallback);

        // Verify the miss behavior
        verify(mockPrepareCallback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
    }

    @Test
    public void testExecuteMessage() {
        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .build();

        Schedule<InAppMessage> schedule = Schedule.newBuilder(message)
                                                  .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                  .build();

        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);

        // Prepare schedule
        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        ArgumentCaptor<AutomationDriver.PrepareScheduleCallback> argumentCaptor = ArgumentCaptor.forClass(AutomationDriver.PrepareScheduleCallback.class);
        verify(mockMessageScheduleDelegate).onPrepareSchedule(eq(schedule), eq(message), argumentCaptor.capture());
        argumentCaptor.getValue().onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        AutomationDriver.ExecutionCallback executionCallback = mock(AutomationDriver.ExecutionCallback.class);
        driver.onExecuteTriggeredSchedule(schedule, executionCallback);
        verify(mockMessageScheduleDelegate).onExecute(schedule, executionCallback);
    }

    @Test
    public void testExecuteActions() {
        Schedule<Actions> schedule = Schedule.newBuilder(new Actions(JsonMap.EMPTY_MAP))
                                             .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                             .build();

        // Prepare schedule
        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);
        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        ArgumentCaptor<AutomationDriver.PrepareScheduleCallback> argumentCaptor = ArgumentCaptor.forClass(AutomationDriver.PrepareScheduleCallback.class);
        verify(mockActionsScheduleDelegate).onPrepareSchedule(eq(schedule), eq(schedule.getData()), argumentCaptor.capture());
        argumentCaptor.getValue().onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        AutomationDriver.ExecutionCallback executionCallback = mock(AutomationDriver.ExecutionCallback.class);
        driver.onExecuteTriggeredSchedule(schedule, executionCallback);
        verify(mockActionsScheduleDelegate).onExecute(schedule, executionCallback);
    }

    @Test
    public void testUpdateConstraints() {
        List<FrequencyConstraint> constraints = new ArrayList<>();
        constraints.add(FrequencyConstraint.newBuilder()
                                           .setRange(TimeUnit.DAYS, 10)
                                           .setId("ID")
                                           .setCount(10)
                                           .build());

        remoteDataObserverDelegate.updateConstraints(constraints);
        verify(mockFrequencyLimitManager).updateConstraints(constraints);
    }

    @Test
    public void testConstraintsIsOverLimit() {
        List<String> constraintIds = new ArrayList<>();
        constraintIds.add("foo");

        FrequencyChecker mockFrequencyChecker = mock(FrequencyChecker.class);
        PendingResult<FrequencyChecker> pendingResult = new PendingResult<>();
        pendingResult.setResult(mockFrequencyChecker);
        when(mockFrequencyLimitManager.getFrequencyChecker(constraintIds)).thenReturn(pendingResult);

        when(mockFrequencyChecker.isOverLimit()).thenReturn(true);

        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .build();



        List<FrequencyConstraint> constraints = new ArrayList<>();
        constraints.add(FrequencyConstraint.newBuilder()
                                           .setRange(TimeUnit.MINUTES, 10)
                                           .setId("foo")
                                           .setCount(1)
                                           .build());

        Schedule<InAppMessage> schedule = Schedule.newBuilder(message)
                                                  .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                  .setFrequencyConstraintIds(constraintIds)
                                                  .build();

        // Start preparing
        when(mockObserver.refreshAndCheckCurrentSync(any(), eq(schedule))).thenReturn(true);
        AutomationDriver.PrepareScheduleCallback mockPrepareCallback = mock(AutomationDriver.PrepareScheduleCallback.class);
        remoteDataObserverDelegate.updateConstraints(constraints);
        driver.onPrepareSchedule(schedule, null, mockPrepareCallback);

        // Verify the miss behavior
        verify(mockPrepareCallback).onFinish(AutomationDriver.PREPARE_RESULT_SKIP);
    }

    @Test
    public void testNewUserCutOff() {
        ShadowPackageManager packageManager = Shadows.shadowOf(TestApplication.getApplication().getPackageManager());
        PackageInfo info = packageManager.getInternalMutablePackageInfo(TestApplication.getApplication().getPackageName());
        info.firstInstallTime = 9191;

        when(mockObserver.getScheduleNewUserCutOffTime()).thenReturn(Long.valueOf(-1));

        inAppAutomation.tearDown();
        inAppAutomation.init();
        inAppAutomation.onAirshipReady(UAirship.shared());


        verify(mockObserver).setScheduleNewUserCutOffTime(9191);
    }

    /**
     * Helper method to run all the looper tasks.
     */
    private void runLooperTasks() {
        Looper mainLooper = Looper.getMainLooper();
        Looper backgroundLooper = AirshipLoopers.getBackgroundLooper();

        do {
            Shadows.shadowOf(mainLooper).runToEndOfTasks();
            Shadows.shadowOf(backgroundLooper).runToEndOfTasks();
        }
        while (Shadows.shadowOf(mainLooper).getScheduler().areAnyRunnable() || Shadows.shadowOf(backgroundLooper).getScheduler().areAnyRunnable());
    }

}
