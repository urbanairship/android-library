package com.urbanairship.iam;

import com.urbanairship.BaseTestCase;
import com.urbanairship.automation.AutomationDriver;
import com.urbanairship.automation.ParseScheduleException;
import com.urbanairship.automation.Triggers;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
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

    @Before
    public void setup() {
        iamCallbacks = mock(InAppMessageDriver.Callbacks.class);
        resultCallback = mock(AutomationDriver.Callback.class);

        driver = new InAppMessageDriver();
        driver.setCallbacks(iamCallbacks);

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
    public void testIsMessageReady() {
        assertFalse(driver.isScheduleReadyToExecute(schedule));
        when(iamCallbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage())).thenReturn(true);
        assertTrue(driver.isScheduleReadyToExecute(schedule));
    }


    @Test
    public void testExecuteTriggeredSchedule() {
        // Execute the triggered schedule
        driver.onExecuteTriggeredSchedule(schedule, resultCallback);

        // Result callback should be called in displayFinished
        verifyZeroInteractions(resultCallback);

        // Verify the callback is called to display the in-app message
        verify(iamCallbacks).onDisplay(schedule.getId());
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