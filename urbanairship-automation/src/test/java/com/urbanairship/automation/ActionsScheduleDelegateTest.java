/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.os.Bundle;
import android.os.Looper;

import com.urbanairship.StubbedActionRunRequest;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionCompletionCallback;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionRunRequestFactory;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.automation.actions.Actions;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class ActionsScheduleDelegateTest {

    private ActionRunRequestFactory mockRunRequestFactory;
    private ActionsScheduleDelegate delegate;
    private Schedule<Actions> actionsSchedule;

    @Before
    public void setup() {
        mockRunRequestFactory = mock(ActionRunRequestFactory.class);
        delegate = new ActionsScheduleDelegate(mockRunRequestFactory);

        JsonMap actionsMap = JsonMap.newBuilder()
                                    .put("cool", "story")
                                    .build();

        actionsSchedule = Schedule.newBuilder(new Actions(actionsMap))
                                  .setId("scheduleId")
                                  .addTrigger(Triggers.newActiveSessionTriggerBuilder().setGoal(1).build())
                                  .build();
    }

    @Test
    public void testExecute() {

        StubbedActionRunRequest stubbedActionRunRequest = new StubbedActionRunRequest() {
            @Override
            public void run(Looper looper, ActionCompletionCallback callback) {
                callback.onFinish(new ActionArguments(Action.SITUATION_AUTOMATION, ActionValue.wrap("cool"), new Bundle()), ActionResult.newEmptyResult());
            }
        };

        stubbedActionRunRequest = spy(stubbedActionRunRequest);

        when(mockRunRequestFactory.createActionRequest("cool")).thenReturn(stubbedActionRunRequest);

        // Prepare
        delegate.onPrepareSchedule(actionsSchedule, actionsSchedule.getData(), mock(AutomationDriver.PrepareScheduleCallback.class));

        // Execute
        AutomationDriver.ExecutionCallback callback = mock(AutomationDriver.ExecutionCallback.class);
        delegate.onExecute(actionsSchedule, callback);

        verify(stubbedActionRunRequest).setValue(JsonValue.wrapOpt("story"));
        verify(stubbedActionRunRequest).setSituation(Action.SITUATION_AUTOMATION);
        verify(stubbedActionRunRequest).setMetadata(ArgumentMatchers.argThat(new ArgumentMatcher<Bundle>() {
            @Override
            public boolean matches(Bundle argument) {
                return argument.get(ActionArguments.ACTION_SCHEDULE_ID_METADATA).equals("scheduleId");
            }
        }));
    }

    @Test
    public void testPrepare() {
        // Prepare
        AutomationDriver.PrepareScheduleCallback callback = mock(AutomationDriver.PrepareScheduleCallback.class);
        delegate.onPrepareSchedule(actionsSchedule, actionsSchedule.getData(), callback);
        verify(callback).onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
    }

    @Test
    public void testReadiness() {
        assertEquals(AutomationDriver.READY_RESULT_INVALIDATE, delegate.onCheckExecutionReadiness(actionsSchedule));

        // Prepare
        delegate.onPrepareSchedule(actionsSchedule, actionsSchedule.getData(), mock(AutomationDriver.PrepareScheduleCallback.class));
        assertEquals(AutomationDriver.READY_RESULT_CONTINUE, delegate.onCheckExecutionReadiness(actionsSchedule));
    }

}
