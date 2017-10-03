package com.urbanairship.iam;

import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.urbanairship.BaseTestCase;
import com.urbanairship.automation.AutomationDriver;
import com.urbanairship.automation.ParseScheduleException;
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

import java.util.concurrent.Executor;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link InAppMessageDriver}
 */
public class InAppMessageDriverTest extends BaseTestCase {

    private InAppMessageDriver driver;
    private AutomationDriver.Callback resultCallback;
    private InAppMessageDriver.Callbacks iamCallbacks;
    private InAppMessageSchedule schedule;
    private InAppMessageAdapter adapter;

    @Before
    public void setup() {
        iamCallbacks = mock(InAppMessageDriver.Callbacks.class);
        resultCallback = mock(AutomationDriver.Callback.class);

        driver = new InAppMessageDriver(new Executor() {
            @Override
            public void execute(@NonNull Runnable runnable) {
                runnable.run();
            }
        });
        driver.setCallbacks(iamCallbacks);

        adapter = mock(InAppMessageAdapter.class);
        driver.setAdapter(InAppMessage.TYPE_CUSTOM, adapter);


        InAppMessageScheduleInfo scheduleInfo = InAppMessageScheduleInfo.newBuilder()
                                                                        .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                                        .setMessage(InAppMessage.newBuilder()
                                                                                                .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                                                .setId("message id")
                                                                                                .build())
                                                                        .build();

        schedule = new InAppMessageSchedule("schedule id", scheduleInfo);
    }

    @Test
    public void testIsScheduleReadyToExecute() {
        when(adapter.prefetchAssets(any(Context.class), eq(schedule.getInfo().getInAppMessage()), any(Bundle.class)))
                .thenReturn(InAppMessageAdapter.OK);

        // First check should start downloading the assets
        assertFalse(driver.isScheduleReadyToExecute(schedule));
        verify(iamCallbacks).onScheduleDataFetched(schedule);

        // Second check after the assets have been downloaded should return true
        when(iamCallbacks.isScheduleReadyToDisplay(schedule)).thenReturn(true);
        assertTrue(driver.isScheduleReadyToExecute(schedule));
    }

    @Test
    public void testRetryFetchAssets() {
        when(adapter.prefetchAssets(any(Context.class), eq(schedule.getInfo().getInAppMessage()), any(Bundle.class)))
                .thenReturn(InAppMessageAdapter.RETRY);

        // First check should start downloading the assets
        assertFalse(driver.isScheduleReadyToExecute(schedule));

        // Should call it once, but a runnable should be dispatched on the main thread with a delay to retry
        verify(adapter, times(1)).prefetchAssets(any(Context.class), eq(schedule.getInfo().getInAppMessage()), any(Bundle.class));

        // Advance the looper
        ShadowLooper mainLooper = Shadows.shadowOf(Looper.getMainLooper());
        mainLooper.runToEndOfTasks();

        // Verify it was called again
        verify(adapter, times(2)).prefetchAssets(any(Context.class), eq(schedule.getInfo().getInAppMessage()), any(Bundle.class));
    }

    @Test
    public void testExecuteTriggeredSchedule() {
        when(adapter.prefetchAssets(any(Context.class), eq(schedule.getInfo().getInAppMessage()), any(Bundle.class)))
                .then(new Answer<Integer>() {
                    @Override
                    public Integer answer(InvocationOnMock invocation) throws Throwable {
                        Bundle assets = invocation.getArgument(2);
                        assets.putString("Some asset", "Some value");
                        return InAppMessageAdapter.OK;
                    }
                });

        // First check should start downloading the assets
        assertFalse(driver.isScheduleReadyToExecute(schedule));
        verify(iamCallbacks).onScheduleDataFetched(schedule);

        // Second check after the assets have been downloaded should return true
        when(iamCallbacks.isScheduleReadyToDisplay(schedule)).thenReturn(true);
        assertTrue(driver.isScheduleReadyToExecute(schedule));

        // Execute the triggered schedule
        driver.onExecuteTriggeredSchedule(schedule, resultCallback);

        // Result callback should be called in displayFinished
        verifyZeroInteractions(resultCallback);

        // Verify the callback is called to display the in-app message
        verify(iamCallbacks).onDisplay(eq(schedule), eq(adapter), argThat(new ArgumentMatcher<Bundle>() {
            @Override
            public boolean matches(Bundle argument) {
                if (argument == null) {
                    return false;
                }

                return "Some value".equals(argument.getString("Some asset"));
            }
        }));
    }

    @Test
    public void testDisplayFinished() {
        testExecuteTriggeredSchedule();
        driver.displayFinished(schedule.getId());
        verify(resultCallback).onFinish();
    }

    @Test
    public void testCreateSchedule() throws ParseScheduleException {
        InAppMessageSchedule fromDriver = driver.createSchedule("some id", schedule.getInfo());

        assertEquals("some id", fromDriver.getId());
        assertEquals(schedule.getInfo().getInAppMessage(), fromDriver.getInfo().getInAppMessage());
    }

}