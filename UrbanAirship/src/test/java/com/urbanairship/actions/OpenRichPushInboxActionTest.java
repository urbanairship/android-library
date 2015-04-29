/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

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

import android.content.Intent;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushManager;
import com.urbanairship.richpush.RichPushMessage;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.shadows.ShadowApplication;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class OpenRichPushInboxActionTest extends BaseTestCase {

    private OpenRichPushInboxAction action;
    private RichPushInbox mockInbox;

    @Before
    public void setup() {
        action = new OpenRichPushInboxAction();

        RichPushManager richPushManager = mock(RichPushManager.class);
        mockInbox = mock(RichPushInbox.class);

        when(richPushManager.getRichPushInbox()).thenReturn(mockInbox);
        TestApplication.getApplication().setRichPushManager(richPushManager);
    }

    /**
     * Test accepts arguments
     */
    @Test
    public void testAcceptsArguments() {
        Situation[] situations = new Situation[] {
                Situation.PUSH_OPENED,
                Situation.MANUAL_INVOCATION,
                Situation.WEB_VIEW_INVOCATION,
                Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON
        };

        // Should accept null value
        for (Situation situation : situations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, null);
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }

        // Should accept message ID as the action value
        for (Situation situation : situations) {
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
        action.perform(ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, null));

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedActivity();
        assertEquals("com.urbanairship.VIEW_RICH_PUSH_INBOX", startedIntent.getAction());
    }

    /**
     * Test action perform when the message is unavailable starts an activity to view the inbox.
     */
    @Test
    public void testPerformMessageUnavailable() {
        action.perform(ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, "message_id"));

        when(mockInbox.getMessage("message_id")).thenReturn(null);

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedActivity();
        assertEquals("com.urbanairship.VIEW_RICH_PUSH_INBOX", startedIntent.getAction());
    }

    /**
     * Test action perform when the message is available it starts an activity to view the message.
     */
    @Test
    public void testPerformMessageAvailable() {
        RichPushMessage message = mock(RichPushMessage.class);
        when(message.getMessageId()).thenReturn("message_id");

        when(mockInbox.getMessage("message_id")).thenReturn(message);

        action.perform(ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, "message_id"));

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedActivity();
        assertEquals("com.urbanairship.VIEW_RICH_PUSH_MESSAGE", startedIntent.getAction());
        assertEquals("message:message_id", startedIntent.getDataString());
    }
}
