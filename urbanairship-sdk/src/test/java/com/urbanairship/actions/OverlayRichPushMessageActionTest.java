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
import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.push.PushMessage;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushMessage;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.shadows.ShadowApplication;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class OverlayRichPushMessageActionTest extends BaseTestCase {

    private OverlayRichPushMessageAction action;

    private @Action.Situation int[] acceptedSituations;
    private @Action.Situation int[] rejectedSituations;
    private RichPushInbox mockInbox;

    @Before
    public void setup() {
        action = new OverlayRichPushMessageAction();

        // Accepted situations (All - PUSH_RECEIVED - BACKGROUND_NOTIFICATION_ACTION_BUTTON)
        acceptedSituations = new int[] {
                Action.SITUATION_PUSH_OPENED,
                Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION,
                Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON
        };

        // Rejected situations (All - accepted)
        rejectedSituations = new int[] {
                Action.SITUATION_PUSH_RECEIVED,
                Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON
        };

        mockInbox = mock(RichPushInbox.class);
        TestApplication.getApplication().setInbox(mockInbox);
    }

    /**
     * Test accepts arguments with a message ID.
     */
    @Test
    public void testAcceptsArgumentMessageId() {
        for (@Action.Situation int situation : acceptedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, "the_message_id");
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }

        for (@Action.Situation int situation : rejectedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, "the_message_id");
            assertFalse("Should reject arguments in situation " + situation,
                    action.acceptsArguments(args));
        }
    }

    /**
     * Test accepts arguments with "auto" placeholder when the push message metadata is available.
     */
    @Test
    public void testAcceptsArgumentsWithPlaceHolderPushMessageMetadata() {

        Bundle metadata = new Bundle();
        metadata.putParcelable(ActionArguments.PUSH_MESSAGE_METADATA, new PushMessage(new Bundle()));

        for (@Action.Situation int situation : acceptedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, "auto", metadata);
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }

        for (@Action.Situation int situation : rejectedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, "auto", metadata);
            assertFalse("Should reject arguments in situation " + situation,
                    action.acceptsArguments(args));
        }
    }

    /**
     * Test accepts arguments with "auto" placeholder when the rich push message ID metadata is available.
     */
    @Test
    public void testAcceptsArgumentsWithPlaceHolderMessageIdMetadata() {

        Bundle metadata = new Bundle();
        metadata.putString(ActionArguments.RICH_PUSH_ID_METADATA, "the_message_id");

        for (@Action.Situation int situation : acceptedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, "auto", metadata);
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }

        for (@Action.Situation int situation : rejectedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, "auto", metadata);
            assertFalse("Should reject arguments in situation " + situation,
                    action.acceptsArguments(args));
        }
    }

    /**
     * Test rejects null argument value.
     */
    @Test
    public void testRejectsNullArgumentValue() {
        for (@Action.Situation int situation : acceptedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, null);
            assertFalse("Should reject null argument value in situation " + situation,
                    action.acceptsArguments(args));
        }
    }

    /**
     * Test rejects null argument value.
     */
    @Test
    public void testRejectsPlaceHolderWithNoMetadata() {
        for (@Action.Situation int situation : acceptedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, "auto");
            assertFalse("Should reject MESSAGE_ID when no metadata is available in situation " + situation,
                    action.acceptsArguments(args));
        }
    }

    /**
     * Test perform with a message ID start a landing page with the message's ID.
     */
    @Test
    public void testPerformMessageId() {
        RichPushMessage message = mock(RichPushMessage.class);
        when(message.getMessageId()).thenReturn("the_message_id");

        when(mockInbox.getMessage("the_message_id")).thenReturn(message);

        action.perform(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "the_message_id"));

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedActivity();
        assertEquals("com.urbanairship.actions.SHOW_LANDING_PAGE_INTENT_ACTION", startedIntent.getAction());
        assertEquals("message:the_message_id", startedIntent.getDataString());
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

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedActivity();
        assertEquals("com.urbanairship.actions.SHOW_LANDING_PAGE_INTENT_ACTION", startedIntent.getAction());
        assertEquals("message:the_message_id", startedIntent.getDataString());
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

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedActivity();
        assertEquals("com.urbanairship.actions.SHOW_LANDING_PAGE_INTENT_ACTION", startedIntent.getAction());
        assertEquals("message:the_message_id", startedIntent.getDataString());
    }
}