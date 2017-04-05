/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import com.urbanairship.BaseTestCase;
import com.urbanairship.ChannelCapture;
import com.urbanairship.TestApplication;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ChannelCaptureActionTest extends BaseTestCase {
    private ChannelCaptureAction action;
    private ChannelCapture mockChannelCapture;

    private
    @Action.Situation
    int[] acceptedSituations;
    private
    @Action.Situation
    int[] rejectedSituations;

    @Before
    public void setup() {
        action = new ChannelCaptureAction();
        mockChannelCapture = mock(ChannelCapture.class);
        TestApplication.getApplication().setChannelCapture(mockChannelCapture);

        // Accepted situations (PUSH_RECEIVED and MANUAL_INVOCATION)
        acceptedSituations = new int[] {
                Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_PUSH_RECEIVED
        };

        // Rejected situations (All - accepted)
        rejectedSituations = new int[] {
                Action.SITUATION_AUTOMATION,
                Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON,
                Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON,
                Action.SITUATION_PUSH_OPENED,
                Action.SITUATION_WEB_VIEW_INVOCATION
        };
    }

    @Test
    public void testAcceptsArgument() {
        for (@Action.Situation int situation : acceptedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, 60);
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }

        for (@Action.Situation int situation : rejectedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, 60);
            assertFalse("Should reject arguments in situation " + situation,
                    action.acceptsArguments(args));
        }
    }

    @Test
    public void testPerformEnable() {
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED, 60);
        action.perform(args);
        verify(mockChannelCapture).enable(60, TimeUnit.SECONDS);
    }

    @Test
    public void testPerformDisable() {
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED, -1);
        action.perform(args);
        verify(mockChannelCapture).disable();
    }
}
