/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.automation.ActionSchedule;
import com.urbanairship.automation.ActionScheduleInfo;
import com.urbanairship.automation.Automation;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ScheduleActionTest extends BaseTestCase {

    private Automation automation;
    private JsonMap scheduleJson;
    private ScheduleAction action;

    @Action.Situation
    private int[] acceptedSituations;

    @Action.Situation
    private int[] rejectedSituations;

    @Before
    public void setup() {
        this.automation = mock(Automation.class);
        TestApplication.getApplication().setAutomation(automation);

        this.action = new ScheduleAction();

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
            ActionArguments args = ActionTestUtils.createArgs(situation, scheduleJson);
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }

        for (@Action.Situation int situation : rejectedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, scheduleJson);
            assertFalse("Should reject arguments in situation " + situation,
                    action.acceptsArguments(args));
        }
    }

    /**
     * Test perform with valid JSON.
     */
    @Test
    public void testPerform() throws JsonException {
        ActionScheduleInfo scheduleInfo = ActionScheduleInfo.parseJson(JsonValue.wrap(scheduleJson));
        when(automation.schedule(any(ActionScheduleInfo.class))).thenReturn(new ActionSchedule("automation id", scheduleInfo));

        ActionResult result = action.perform(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, scheduleJson));
        assertEquals("automation id", result.getValue().getString());
    }

    /**
     * Test perform with invalid JSON.
     */
    @Test
    public void testPerformInvalidJson() throws JsonException {
        ActionResult result = action.perform( ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, JsonMap.EMPTY_MAP));
        assertNotNull(result.getException());
        assertEquals(ActionResult.STATUS_EXECUTION_ERROR, result.getStatus());
    }
}