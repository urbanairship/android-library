package com.urbanairship.automation;

import android.os.Handler;
import android.os.Looper;

import com.urbanairship.AirshipLoopers;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.automation.actions.Actions;
import com.urbanairship.automation.auth.AuthException;
import com.urbanairship.automation.deferred.Deferred;
import com.urbanairship.automation.deferred.DeferredScheduleClient;
import com.urbanairship.automation.tags.AudienceManager;
import com.urbanairship.automation.tags.TagGroupLookupResponseCache;
import com.urbanairship.automation.tags.TagGroupResult;
import com.urbanairship.automation.tags.TagSelector;
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
import com.urbanairship.util.RetryingExecutor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Shadows;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.urbanairship.automation.tags.TestUtils.tagSet;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link InAppAutomation}.
 */
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
            public Void answer(InvocationOnMock invocation) throws Throwable {
                driver = invocation.getArgument(0);
                return null;
            }
        }).when(mockEngine).start(any(AutomationDriver.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                scheduleListener = invocation.getArgument(0);
                return null;
            }
        }).when(mockEngine).setScheduleListener(any(AutomationEngine.ScheduleListener.class));

        RetryingExecutor executor = new RetryingExecutor(new Handler(Looper.getMainLooper()), new Executor() {
            @Override
            public void execute(@NonNull Runnable runnable) {
                runnable.run();
            }
        });

        mockMessageScheduleDelegate = mock(InAppMessageScheduleDelegate.class);
        mockActionsScheduleDelegate = mock(ActionsScheduleDelegate.class);

        inAppAutomation = new InAppAutomation(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore,
                mockEngine, mockChannel, mockAudienceManager, mockObserver, mockIamManager, executor, mockDeferredScheduleClient,
                mockActionsScheduleDelegate, mockMessageScheduleDelegate);

        inAppAutomation.init();
        inAppAutomation.onAirshipReady(UAirship.shared());

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
        verify(mockMessageScheduleDelegate).onPrepareSchedule(eq(schedule.getId()), eq(schedule.getData()), argumentCaptor.capture());
        argumentCaptor.getValue().onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
    }

    @Test
    public void testDeferredSchedules() throws MalformedURLException, AuthException, RequestException {
        when(mockChannel.getId()).thenReturn("some channel");
        CustomEvent event = CustomEvent.newBuilder("some event").build();
        TriggerContext triggerContext = new TriggerContext(Triggers.newCustomEventTriggerBuilder().build(), event.toJsonValue());

        Deferred deferredScheduleData = new Deferred(new URL("https://neat"), true);
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

        when(mockDeferredScheduleClient.performRequest(new URL("https://neat"), "some channel", triggerContext, tagOverrides, attributeOverrides))
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(200)
                        .setResult(new DeferredScheduleClient.Result(true, message))
                        .build());

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, triggerContext, callback);
        ArgumentCaptor<AutomationDriver.PrepareScheduleCallback> argumentCaptor = ArgumentCaptor.forClass(AutomationDriver.PrepareScheduleCallback.class);
        verify(mockMessageScheduleDelegate).onPrepareSchedule(eq(schedule.getId()), eq(message), argumentCaptor.capture());
        argumentCaptor.getValue().onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        when(mockObserver.isRemoteSchedule(schedule)).thenReturn(false);
        when(mockObserver.isScheduleValid(schedule)).thenReturn(false);

        // Readiness
        when(mockMessageScheduleDelegate.onCheckExecutionReadiness(schedule.getId())).thenReturn(AutomationDriver.READY_RESULT_CONTINUE);
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, driver.onCheckExecutionReadiness(schedule));
        verify(mockMessageScheduleDelegate).onCheckExecutionReadiness(schedule.getId());

        // Execution
        AutomationDriver.ExecutionCallback executeCallback = mock(AutomationDriver.ExecutionCallback.class);
        driver.onExecuteTriggeredSchedule(schedule, executeCallback);
        verify(mockMessageScheduleDelegate).onExecute(schedule.getId(), executeCallback);
    }

    @Test
    public void testPrepareDeferredScheduleMissedAudience() throws MalformedURLException, AuthException, RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        CustomEvent event = CustomEvent.newBuilder("some event").build();
        TriggerContext triggerContext = new TriggerContext(Triggers.newCustomEventTriggerBuilder().build(), event.toJsonValue());

        Deferred deferredScheduleData = new Deferred(new URL("https://neat"), true);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                                                            .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                                                            .setAudience(Audience.newBuilder()
                                                                                 .setMissBehavior(Audience.MISS_BEHAVIOR_SKIP)
                                                                                 .build())
                                                            .build();

        when(mockDeferredScheduleClient.performRequest(new URL("https://neat"), "some channel", triggerContext, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
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
        verify(mockMessageScheduleDelegate).onPrepareSchedule(eq(schedule.getId()), eq(message), argumentCaptor.capture());
        argumentCaptor.getValue().onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        // Check execution readiness
        when(mockMessageScheduleDelegate.onCheckExecutionReadiness(schedule.getId())).thenReturn(AutomationDriver.READY_RESULT_CONTINUE);
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, driver.onCheckExecutionReadiness(schedule));
    }

    @Test
    public void testPrepareDeferredScheduleNoMessage() throws MalformedURLException, AuthException, RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(new URL("https://neat"), true);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                                                            .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                                                            .setAudience(Audience.newBuilder()
                                                                                 .setMissBehavior(Audience.MISS_BEHAVIOR_SKIP)
                                                                                 .build())
                                                            .build();

        when(mockDeferredScheduleClient.performRequest(new URL("https://neat"), "some channel", null, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
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
        verify(mockActionsScheduleDelegate).onPrepareSchedule(eq(schedule.getId()), eq(schedule.getData()), argumentCaptor.capture());
        argumentCaptor.getValue().onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        when(mockActionsScheduleDelegate.onCheckExecutionReadiness(schedule.getId())).thenReturn(AutomationDriver.READY_RESULT_CONTINUE);
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, driver.onCheckExecutionReadiness(schedule));
    }

    @Test
    public void testPrepareDeferredScheduleFailedResponse() throws MalformedURLException, AuthException, RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(new URL("https://neat"), true);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                                                            .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                                                            .build();

        when(mockDeferredScheduleClient.performRequest(new URL("https://neat"), "some channel", null, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(400).build());

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);

        verifyZeroInteractions(callback);

        when(mockDeferredScheduleClient.performRequest(new URL("https://neat"), "some channel", null, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(200)
                        .setResult(new DeferredScheduleClient.Result(true, null))
                        .build());
        runLooperTasks();

        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
    }

    @Test
    public void testPrepareDeferredScheduleNoResponse() throws MalformedURLException, AuthException, RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(new URL("https://neat"), true);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                                                            .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                                                            .build();

        when(mockDeferredScheduleClient.performRequest(new URL("https://neat"), "some channel", null, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
                .thenThrow(new RequestException("neat"))
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(200)
                        .setResult(new DeferredScheduleClient.Result(true, null))
                        .build());

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);

        verifyZeroInteractions(callback);
        runLooperTasks();
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
    }

    @Test
    public void testPrepareDeferredScheduleNoResponseNoRetry() throws MalformedURLException, AuthException, RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(new URL("https://neat"), false);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                                                            .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                                                            .build();

        when(mockDeferredScheduleClient.performRequest(new URL("https://neat"), "some channel", null, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
                .thenThrow(new RequestException("neat"));

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
    }

    @Test
    public void testPrepareDeferredScheduleAuthException() throws MalformedURLException, AuthException, RequestException {
        when(mockChannel.getId()).thenReturn("some channel");

        Deferred deferredScheduleData = new Deferred(new URL("https://neat"), false);
        Schedule<Deferred> schedule = Schedule.newBuilder(deferredScheduleData)
                                              .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                                              .build();

        when(mockDeferredScheduleClient.performRequest(new URL("https://neat"), "some channel", null, EMPTY_TAG_OVERRIDES, EMPTY_ATTRIBUTE_OVERRIDES))
                .thenThrow(new AuthException("neat"))
                .thenReturn(new Response.Builder<DeferredScheduleClient.Result>(200)
                        .setResult(new DeferredScheduleClient.Result(true, null))
                        .build());

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        verifyZeroInteractions(callback);

        runLooperTasks();
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
    }

    @Test
    public void testPrepareDeferredScheduleNoChannel() throws MalformedURLException, AuthException, RequestException {
        when(mockChannel.getId()).thenReturn(null);

        Deferred deferredScheduleData = new Deferred(new URL("https://neat"), false);
        Schedule<? extends ScheduleData> schedule = Schedule.newBuilder(deferredScheduleData)
                                                            .addTrigger(Triggers.newCustomEventTriggerBuilder().build())
                                                            .build();

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        verifyZeroInteractions(callback);
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
        verify(mockActionsScheduleDelegate).onPrepareSchedule(eq(schedule.getId()), eq(schedule.getData()), argumentCaptor.capture());
        argumentCaptor.getValue().onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        when(mockActionsScheduleDelegate.onCheckExecutionReadiness(schedule.getId())).thenReturn(AutomationDriver.READY_RESULT_CONTINUE);

        inAppAutomation.setPaused(true);
        assertEquals(AutomationDriver.READY_RESULT_NOT_READY, driver.onCheckExecutionReadiness(schedule));

        clearInvocations(mockEngine);

        inAppAutomation.setPaused(false);
        verify(mockEngine).checkPendingSchedules();
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, driver.onCheckExecutionReadiness(schedule));
    }

    @Test
    public void testEnable() {
        clearInvocations(mockEngine);
        inAppAutomation.setEnabled(true);
        verify(mockEngine).setPaused(false);
    }

    @Test
    public void testDisable() {
        clearInvocations(mockEngine);
        inAppAutomation.setEnabled(false);
        verify(mockEngine).setPaused(true);
    }

    @Test
    public void testNewConfig() {
        JsonMap config = JsonMap.newBuilder()
                                .put("tag_groups", JsonMap.newBuilder()
                                                          .put("enabled", false)
                                                          .put("cache_max_age_seconds", 1)
                                                          .put("cache_stale_read_age_seconds", 11)
                                                          .put("cache_prefer_local_until_seconds", 111)
                                                          .build())
                                .build();

        inAppAutomation.onNewConfig(config);

        verify(mockAudienceManager).setEnabled(false);
        verify(mockAudienceManager).setCacheMaxAgeTime(1, TimeUnit.SECONDS);
        verify(mockAudienceManager).setCacheStaleReadTime(11, TimeUnit.SECONDS);
        verify(mockAudienceManager).setPreferLocalTagDataTime(111, TimeUnit.SECONDS);

        Mockito.reset(mockAudienceManager);

        // verify null config resets to defaults

        inAppAutomation.onNewConfig(null);

        verify(mockAudienceManager).setEnabled(true);
        verify(mockAudienceManager).setCacheMaxAgeTime(TimeUnit.MILLISECONDS.toSeconds(TagGroupLookupResponseCache.DEFAULT_MAX_AGE_TIME_MS), TimeUnit.SECONDS);
        verify(mockAudienceManager).setCacheStaleReadTime(TimeUnit.MILLISECONDS.toSeconds(TagGroupLookupResponseCache.DEFAULT_STALE_READ_TIME_MS), TimeUnit.SECONDS);
        verify(mockAudienceManager).setPreferLocalTagDataTime(TimeUnit.MILLISECONDS.toSeconds(AudienceManager.DEFAULT_PREFER_LOCAL_DATA_TIME_MS), TimeUnit.SECONDS);
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
        when(mockObserver.isUpToDate()).thenReturn(true);

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
        verify(mockIamManager, times(1))
                .onNewMessageSchedule(schedule.getId(), schedule.getData());

        scheduleListener.onScheduleLimitReached(schedule);
        scheduleListener.onScheduleCancelled(schedule);
        scheduleListener.onScheduleExpired(schedule);
        verify(mockIamManager, times(3)).onMessageScheduleFinished(schedule.getId());
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
    public void testAudienceConditionCheckWithTagGroups() {
        Map<String, Set<String>> tagGroups = new HashMap<>();
        tagGroups.put("expected group", Collections.singleton("expected tag"));
        when(mockAudienceManager.getTags(tagGroups)).thenReturn(new TagGroupResult(true, tagGroups));

        Schedule<Actions> schedule = Schedule.newBuilder(new Actions(JsonMap.EMPTY_MAP))
                                             .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                             .setAudience(Audience.newBuilder()
                                                                  .setNotificationsOptIn(true)
                                                                  .setMissBehavior(Audience.MISS_BEHAVIOR_SKIP)
                                                                  .setTagSelector(TagSelector.tag("expected tag", "expected group"))
                                                                  .build())
                                             .build();

        // Start preparing
        AutomationDriver.PrepareScheduleCallback mockPrepareCallback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, mockPrepareCallback);

        // Verify prepare result
        verify(mockPrepareCallback).onFinish(AutomationDriver.PREPARE_RESULT_SKIP);
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
        verify(mockMessageScheduleDelegate).onPrepareSchedule(eq(schedule.getId()), eq(message), argumentCaptor.capture());
        argumentCaptor.getValue().onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        AutomationDriver.ExecutionCallback executionCallback = mock(AutomationDriver.ExecutionCallback.class);
        driver.onExecuteTriggeredSchedule(schedule, executionCallback);
        verify(mockMessageScheduleDelegate).onExecute(schedule.getId(), executionCallback);
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
        verify(mockActionsScheduleDelegate).onPrepareSchedule(eq(schedule.getId()), eq(schedule.getData()), argumentCaptor.capture());
        argumentCaptor.getValue().onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);

        AutomationDriver.ExecutionCallback executionCallback = mock(AutomationDriver.ExecutionCallback.class);
        driver.onExecuteTriggeredSchedule(schedule, executionCallback);
        verify(mockActionsScheduleDelegate).onExecute(schedule.getId(), executionCallback);
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
