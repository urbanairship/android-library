package com.urbanairship.automation;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.urbanairship.AirshipLoopers;
import com.urbanairship.StubbedActionRunRequest;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionCompletionCallback;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionRunRequestFactory;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.automation.tags.TagGroupManager;
import com.urbanairship.automation.tags.TagGroupResult;
import com.urbanairship.automation.tags.TagSelector;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageManager;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.RetryingExecutor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Shadows;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link InAppAutomation}.
 */
@RunWith(AndroidJUnit4.class)
public class InAppAutomationTest {

    private InAppAutomation inAppAutomation;
    private AutomationEngine.ScheduleListener scheduleListener;

    private AutomationDriver driver;
    private AutomationEngine mockEngine;

    private TagGroupManager mockTagManager;
    private InAppRemoteDataObserver mockObserver;
    private InAppMessageManager mockIamManager;
    private AirshipChannel mockChannel;
    private ActionRunRequestFactory mockActionRunRequestFactory;

    @Before
    public void setup() {
        mockTagManager = mock(TagGroupManager.class);
        mockChannel = mock(AirshipChannel.class);
        mockIamManager = mock(InAppMessageManager.class);
        mockObserver = mock(InAppRemoteDataObserver.class);
        mockEngine = mock(AutomationEngine.class);

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

        mockActionRunRequestFactory = mock(ActionRunRequestFactory.class);

        inAppAutomation = new InAppAutomation(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore,
                mockEngine, mockChannel, mockTagManager, mockObserver, mockIamManager, executor, mockActionRunRequestFactory);

        inAppAutomation.init();
        inAppAutomation.onAirshipReady(UAirship.shared());

        runLooperTasks();
    }

    @Test
    public void testPrepareSchedule() {
        InAppMessage message = InAppMessage.newBuilder()
                                           .setId("cool")
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .build();

        Schedule schedule = Schedule.newMessageScheduleBuilder(message)
                                    .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                    .build();

        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        verify(mockIamManager).onPrepare(schedule.getId(), (InAppMessage) schedule.requireData(), callback);
    }

