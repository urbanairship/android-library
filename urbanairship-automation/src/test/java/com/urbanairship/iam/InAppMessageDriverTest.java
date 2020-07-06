package com.urbanairship.iam;

import com.urbanairship.automation.AutomationDriver;
import com.urbanairship.automation.ParseScheduleException;
import com.urbanairship.automation.TriggerContext;
import com.urbanairship.automation.Triggers;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link InAppMessageDriver}
 */
@RunWith(AndroidJUnit4.class)
public class InAppMessageDriverTest {

    private InAppMessageDriver driver;
    private AutomationDriver.ExecutionCallback executionCallback;
    private InAppMessageSchedule schedule;

    @Before
    public void setup() {
        executionCallback = mock(AutomationDriver.ExecutionCallback.class);

        driver = new InAppMessageDriver() {
            @Override
            public void onPrepareSchedule(@NonNull InAppMessageSchedule schedule, @Nullable TriggerContext triggerContext, @NonNull PrepareScheduleCallback callback) {

            }

            @Override
            public int onCheckExecutionReadiness(@NonNull InAppMessageSchedule schedule) {
                return 0;
            }

            @Override
            public void onExecuteTriggeredSchedule(@NonNull InAppMessageSchedule schedule, @NonNull ExecutionCallback finishCallback) {

            }
        };

        InAppMessageScheduleInfo scheduleInfo = InAppMessageScheduleInfo.newBuilder()
                                                                        .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                                        .setMessage(InAppMessage.newBuilder()
                                                                                                .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                                                .setId("message id")
                                                                                                .build())
                                                                        .build();

        schedule = new InAppMessageSchedule("schedule id", JsonMap.EMPTY_MAP, scheduleInfo);
    }

    @Test
    public void testCreateSchedule() throws ParseScheduleException {
        InAppMessageSchedule fromDriver = driver.createSchedule("some id", schedule.getMetadata(), schedule.getInfo());

        assertEquals("some id", fromDriver.getId());
        assertEquals(schedule.getInfo().getInAppMessage(), fromDriver.getInfo().getInAppMessage());
    }

}
