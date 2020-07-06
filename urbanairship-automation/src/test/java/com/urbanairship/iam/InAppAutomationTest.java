package com.urbanairship.iam;

import android.os.Looper;

import com.urbanairship.AirshipLoopers;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.automation.AutomationDriver;
import com.urbanairship.automation.AutomationEngine;
import com.urbanairship.automation.Triggers;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.iam.tags.TagGroupManager;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.reactive.Subject;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.remotedata.RemoteDataPayload;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Shadows;

import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link InAppAutomation}.
 */
@RunWith(AndroidJUnit4.class)
public class InAppAutomationTest {

    private InAppAutomation inAppAutomation;
    private AutomationEngine.ScheduleListener<InAppMessageSchedule> scheduleListener;

    private InAppMessageDriver driver;
    private AutomationEngine<InAppMessageSchedule> mockEngine;
    private RemoteData mockRemoteData;

    private InAppMessageSchedule schedule;

    private TagGroupManager mockTagManager;
    private TestInAppRemoteDataObserver testObserver;
    private InAppMessageManager mockIamManager;
    private AirshipChannel mockChannel;

    @Before
    public void setup() {
        driver = mock(InAppMessageDriver.class);
        mockTagManager = mock(TagGroupManager.class);
        mockChannel = mock(AirshipChannel.class);
        mockIamManager = mock(InAppMessageManager.class);
        testObserver = new TestInAppRemoteDataObserver(TestApplication.getApplication().preferenceDataStore);
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

        mockRemoteData = mock(RemoteData.class);
        Subject<RemoteDataPayload> subject = Subject.create();
        when(mockRemoteData.payloadsForType(any(String.class))).thenReturn(subject);

        inAppAutomation = new InAppAutomation(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore,
                mockEngine, mockRemoteData, mockChannel, mockTagManager, testObserver, mockIamManager);

        InAppMessageScheduleInfo info = InAppMessageScheduleInfo.newBuilder()
                                                                .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                                .setMessage(InAppMessage.newBuilder()
                                                                                        .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                                        .setId("message id")
                                                                                        .addAction("action_name", JsonValue.wrap("action_value"))
                                                                                        .build())
                                                                .build();
        schedule = new InAppMessageSchedule("schedule id", JsonMap.EMPTY_MAP, info);

        inAppAutomation.init();
        inAppAutomation.onAirshipReady(UAirship.shared());

        runLooperTasks();
    }

    @Test
    public void testPrepareSchedule() {
        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);
        verify(mockIamManager).onPrepare(schedule.getId(), schedule.getInfo().getInAppMessage(), callback);
    }

    @Test
    public void testOnCheckExecutionReadiness() {
        when(mockIamManager.onCheckExecutionReadiness(schedule.getId())).thenReturn(AutomationDriver.READY_RESULT_CONTINUE);
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, driver.onCheckExecutionReadiness(schedule));
    }

    @Test
    public void testIsPaused() {
        when(mockIamManager.onCheckExecutionReadiness(schedule.getId())).thenReturn(AutomationDriver.READY_RESULT_CONTINUE);
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

        when(mockRemoteData.isMetadataCurrent(metadata)).thenReturn(false);

        InAppMessageScheduleInfo info = InAppMessageScheduleInfo.newBuilder()
                                                                .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                                .setMessage(InAppMessage.newBuilder()
                                                                                        .setSource(InAppMessage.SOURCE_REMOTE_DATA)
                                                                                        .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                                        .setId("message id")
                                                                                        .addAction("action_name", JsonValue.wrap("action_value"))
                                                                                        .build())
                                                                .build();

        InAppMessageSchedule schedule = new InAppMessageSchedule("Some-ID", metadata, info);

        // Verify it returns an invalidate result
        assertEquals(AutomationDriver.READY_RESULT_INVALIDATE, driver.onCheckExecutionReadiness(schedule));
    }

    @Test
    public void testInvalidScheduleOnPrepare() {
        JsonMap observerMetadata = JsonMap.newBuilder()
                                          .putOpt("cool", "schedule")
                                          .build();

        JsonMap scheduleMetadata = JsonMap.newBuilder()
                                          .putOpt("cool", "message")
                                          .build();

        testObserver.setLastPayloadMetadata(observerMetadata);
        when(mockRemoteData.isMetadataCurrent(observerMetadata)).thenReturn(true);

        when(mockRemoteData.isMetadataCurrent(scheduleMetadata)).thenReturn(false);

        InAppMessageScheduleInfo info = InAppMessageScheduleInfo.newBuilder()
                                                                .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                                .setMessage(InAppMessage.newBuilder()
                                                                                        .setSource(InAppMessage.SOURCE_REMOTE_DATA)
                                                                                        .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                                        .setId("message id")
                                                                                        .addAction("action_name", JsonValue.wrap("action_value"))
                                                                                        .build())
                                                                .build();

        InAppMessageSchedule schedule = new InAppMessageSchedule("Some-ID", scheduleMetadata, info);

        // Prepare the schedule
        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        driver.onPrepareSchedule(schedule, null, callback);

        runLooperTasks();

        // Verify the schedule is invalidated
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_INVALIDATE);
    }

    @Test
    public void testForwardScheduleListener() {
        scheduleListener.onScheduleCancelled(schedule);
        scheduleListener.onScheduleExpired(schedule);
        scheduleListener.onScheduleLimitReached(schedule);
        scheduleListener.onNewSchedule(schedule);

        verify(mockIamManager, times(1)).onNewSchedule(schedule);
        verify(mockIamManager, times(1)).onScheduleCancelled(schedule);
        verify(mockIamManager, times(1)).onScheduleExpired(schedule);
        verify(mockIamManager, times(1)).onScheduleLimitReached(schedule);
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