    @Test
    public void testOnCheckExecutionReadinessMessage() {
        InAppMessage message = InAppMessage.newBuilder()
                                           .setId("cool")
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .build();

        Schedule schedule = Schedule.newMessageScheduleBuilder(message)
                                    .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                    .build();

        when(mockIamManager.onCheckExecutionReadiness(schedule.getId())).thenReturn(AutomationDriver.READY_RESULT_CONTINUE);
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, driver.onCheckExecutionReadiness(schedule));
    }

    @Test
    public void testOnCheckExecutionReadinessActions() {
        Schedule schedule = Schedule.newActionScheduleBuilder(JsonMap.EMPTY_MAP)
                                    .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                    .build();

        when(mockObserver.isRemoteSchedule(schedule)).thenReturn(false);
        when(mockObserver.isScheduleValid(schedule)).thenReturn(false);

        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, driver.onCheckExecutionReadiness(schedule));
    }

    @Test
    public void testExecuteActions() {
        JsonMap actions = JsonMap.newBuilder()
                                 .put("cool", "story")
                                 .build();

        final Schedule schedule = Schedule.newActionScheduleBuilder(actions)
                                          .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                          .build();

        StubbedActionRunRequest stubbedActionRunRequest = new StubbedActionRunRequest() {
            @Override
            public void run(Looper looper, ActionCompletionCallback callback) {
                callback.onFinish(new ActionArguments(Action.SITUATION_AUTOMATION, ActionValue.wrap("cool"), new Bundle()), ActionResult.newEmptyResult());
            }
        };

        stubbedActionRunRequest = spy(stubbedActionRunRequest);

        when(mockActionRunRequestFactory.createActionRequest("cool")).thenReturn(stubbedActionRunRequest);

        AutomationDriver.ExecutionCallback callback = mock(AutomationDriver.ExecutionCallback.class);
        driver.onExecuteTriggeredSchedule(schedule, callback);

        verify(stubbedActionRunRequest).setValue(JsonValue.wrapOpt("story"));
        verify(stubbedActionRunRequest).setSituation(Action.SITUATION_AUTOMATION);
        verify(stubbedActionRunRequest).setMetadata(ArgumentMatchers.argThat(new ArgumentMatcher<Bundle>() {
            @Override
            public boolean matches(Bundle argument) {
                return argument.get(ActionArguments.ACTION_SCHEDULE_METADATA).equals(schedule);
            }
        }));
    }

    @Test
    public void testExecuteMessage() {
        InAppMessage message = InAppMessage.newBuilder()
                                           .setId("cool")
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .build();

        Schedule schedule = Schedule.newMessageScheduleBuilder(message)
                                    .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                    .build();

        AutomationDriver.ExecutionCallback callback = mock(AutomationDriver.ExecutionCallback.class);
        driver.onExecuteTriggeredSchedule(schedule, callback);
        verify(mockIamManager).onExecute(schedule.getId(), callback);
    }

    @Test
    public void testIsPaused() {
        Schedule schedule = Schedule.newActionScheduleBuilder(JsonMap.EMPTY_MAP)
                                    .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                    .build();

        when(mockObserver.isRemoteSchedule(schedule)).thenReturn(false);
        when(mockObserver.isScheduleValid(schedule)).thenReturn(false);


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

        verify(mockTagManager).setEnabled(false);
        verify(mockTagManager).setCacheMaxAgeTime(1, TimeUnit.SECONDS);
        verify(mockTagManager).setCacheStaleReadTime(11, TimeUnit.SECONDS);
        verify(mockTagManager).setPreferLocalTagDataTime(111, TimeUnit.SECONDS);

        Mockito.reset(mockTagManager);

        // verify null config resets to defaults

        inAppAutomation.onNewConfig(null);

        verify(mockTagManager).setEnabled(true);
        verify(mockTagManager).setCacheMaxAgeTime(TimeUnit.MILLISECONDS.toSeconds(TagGroupManager.DEFAULT_CACHE_MAX_AGE_TIME_MS), TimeUnit.SECONDS);
        verify(mockTagManager).setCacheStaleReadTime(TimeUnit.MILLISECONDS.toSeconds(TagGroupManager.DEFAULT_CACHE_STALE_READ_TIME_MS), TimeUnit.SECONDS);
        verify(mockTagManager).setPreferLocalTagDataTime(TimeUnit.MILLISECONDS.toSeconds(TagGroupManager.DEFAULT_PREFER_LOCAL_DATA_TIME_MS), TimeUnit.SECONDS);
    }

    @Test
    public void testInvalidScheduleOnExecution() {
        JsonMap metadata = JsonMap.newBuilder()
                                  .putOpt("cool", "story")
                                  .build();

        InAppMessage message = InAppMessage.newBuilder()
                                           .setSource(InAppMessage.SOURCE_REMOTE_DATA)
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .setId("message id")
                                           .addAction("action_name", JsonValue.wrap("action_value"))
                                           .build();

        Schedule schedule = Schedule.newMessageScheduleBuilder(message)
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
                                           .setId("message id")
                                           .addAction("action_name", JsonValue.wrap("action_value"))
                                           .build();

        Schedule schedule = Schedule.newMessageScheduleBuilder(message)
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
                                           .setId("message id")
                                           .addAction("action_name", JsonValue.wrap("action_value"))
                                           .build();

        Schedule schedule = Schedule.newMessageScheduleBuilder(message)
                                    .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                    .build();

        scheduleListener.onNewSchedule(schedule);
        verify(mockIamManager, times(1)).onNewSchedule(schedule.getId(), (InAppMessage) schedule.requireData());

        scheduleListener.onScheduleLimitReached(schedule);
        scheduleListener.onScheduleCancelled(schedule);
        verify(mockIamManager, times(2)).onScheduleFinished(schedule.getId(), (InAppMessage) schedule.requireData());

        scheduleListener.onScheduleExpired(schedule);
        verify(mockIamManager, times(1)).onScheduleExpired(schedule.getId(), schedule.getEnd(), (InAppMessage) schedule.requireData());
    }

    @Test
    public void testAudienceConditionsCheckDefaultMissBehavior() {
        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .setId("message id")
                                           .build();

        Schedule schedule = Schedule.newMessageScheduleBuilder(message)
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
                                           .setId("message id")
                                           .build();

        Schedule schedule = Schedule.newMessageScheduleBuilder(message)
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
        Schedule schedule = Schedule.newActionScheduleBuilder(JsonMap.EMPTY_MAP)
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
        Schedule schedule = Schedule.newActionScheduleBuilder(JsonMap.EMPTY_MAP)
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
        when(mockTagManager.getTags(tagGroups)).thenReturn(new TagGroupResult(true, tagGroups));

        Schedule schedule = Schedule.newActionScheduleBuilder(JsonMap.EMPTY_MAP)
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
