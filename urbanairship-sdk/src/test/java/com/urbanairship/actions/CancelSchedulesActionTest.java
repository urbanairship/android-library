/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.automation.Automation;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CancelSchedulesActionTest extends BaseTestCase {

    private Automation automation;
    private CancelSchedulesAction action;

    @Action.Situation
    private int[] acceptedSituations;

    @Action.Situation
    private int[] rejectedSituations;

    @Before
    public void setup() {
        this.automation = mock(Automation.class);
        TestApplication.getApplication().setAutomation(automation);

        this.action = new CancelSchedulesAction();

        // Accepted situations
        this.acceptedSituations = new int[] {
                Action.SITUATION_PUSH_RECEIVED,
                Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION,

        };

        // Rejected situations
        this.rejectedSituations = new int[] {
                Action.SITUATION_PUSH_OPENED,
                Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON,
                Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON
        };
    }

    /**
     * Test accepts arguments for the cancel all value.
     */
    @Test
    public void testAcceptsCancelAllArgument() {
        JsonValue argValue = JsonValue.wrap(CancelSchedulesAction.ALL);

        for (@Action.Situation int situation : acceptedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, argValue);
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }

        for (@Action.Situation int situation : rejectedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, argValue);
            assertFalse("Should reject arguments in situation " + situation,
                    action.acceptsArguments(args));
        }
    }

    /**
     * Test accepts arguments for canceling groups and IDs
     */
    @Test
    public void testAcceptsMapArguments() {
        JsonMap argValue = JsonMap.newBuilder()
                .put(CancelSchedulesAction.GROUPS, "group")
                .put(CancelSchedulesAction.IDS, "id")
                .build();

        for (@Action.Situation int situation : acceptedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, argValue);
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }

        for (@Action.Situation int situation : rejectedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, argValue);
            assertFalse("Should reject arguments in situation " + situation,
                    action.acceptsArguments(args));
        }
    }


    /**
     * Test perform with the cancel all argument.
     */
    @Test
    public void testPerformCancelAll() {
        JsonValue argValue = JsonValue.wrap(CancelSchedulesAction.ALL);
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, argValue);

        action.perform(args);

        verify(automation).cancelAll();
    }

    /**
     * Test perform cancels a list of groups.
     */
    @Test
    public void testPerformGroups() {
        List<String> groups = new ArrayList<>();
        groups.add("group 1");
        groups.add("group 2");

        JsonMap argValue = JsonMap.newBuilder()
                                  .put(CancelSchedulesAction.GROUPS, JsonValue.wrapOpt(groups))
                                  .build();

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, argValue);

        action.perform(args);

        for (String group : groups) {
            verify(automation, atLeastOnce()).cancelGroup(group);
        }
    }

    /**
     * Test perform cancels a single group
     */
    @Test
    public void testPerformSingleGroup() {
        JsonMap argValue = JsonMap.newBuilder()
                                  .put(CancelSchedulesAction.GROUPS, "group 1")
                                  .build();

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, argValue);

        action.perform(args);
        verify(automation).cancelGroup("group 1");
    }

    /**
     * Test perform cancels a list of IDs.
     */
    @Test
    public void testPerformIds() {
        List<String> ids = new ArrayList<>();
        ids.add("id 1");
        ids.add("id 2");

        JsonMap argValue = JsonMap.newBuilder()
                                  .put(CancelSchedulesAction.IDS, JsonValue.wrapOpt(ids))
                                  .build();

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, argValue);

        action.perform(args);

        verify(automation).cancel(ids);
    }

    /**
     * Test perform cancels a single ID.
     */
    @Test
    public void testPerformSingleId() {
        JsonMap argValue = JsonMap.newBuilder()
                                  .put(CancelSchedulesAction.IDS, "id 1")
                                  .build();

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, argValue);

        action.perform(args);

        verify(automation).cancel("id 1");
    }
}