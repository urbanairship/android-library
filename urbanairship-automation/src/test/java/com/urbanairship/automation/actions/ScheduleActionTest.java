/* Copyright Airship and Contributors */

package com.urbanairship.automation.actions;

import com.urbanairship.PendingResult;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.automation.InAppAutomation;
import com.urbanairship.automation.Schedule;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class ScheduleActionTest {

    private InAppAutomation automation;
    private JsonMap scheduleJson;
    private ScheduleAction action;

    @Action.Situation
    private int[] acceptedSituations;

    @Action.Situation
    private int[] rejectedSituations;

    @Before
    public void setup() {
        this.automation = mock(InAppAutomation.class);

        this.action = new ScheduleAction(new Callable<InAppAutomation>() {
            @Override
            public InAppAutomation call() {
                return automation;
            }
        });

        // Accepted situations
        this.acceptedSituations = new int[] {
                Action.SITUATION_PUSH_RECEIVED,
                Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION,
                Action.SITUATION_AUTOMATION
        };

        // Rejected situations
        this.rejectedSituations = new int[] {
                Action.SITUATION_PUSH_OPENED,
                Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON,
                Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON
        };

        List<JsonMap> triggersJson = new ArrayList<>();
        triggersJson.add(JsonMap.newBuilder()
                                .put("type", "foreground")
                                .put("goal", 20.0)
                                .build());

        scheduleJson = JsonMap.newBuilder()
                              .put("actions", JsonMap.newBuilder()
                                                     .put("tag_action", "cat")
                                                     .build())
                              .put("triggers", JsonValue.wrapOpt(triggersJson))
                              .put("group", "campaign")
                              .put("limit", 10)
                              .build();
    }

    /**
     * Test accepts arguments.
     */
    @Test
    public void testAcceptsArguments() {
        for (@Action.Situation int situation : acceptedSituations) {
            ActionArguments args = createArgs(situation, scheduleJson);
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }

        for (@Action.Situation int situation : rejectedSituations) {
            ActionArguments args = createArgs(situation, scheduleJson);
            assertFalse("Should reject arguments in situation " + situation,
                    action.acceptsArguments(args));
        }
    }

    private ActionArguments createArgs(int situation, JsonMap value) {
        return new ActionArguments(situation, ActionValue.wrap(value), null);
    }

    /**
     * Test perform with valid JSON.
     */
    @Test
    public void testPerform() {
        PendingResult<Boolean> pendingResult = new PendingResult<>();
        pendingResult.setResult(true);

        when(automation.schedule(any(Schedule.class))).thenReturn(pendingResult);

        ActionResult result = action.perform(createArgs(Action.SITUATION_MANUAL_INVOCATION, scheduleJson));
        assertNotNull(result.getValue().getString());
    }

    /**
     * Test perform with invalid JSON.
     */
    @Test
    public void testPerformInvalidJson() {
        ActionResult result = action.perform(createArgs(Action.SITUATION_MANUAL_INVOCATION, JsonMap.EMPTY_MAP));
        assertNotNull(result.getException());
        assertEquals(ActionResult.STATUS_EXECUTION_ERROR, result.getStatus());
    }

}
