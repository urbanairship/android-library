/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.actions;

import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.push.PushMessage;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushMessage;

import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class OpenRichPushInboxActionTest extends BaseTestCase {

    OpenRichPushInboxAction action;
    RichPushInbox mockInbox;

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
                Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON
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
     * Test action perform when the message is unavailable starts an activity to view the inbox.
     */
    @Test
    public void testPerformMessageUnavailable() {
        when(mockInbox.getMessage("message_id")).thenReturn(null);

        action.perform(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "message_id"));

        verify(mockInbox).startInboxActivity();
    }

    /**
     * Test action perform when the message is available it starts an activity to view the message.
     */
    @Test
    public void testPerformMessageAvailable() {
        RichPushMessage message = mock(RichPushMessage.class);
        when(message.getMessageId()).thenReturn("message_id");

        when(mockInbox.getMessage("message_id")).thenReturn(message);

        action.perform(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "message_id"));

        verify(mockInbox).startMessageActivity("message_id");
    }

    /**
     * Test "auto" placeholder looks for the message's ID in the push message metadata.
     */
    @Test
    public void testPerformMessageIdPlaceHolderPushMetadata() {
        RichPushMessage message = mock(RichPushMessage.class);
        when(message.getMessageId()).thenReturn("the_message_id");
        when(mockInbox.getMessage("the_message_id")).thenReturn(message);

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
        RichPushMessage message = mock(RichPushMessage.class);
        when(message.getMessageId()).thenReturn("the_message_id");
        when(mockInbox.getMessage("the_message_id")).thenReturn(message);

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
}
