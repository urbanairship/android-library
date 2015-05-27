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
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.push.PushMessage;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushManager;
import com.urbanairship.richpush.RichPushMessage;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowApplication;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class OpenRichPushInboxActionTest extends BaseTestCase {

    OpenRichPushInboxAction action;
    RichPushInbox mockInbox;

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
        addResolveInfoForAction("com.urbanairship.VIEW_RICH_PUSH_INBOX", null);

        action.perform(ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, null));

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedActivity();
        assertEquals("com.urbanairship.VIEW_RICH_PUSH_INBOX", startedIntent.getAction());
    }

    /**
     * Test action perform when the message is unavailable starts an activity to view the inbox.
     */
    @Test
    public void testPerformMessageUnavailable() {
        addResolveInfoForAction("com.urbanairship.VIEW_RICH_PUSH_INBOX", null);

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
        addResolveInfoForAction("com.urbanairship.VIEW_RICH_PUSH_MESSAGE", "message:message_id");

        RichPushMessage message = mock(RichPushMessage.class);
        when(message.getMessageId()).thenReturn("message_id");

        when(mockInbox.getMessage("message_id")).thenReturn(message);

        action.perform(ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, "message_id"));

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedActivity();
        assertEquals("com.urbanairship.VIEW_RICH_PUSH_MESSAGE", startedIntent.getAction());
        assertEquals("message:message_id", startedIntent.getDataString());
    }

    /**
     * Test falling back to the landing page when the application does not handle com.urbanairship.VIEW_RICH_PUSH_MESSAGE
     * intent action.
     */
    @Test
    public void testFallbackLandingPage() {
        addResolveInfoForAction("com.urbanairship.actions.SHOW_LANDING_PAGE_INTENT_ACTION", "message:message_id");

        RichPushMessage message = mock(RichPushMessage.class);
        when(message.getMessageId()).thenReturn("message_id");

        when(mockInbox.getMessage("message_id")).thenReturn(message);

        action.perform(ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, "message_id"));

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedActivity();
        assertEquals("com.urbanairship.actions.SHOW_LANDING_PAGE_INTENT_ACTION", startedIntent.getAction());
        assertEquals("message:message_id", startedIntent.getDataString());
    }

    /**
     * Test "auto" placeholder looks for the message's ID in the push message metadata.
     */
    @Test
    public void testPerformMessageIdPlaceHolderPushMetadata() {
        addResolveInfoForAction("com.urbanairship.VIEW_RICH_PUSH_MESSAGE", "message:the_message_id");

        RichPushMessage message = mock(RichPushMessage.class);
        when(message.getMessageId()).thenReturn("the_message_id");
        when(mockInbox.getMessage("the_message_id")).thenReturn(message);

        Bundle pushBundle = new Bundle();
        pushBundle.putString(RichPushManager.RICH_PUSH_KEY, "the_message_id");
        Bundle metadata = new Bundle();
        metadata.putParcelable(ActionArguments.PUSH_MESSAGE_METADATA, new PushMessage(pushBundle));

        action.perform(ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, "auto", metadata));

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedActivity();
        assertEquals("com.urbanairship.VIEW_RICH_PUSH_MESSAGE", startedIntent.getAction());
        assertEquals("message:the_message_id", startedIntent.getDataString());
    }

    /**
     * Test "auto" placeholder looks for the message's ID in the rich push message ID metadata.
     */
    @Test
    public void testPerformMessageIdPlaceHolderRichPushMessageMetadata() {
        addResolveInfoForAction("com.urbanairship.VIEW_RICH_PUSH_MESSAGE", "message:the_message_id");

        RichPushMessage message = mock(RichPushMessage.class);
        when(message.getMessageId()).thenReturn("the_message_id");
        when(mockInbox.getMessage("the_message_id")).thenReturn(message);

        Bundle metadata = new Bundle();
        metadata.putString(ActionArguments.RICH_PUSH_ID_METADATA, "the_message_id");

        action.perform(ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, "auto", metadata));

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedActivity();
        assertEquals("com.urbanairship.VIEW_RICH_PUSH_MESSAGE", startedIntent.getAction());
        assertEquals("message:the_message_id", startedIntent.getDataString());
    }

    /**
     * Test "auto" placeholder will fail to find the message ID if no metadata is available
     * and tries to view the inbox instead.
     */
    @Test
    public void testPerformMessageIdPlaceHolderNoMetadata() {
        addResolveInfoForAction("com.urbanairship.VIEW_RICH_PUSH_MESSAGE", "message:the_message_id");
        addResolveInfoForAction("com.urbanairship.VIEW_RICH_PUSH_INBOX", null);

        action.perform(ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, "auto"));

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedActivity();
        assertEquals("com.urbanairship.VIEW_RICH_PUSH_INBOX", startedIntent.getAction());
    }

    /**
     * Adds resolve info for an intent to the package manager. Allows us to simulate that a given intent
     * is capable of starting for the activity.
     * @param action The intent's action.
     * @param data Optional intent data.
     */
    void addResolveInfoForAction(String action, String data) {
        ResolveInfo info = new ResolveInfo();
        info.isDefault = true;
        info.activityInfo = new ActivityInfo();
        info.activityInfo.name = action;
        info.activityInfo.applicationInfo = new ApplicationInfo();
        info.activityInfo.applicationInfo.packageName = RuntimeEnvironment.application.getPackageName();

        Intent intent = new Intent(action)
                .setPackage(RuntimeEnvironment.application.getPackageName());

        if (data != null) {
            intent.setData(Uri.parse(data));
        }

        RuntimeEnvironment.getRobolectricPackageManager().addResolveInfoForIntent(intent, info);
    }
}
