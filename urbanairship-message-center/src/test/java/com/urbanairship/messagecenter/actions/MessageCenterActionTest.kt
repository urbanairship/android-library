/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter.actions;

import android.os.Bundle;

import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.messagecenter.MessageCenter;
import com.urbanairship.push.PushMessage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.concurrent.Callable;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class MessageCenterActionTest {

    private MessageCenterAction action;
    private MessageCenter mockMessageCenter;


    @Before
    public void setup() {
        mockMessageCenter = mock(MessageCenter.class);
        action = new MessageCenterAction(new Callable<MessageCenter>() {
            @Override
            public MessageCenter call() {
                return mockMessageCenter;
            }
        });
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
            ActionArguments args = createArgs(situation, null);
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }

        // Should accept message ID as the action value
        for (@Action.Situation int situation : situations) {
            ActionArguments args = createArgs(situation, "message_id");
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }
    }
    /**
     * Test action perform when the message ID is not specified it starts an activity to view the inbox.
     */
    @Test
    public void testPerformNoMessageId() {
        action.perform(createArgs(Action.SITUATION_MANUAL_INVOCATION, null));

        verify(mockMessageCenter).showMessageCenter();
    }

    /**
     * Test action perform when the message is available it starts an activity to view the message.
     */
    @Test
    public void testPerform() {
        action.perform(createArgs(Action.SITUATION_MANUAL_INVOCATION, "message_id"));

        verify(mockMessageCenter).showMessageCenter("message_id");
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

        action.perform(createArgs(Action.SITUATION_MANUAL_INVOCATION, "auto", metadata));

        verify(mockMessageCenter).showMessageCenter("the_message_id");
    }

    /**
     * Test "auto" placeholder looks for the message's ID in the rich push message ID metadata.
     */
    @Test
    public void testPerformMessageIdPlaceHolderRichPushMessageMetadata() {
        Bundle metadata = new Bundle();
        metadata.putString(ActionArguments.RICH_PUSH_ID_METADATA, "the_message_id");

        action.perform(createArgs(Action.SITUATION_MANUAL_INVOCATION, "auto", metadata));

        verify(mockMessageCenter).showMessageCenter("the_message_id");
    }

    /**
     * Test "auto" placeholder will fail to find the message ID if no metadata is available
     * and tries to view the inbox instead.
     */
    @Test
    public void testPerformMessageIdPlaceHolderNoMetadata() {
        action.perform(createArgs(Action.SITUATION_MANUAL_INVOCATION, "auto"));

        verify(mockMessageCenter).showMessageCenter();
    }

    /**
     * Test "" placeholder will load the inbox.
     */
    @Test
    public void testPerformEmptyMessageId() {
        action.perform(createArgs(Action.SITUATION_MANUAL_INVOCATION, ""));

        verify(mockMessageCenter).showMessageCenter();
    }

    private ActionArguments createArgs(int situation, String value) {
        return new ActionArguments(situation, ActionValue.wrap(value), null);
    }

    private ActionArguments createArgs(int situation, String value, Bundle metadata) {
        return new ActionArguments(situation, ActionValue.wrap(value), metadata);
    }
}
