/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.push.PushMessage;
import com.urbanairship.richpush.RichPushInbox;

import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;


public class OpenRichPushInboxActionTest extends BaseTestCase {

    private OpenRichPushInboxAction action;
    private RichPushInbox mockInbox;

    @Before
    public void setup() {
        action = new OpenRichPushInboxAction();

        mockInbox = mock(RichPushInbox.class);

        TestApplication.getApplication().setInbox(mockInbox);
    }

    /**
     * Test accepts arguments
     */
    @Test
    public void testAcceptsArguments() {
        @Action.Situation int[] situations = new int[] {
                Action.SITUATION_PUSH_OPENED,
                Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION,
                Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON,
                Action.SITUATION_AUTOMATION
        };

        // Should accept null value
        for (@Action.Situation int situation : situations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, null);
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }

        // Should accept message ID as the action value
        for (@Action.Situation int situation : situations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, "message_id");
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }
    }

    /**
     * Test action perform when the message ID is not specified it starts an activity to view the inbox.
     */
    @Test
    public void testPerformNoMessageId() {
        action.perform(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, null));

        verify(mockInbox).startInboxActivity();
    }


    /**
     * Test action perform when the message is available it starts an activity to view the message.
     */
    @Test
    public void testPerform() {
        action.perform(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "message_id"));

        verify(mockInbox).startMessageActivity("message_id");
    }

    /**
     * Test "auto" placeholder looks for the message's ID in the push message metadata.
     */
    @Test
    public void testPerformMessageIdPlaceHolderPushMetadata() {
        Bundle pushBundle = new Bundle();
        pushBundle.putString(PushMessage.EXTRA_RICH_PUSH_ID, "the_message_id");
        Bundle metadata = new Bundle();
        metadata.putParcelable(ActionArguments.PUSH_MESSAGE_METADATA, new PushMessage(pushBundle));

        action.perform(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "auto", metadata));

        verify(mockInbox).startMessageActivity("the_message_id");
    }

    /**
     * Test "auto" placeholder looks for the message's ID in the rich push message ID metadata.
     */
    @Test
    public void testPerformMessageIdPlaceHolderRichPushMessageMetadata() {
        Bundle metadata = new Bundle();
        metadata.putString(ActionArguments.RICH_PUSH_ID_METADATA, "the_message_id");

        action.perform(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "auto", metadata));

        verify(mockInbox).startMessageActivity("the_message_id");
    }

    /**
     * Test "auto" placeholder will fail to find the message ID if no metadata is available
     * and tries to view the inbox instead.
     */
    @Test
    public void testPerformMessageIdPlaceHolderNoMetadata() {
        action.perform(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "auto"));

        verify(mockInbox).startInboxActivity();
    }

    /**
     * Test "" placeholder will load the inbox.
     */
    @Test
    public void testPerformEmptyMessageId() {
        action.perform(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, ""));

        verify(mockInbox).startInboxActivity();
    }
}
