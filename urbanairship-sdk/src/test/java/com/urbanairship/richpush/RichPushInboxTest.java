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

package com.urbanairship.richpush;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;

import com.urbanairship.BaseTestCase;
import com.urbanairship.Cancelable;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplication;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class RichPushInboxTest extends BaseTestCase {

    RichPushInbox inbox;
    ShadowApplication application;

    @Before
    public void setUp() {
        RichPushUser user = mock(RichPushUser.class);
        Context context = RuntimeEnvironment.application;
        RichPushResolver resolver = new RichPushResolver(context);
        Executor executor = new Executor() {
            @Override
            public void execute(Runnable runnable) {
                runnable.run();
            }
        };

        inbox = new RichPushInbox(context, user, resolver, executor);

        // Populate the MCRAP database with 10 messages
        for (int i = 0; i < 10; i++) {
            RichPushTestUtils.insertMessage(String.valueOf(i + 1) + "_message_id");
        }

        inbox.refresh();

        application = Shadows.shadowOf(RuntimeEnvironment.application);
        application.clearStartedServices();
    }

    /**
     * Tests the inbox reports the correct
     * number of messages that it was set up
     * with
     */
    @Test
    public void testNewRichPushInbox() {
        assertEquals(10, inbox.getCount());
        assertEquals(10, inbox.getUnreadCount());
        assertEquals(0, inbox.getReadCount());
    }

    /**
     * Test mark messages are marked deleted in the database
     * and the inbox.
     */
    @Test
    public void testMarkMessagesDeleted() throws JSONException, InterruptedException {
        assertEquals(10, inbox.getCount());

        HashSet<String> deletedIds = new HashSet<>();
        deletedIds.add("1_message_id");
        deletedIds.add("3_message_id");
        deletedIds.add("6_message_id");

        inbox.deleteMessages(deletedIds);

        // Should have 3 less messages
        assertEquals(7, this.inbox.getCount());
        assertEquals(7, this.inbox.getUnreadCount());
        assertEquals(0, this.inbox.getReadCount());

        for (String deletedId : deletedIds) {
            assertFalse(inbox.getMessageIds().contains(deletedId));
        }
    }

    /**
     * Test mark messages are marked read in the database
     * and the inbox.
     */
    @Test
    public void testMarkMessagesRead() throws InterruptedException {
        HashSet<String> markedReadIds = new HashSet<>();
        markedReadIds.add("1_message_id");
        markedReadIds.add("3_message_id");
        markedReadIds.add("6_message_id");

        inbox.markMessagesRead(markedReadIds);

        assertEquals(3, inbox.getReadCount());

        // Should have 3 read messages
        assertEquals(10, inbox.getCount());
        assertEquals(7, inbox.getUnreadCount());
        assertEquals(3, inbox.getReadCount());

        Map<String, RichPushMessage> readMessages = createIdToMessageMap(inbox.getReadMessages());
        Map<String, RichPushMessage> unreadMessages = createIdToMessageMap(inbox.getUnreadMessages());

        // Verify the read message are in the right lists
        for (String readId : markedReadIds) {
            assertTrue(readMessages.containsKey(readId));
            assertFalse(unreadMessages.containsKey(readId));
        }
    }

    /**
     * Test mark messages are marked unread in the database
     * and the inbox.
     */
    @Test
    public void testMarkMessagesUnread() throws InterruptedException {
        HashSet<String> messageIds = new HashSet<>();
        messageIds.add("1_message_id");
        messageIds.add("3_message_id");
        messageIds.add("6_message_id");

        // Mark messages read
        inbox.markMessagesRead(messageIds);

        assertEquals(3, inbox.getReadCount());
        assertEquals(7, inbox.getUnreadCount());

        // Mark messages as unread
        inbox.markMessagesUnread(messageIds);

        assertEquals(10, inbox.getCount());
        assertEquals(10, inbox.getUnreadCount());
        assertEquals(0, inbox.getReadCount());
    }

    /**
     * Test fetch messages starts the RichPushUpdateService.
     */
    @Test
    public void testFetchMessages() {
        inbox.fetchMessages();

        Intent intent = application.getNextStartedService();
        assertEquals(RichPushUpdateService.ACTION_RICH_PUSH_MESSAGES_UPDATE, intent.getAction());
    }


    /**
     * Test fetching messages skips triggering the rich push service if already
     * refreshing.
     */
    @Test
    public void testRefreshMessagesAlreadyRefreshing() {
        // Start refreshing messages
        inbox.fetchMessages();

        // Clear the services
        application.clearStartedServices();

        // Try to refresh again
        inbox.fetchMessages();

        // Verify a new service was not started
        assertNull(application.peekNextStartedService());
    }

    /**
     * Test fetching messages with a callback forcefully fetches the list.
     */
    @Test
    public void testRefreshMessageResponse() {
        RichPushInbox.FetchMessagesCallback callback = mock(RichPushInbox.FetchMessagesCallback.class);

        // Start refreshing messages
        inbox.fetchMessages();

        // Verify we started the service
        assertEquals(RichPushUpdateService.ACTION_RICH_PUSH_MESSAGES_UPDATE, application.getNextStartedService().getAction());

        // Force another update
        inbox.fetchMessages(callback);

        // Verify we started another service
        assertEquals(RichPushUpdateService.ACTION_RICH_PUSH_MESSAGES_UPDATE, application.getNextStartedService().getAction());
    }


    /**
     * Test fetch message request with a callback
     */
    @Test
    public void testRefreshMessagesWithCallback() {
        RichPushInbox.FetchMessagesCallback callback = mock(RichPushInbox.FetchMessagesCallback.class);

        inbox.fetchMessages(callback);

        // Send result to the receiver
        ResultReceiver receiver = application.peekNextStartedService()
                                             .getParcelableExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER);
        receiver.send(RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS, new Bundle());

        verify(callback).onFinished(true);
    }

    /**
     * Test failed fetch message request with a callback
     */
    @Test
    public void testFetchMessagesFailWithCallback() {
        RichPushInbox.FetchMessagesCallback callback = mock(RichPushInbox.FetchMessagesCallback.class);

        inbox.fetchMessages(callback);

        // Send result to the receiver
        ResultReceiver receiver = application.peekNextStartedService()
                                             .getParcelableExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER);
        receiver.send(RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_ERROR, new Bundle());

        verify(callback).onFinished(false);
    }

    /**
     * Test canceling the fetch message request with a callback
     */
    @Test
    public void testFetchMessagesCallbackCanceled() {
        RichPushInbox.FetchMessagesCallback callback = mock(RichPushInbox.FetchMessagesCallback.class);

        Cancelable cancelable = inbox.fetchMessages(callback);
        cancelable.cancel();

        // Send result to the receiver
        ResultReceiver receiver = application.peekNextStartedService()
                                             .getParcelableExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER);
        receiver.send(RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_ERROR, new Bundle());

        verifyZeroInteractions(callback);
    }

    /**
     * Helper method to convert a list of rich push messages
     * to a map of message ids to messages
     *
     * @param messages List of messages to convert
     * @return A map of rich push messages
     */
    private static Map<String, RichPushMessage> createIdToMessageMap(List<RichPushMessage> messages) {
        Map<String, RichPushMessage> messageMap = new HashMap<>();

        for (RichPushMessage message : messages) {
            messageMap.put(message.getMessageId(), message);
        }

        return messageMap;
    }
}