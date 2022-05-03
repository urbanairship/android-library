package com.urbanairship.automation;

import static com.urbanairship.automation.tags.TestUtils.tagSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.urbanairship.AirshipLoopers;
import com.urbanairship.PendingResult;
import com.urbanairship.PrivacyManager;
import com.urbanairship.ShadowAirshipExecutorsLegacy;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.automation.actions.Actions;
import com.urbanairship.automation.auth.AuthException;
import com.urbanairship.automation.deferred.Deferred;
import com.urbanairship.automation.deferred.DeferredScheduleClient;
import com.urbanairship.automation.limits.FrequencyChecker;
import com.urbanairship.automation.limits.FrequencyConstraint;
import com.urbanairship.automation.limits.FrequencyLimitManager;
import com.urbanairship.automation.tags.AudienceManager;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.AttributeMutation;
import com.urbanairship.channel.TagGroupsMutation;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageManager;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.reactive.Subscription;
import com.urbanairship.util.RetryingExecutor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
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

    private static List<TagGroupsMutation> EMPTY_TAG_OVERRIDES = Collections.emptyList();
    private static List<AttributeMutation> EMPTY_ATTRIBUTE_OVERRIDES = Collections.emptyList();

    private InAppAutomation inAppAutomation;
    private AutomationEngine.ScheduleListener scheduleListener;

    private AutomationDriver driver;
    private AutomationEngine mockEngine;

    private AudienceManager mockAudienceManager;
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

    @Before
    public void setup() {
        mockAudienceManager = mock(AudienceManager.class);
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
                privacyManager, mockEngine, mockChannel, mockAudienceManager, mockObserver, mockIamManager, executor, mockDeferredScheduleClient,
                mockActionsScheduleDelegate, mockMessageScheduleDelegate, mockFrequencyLimitManager);

        inAppAutomation.init();
        inAppAutomation.onAirshipReady(UAirship.shared());

        ArgumentCaptor<InAppRemoteDataObserver.Delegate> argument = ArgumentCaptor.forClass(InAppRemoteDataObserver.Delegate.class);
        verify(mockObserver).subscribe(argument.capture());
        remoteDataObserverDelegate = argument.getValue();

        runLooperTasks();
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

        Subscription subscription = Subscription.create(null);
        when(mockObserver.subscribe(any(InAppRemoteDataObserver.Delegate.class))).thenReturn(subscription);

        privacyManager.enable(PrivacyManager.FEATURE_IN_APP_AUTOMATION);
        verify(mockObserver).subscribe(any(InAppRemoteDataObserver.Delegate.class));
        assertFalse(subscription.isCancelled());

        privacyManager.disable(PrivacyManager.FEATURE_IN_APP_AUTOMATION);
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void testPrepareSchedule() {
        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .build();

        Schedule<InAppMessage> schedule = Schedule.newBuilder(message)
                                                  .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                  .build();

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        ArgumentCaptor<AutomationDriver.PrepareScheduleCallback> argumentCaptor = ArgumentCaptor.forClass(AutomationDriver.PrepareScheduleCallback.class);
        verify(mockMessageScheduleDelegate).onPrepareSchedule(eq(schedule), eq(schedule.getData()), argumentCaptor.capture());
        argumentCaptor.getValue().onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
    }

    @Test
    public void testDeferredSchedules() throws AuthException, RequestException {
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
        when(mockAudienceManager.getTagOverrides()).thenReturn(tagOverrides);

        List<AttributeMutation> attributeOverrides = new ArrayList<>();
        attributeOverrides.add(AttributeMutation.newRemoveAttributeMutation("foo", 100));
        attributeOverrides.add(AttributeMutation.newRemoveAttributeMutation("bar", 100));
        when(mockAudienceManager.getAttributeOverrides()).thenReturn(attributeOverrides);

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", triggerContext, tagOverrides, attributeOverrides))
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(200)
                        .setResult(new DeferredScheduleClient.Result(true, message))
                        .build());

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, triggerContext, callback);
        ArgumentCaptor<AutomationDriver.PrepareScheduleCallback> argumentCaptor = ArgumentCaptor.forClass(AutomationDriver.PrepareScheduleCallback.class);
        verify(mockMessageScheduleDelegate).onPrepareSchedule(eq(schedule), eq(message), argumentCaptor.capture());
        argumentCaptor.getValue().onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        when(mockObserver.isRemoteSchedule(schedule)).thenReturn(false);
        when(mockObserver.isScheduleValid(schedule)).thenReturn(false);

        // Readiness
        when(mockMessageScheduleDelegate.onCheckExecutionReadiness(schedule)).thenReturn(AutomationDriver.READY_RESULT_CONTINUE);
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, driver.onCheckExecutionReadiness(schedule));
        verify(mockMessageScheduleDelegate).onCheckExecutionReadiness(schedule);

        // Execution
        AutomationDriver.ExecutionCallback executeCallback = mock(AutomationDriver.ExecutionCallback.class);
        driver.onExecuteTriggeredSchedule(schedule, executeCallback);
        verify(mockMessageScheduleDelegate).onExecute(schedule, executeCallback);
    }

    @Test
    public void testPrepareDeferredScheduleMissedAudience() throws AuthException, RequestException {
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

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", triggerContext, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(200)
                        .setResult(new DeferredScheduleClient.Result(false, null))
                        .build());

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

        // Prepare schedule
        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        ArgumentCaptor<AutomationDriver.PrepareScheduleCallback> argumentCaptor = ArgumentCaptor.forClass(AutomationDriver.PrepareScheduleCallback.class);
        verify(mockMessageScheduleDelegate).onPrepareSchedule(eq(schedule), eq(message), argumentCaptor.capture());
        argumentCaptor.getValue().onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        // Check execution readiness
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
        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        ArgumentCaptor<AutomationDriver.PrepareScheduleCallback> argumentCaptor = ArgumentCaptor.forClass(AutomationDriver.PrepareScheduleCallback.class);
        verify(mockMessageScheduleDelegate).onPrepareSchedule(eq(schedule), eq(message), argumentCaptor.capture());
        argumentCaptor.getValue().onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(mockFrequencyChecker).isOverLimit();

        when(mockMessageScheduleDelegate.onCheckExecutionReadiness(schedule)).thenReturn(AutomationDriver.READY_RESULT_NOT_READY);
        assertEquals(AutomationDriver.READY_RESULT_NOT_READY, driver.onCheckExecutionReadiness(schedule));

        when(mockMessageScheduleDelegate.onCheckExecutionReadiness(schedule)).thenReturn(AutomationDriver.READY_RESULT_CONTINUE);
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, driver.onCheckExecutionReadiness(schedule));

        verify(mockFrequencyChecker, times(1)).checkAndIncrement();
    }

    @Test
    public void testPrepareDeferredScheduleNoMessage() throws AuthException, RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), true);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                                                            .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                                                            .setAudience(Audience.newBuilder()
                                                                                 .setMissBehavior(Audience.MISS_BEHAVIOR_SKIP)
                                                                                 .build())
                                                            .build();

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(200)
                        .setResult(new DeferredScheduleClient.Result(true, null))
                        .build());

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
        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        ArgumentCaptor<AutomationDriver.PrepareScheduleCallback> argumentCaptor = ArgumentCaptor.forClass(AutomationDriver.PrepareScheduleCallback.class);
        verify(mockActionsScheduleDelegate).onPrepareSchedule(eq(schedule), eq(schedule.getData()), argumentCaptor.capture());
        argumentCaptor.getValue().onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        when(mockActionsScheduleDelegate.onCheckExecutionReadiness(schedule)).thenReturn(AutomationDriver.READY_RESULT_CONTINUE);
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, driver.onCheckExecutionReadiness(schedule));
    }

    @Test
    public void testPrepareDeferredScheduleFailedResponse() throws AuthException, RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), true);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                                                            .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                                                            .build();

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(400).build());

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);

        verifyNoInteractions(callback);

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(200)
                        .setResult(new DeferredScheduleClient.Result(true, null))
                        .build());
        runLooperTasks();

        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
    }

    @Test
    public void testPrepareDeferredScheduleNoResponse() throws AuthException, RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), true, Deferred.TYPE_IN_APP_MESSAGE);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                                                            .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                                                            .build();

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
                .thenThrow(new RequestException("neat"))
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(200)
                        .setResult(new DeferredScheduleClient.Result(true, null))
                        .build());

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);

        verifyNoInteractions(callback);
        runLooperTasks();
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
    }

    @Test
    public void testPrepareDeferredScheduleNoResponseNoRetry() throws AuthException, RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), false, Deferred.TYPE_IN_APP_MESSAGE);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                                                            .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                                                            .build();

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
                .thenThrow(new RequestException("neat"));

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
    }

    @Test
    public void testPrepareDeferredScheduleAuthException() throws AuthException, RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), false, Deferred.TYPE_IN_APP_MESSAGE);
        Schedule<Deferred> schedule = Schedule.newBuilder(deferredScheduleData)
                                              .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                                              .build();

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
                .thenThrow(new AuthException("neat"))
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(200)
                        .setResult(new DeferredScheduleClient.Result(true, null))
                        .build());

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        verifyNoInteractions(callback);

        runLooperTasks();
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
    }

    @Test
    public void testPrepareDeferredScheduleNoChannel() {
        when(mockChannel.getId()).thenReturn(null);

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), false, Deferred.TYPE_IN_APP_MESSAGE);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                                                            .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                                                            .build();

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        verifyNoInteractions(callback);
    }

    @Test
    public void testPrepareDeferredSchedule409StatusCode() throws AuthException, RequestException, InterruptedException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), false, Deferred.TYPE_IN_APP_MESSAGE);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                .build();

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(409)
                        .build());

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);

        runLooperTasks();

        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_INVALIDATE);
        verifyNoMoreInteractions(callback);
    }

    @Test
    public void testPrepareDeferredSchedule429StatusCode() throws AuthException, RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), false, Deferred.TYPE_IN_APP_MESSAGE);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                .build();

        Map<String, List<String>> headers = new HashMap<String, List<String>>() {{
            put("Location", Collections.singletonList("https://fakeLocation.com"));
            put("Retry-After", Collections.singletonList("60"));
        }};

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(429)
                        .setResponseHeaders(headers)
                        .build());

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);

        verifyNoInteractions(callback);

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://fakeLocation.com"), "some channel", null, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(200)
                        .setResult(new DeferredScheduleClient.Result(true, null))
                        .build());

        runLooperTasks();

        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
        verifyNoMoreInteractions(callback);
    }

    @Test
    public void testPrepareDeferredSchedule429StatusCodeNoLocationNoRetryTime() throws AuthException, RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), false, Deferred.TYPE_IN_APP_MESSAGE);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                .build();

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(429)
                        .build())
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(200)
                        .setResult(new DeferredScheduleClient.Result(true, null))
                        .build());

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);

        runLooperTasks();

        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
        verifyNoMoreInteractions(callback);
    }

    @Test
    public void testPrepareDeferredSchedule307StatusCode() throws AuthException, RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), false, Deferred.TYPE_IN_APP_MESSAGE);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                .build();

        Map<String, List<String>> headers = new HashMap<String, List<String>>() {{
            put("Location", Collections.singletonList("https://fakeLocation.com"));
            put("Retry-After", Collections.singletonList("60"));
        }};

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(307)
                        .setResponseHeaders(headers)
                        .build());

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://fakeLocation.com"), "some channel", null, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(200)
                        .setResult(new DeferredScheduleClient.Result(true, null))
                        .build());

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);

        verifyNoInteractions(callback);

        runLooperTasks();

        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
        verifyNoMoreInteractions(callback);
    }

    @Test
    public void testPrepareDeferredSchedule307StatusCodeNoLocationNoRetryTime() throws AuthException, RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(Uri.parse("https://neat"), false, Deferred.TYPE_IN_APP_MESSAGE);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                .build();

        when(mockDeferredScheduleClient.performRequest(Uri.parse("https://neat"), "some channel", null, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(307)
                        .build())
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(200)
                        .setResult(new DeferredScheduleClient.Result(true, null))
                        .build());

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

        when(mockObserver.isRemoteSchedule(schedule)).thenReturn(false);
        when(mockObserver.isScheduleValid(schedule)).thenReturn(false);

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

        when(mockObserver.isRemoteSchedule(schedule)).thenReturn(true);
        when(mockObserver.isScheduleValid(schedule)).thenReturn(false);

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

        when(mockObserver.isRemoteSchedule(schedule)).thenReturn(true);
        when(mockObserver.isScheduleValid(schedule)).thenReturn(false);

        doAnswer((Answer) invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(mockObserver).attemptRefresh(any());


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
