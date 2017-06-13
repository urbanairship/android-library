/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.richpush;

import android.content.Context;

import com.urbanairship.ActivityMonitor;
import com.urbanairship.BaseTestCase;
import com.urbanairship.Cancelable;
import com.urbanairship.TestApplication;
import com.urbanairship.job.JobInfo;
import com.urbanairship.job.JobDispatcher;

import junit.framework.Assert;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.robolectric.RuntimeEnvironment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class RichPushInboxTest extends BaseTestCase {

    private RichPushInbox inbox;
    private RichPushInbox.Predicate testPredicate;
    private RichPushUser mockUser;
    private JobDispatcher mockDispatcher;

    @Before
    public void setUp() {
        mockDispatcher = mock(JobDispatcher.class);
        mockUser = mock(RichPushUser.class);

        Context context = RuntimeEnvironment.application;
        RichPushResolver resolver = new RichPushResolver(context);
        Executor executor = new Executor() {
            @Override
            public void execute(Runnable runnable) {
                runnable.run();
            }
        };

        inbox = new RichPushInbox(context, TestApplication.getApplication().preferenceDataStore, mockDispatcher, mockUser, resolver, executor, new ActivityMonitor());

        // Only the "even" messages
        testPredicate = new RichPushInbox.Predicate() {
            @Override
            public boolean apply(RichPushMessage message) {
                String substring = message.getMessageId().replace("_message_id", "");
                int index = Integer.parseInt(substring);
                return index % 2 == 0;
            }
        };

        // Populate the MCRAP database with 10 messages
        for (int i = 0; i < 10; i++) {
            RichPushTestUtils.insertMessage(String.valueOf(i + 1) + "_message_id");
        }

        // Put some expired messages in there (these should not show up after refresh)
        for (int i = 10; i< 15; i++) {
            RichPushTestUtils.insertMessage(String.valueOf(i + 1) + "_message_id", null, true);
        }

        inbox.refresh(false);
    }

    /**
     * Test init only updates the mockUser if it already exists. Normally the mockUser updates
     * after channel creation.
     */
    @Test
    public void testInitNoUser() {
        when(mockUser.getId()).thenReturn(null);

        inbox.init();

        verify(mockUser, never()).update(false);
    }

    /**
     * Test init only updates the mockUser if it exists.
     */
    @Test
    public void testInitWithUser() {
        when(mockUser.getId()).thenReturn("cool");

        inbox.init();

        verify(mockUser, times(1)).update(false);
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
     * Test fetch messages starts the AirshipService.
     */
    @Test
    public void testFetchMessages() {
        inbox.fetchMessages();

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE);
            }
        }));
    }


    /**
     * Test fetching messages skips triggering the rich push service if already
     * refreshing.
     */
    @Test
    public void testRefreshMessagesAlreadyRefreshing() {
        // Start refreshing messages
        inbox.fetchMessages();

        // Try to refresh again
        inbox.fetchMessages();

        // Verify only 1 job was dispatched to refresh the messages
        verify(mockDispatcher, times(1)).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE);
            }
        }));
    }

    /**
     * Test multiple fetch requests only performs a single request if the first one has yet to finish.
     */
    @Test
    public void testRefreshMessageResponse() {
        RichPushInbox.FetchMessagesCallback callback = mock(RichPushInbox.FetchMessagesCallback.class);

        // Start refreshing messages
        inbox.fetchMessages();

        // Force another update
        inbox.fetchMessages(callback);

        // Verify we dispatched only 1 job
        verify(mockDispatcher, times(1)).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE);
            }
        }));
    }


    /**
     * Test fetch message request with a callback
     */
    @Test
    public void testRefreshMessagesWithCallback() {
        RichPushInbox.FetchMessagesCallback callback = mock(RichPushInbox.FetchMessagesCallback.class);

        inbox.fetchMessages(callback);

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                if (!jobInfo.getAction().equals(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)) {
                    return false;
                }

                return true;
            }
        }));

        inbox.onUpdateMessagesFinished(true);

        verify(callback).onFinished(true);
    }

    /**
     * Test failed fetch message request with a callback
     */
    @Test
    public void testFetchMessagesFailWithCallback() {
        RichPushInbox.FetchMessagesCallback callback = mock(RichPushInbox.FetchMessagesCallback.class);

        inbox.fetchMessages(callback);

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                if (!jobInfo.getAction().equals(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)) {
                    return false;
                }

                return true;
            }
        }));

        inbox.onUpdateMessagesFinished(false);

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

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                if (!jobInfo.getAction().equals(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)) {
                    return false;
                }

                return true;
            }
        }));

        inbox.onUpdateMessagesFinished(false);

        verifyZeroInteractions(callback);
    }

    /**
     * Test getting messages with or without a predicate
     */
    @Test
    public void testGetMessages() {

        // regular style

        List<RichPushMessage> messages = inbox.getMessages();
        Assert.assertEquals(messages.size(), inbox.getCount());

        // filtered style
        List<RichPushMessage> filteredMessages = inbox.getMessages(testPredicate);

        Assert.assertEquals(filteredMessages.size(), inbox.getCount() / 2);
    }

    @Test
    public void testGetUnreadMessages() {

        HashSet<String> messageIds = new HashSet<>();
        messageIds.add("1_message_id");
        messageIds.add("2_message_id");
        messageIds.add("3_message_id");
        messageIds.add("4_message_id");

        // Mark messages read
        inbox.markMessagesRead(messageIds);


        List<RichPushMessage> unreadMessages = inbox.getUnreadMessages();
        Assert.assertEquals(unreadMessages.size(), 6);


        List<RichPushMessage> filteredMessages = inbox.getUnreadMessages(testPredicate);
        Assert.assertEquals(filteredMessages.size(), 3);

        for (RichPushMessage message : filteredMessages) {
            String substring = message.getMessageId().replace("_message_id", "");
            int index = Integer.parseInt(substring);
            Assert.assertEquals(index % 2,  0);
        }
    }

    @Test
    public void testGetReadMessages() {
        HashSet<String> messageIds = new HashSet<>();

        messageIds.add("1_message_id");
        messageIds.add("2_message_id");
        messageIds.add("3_message_id");
        messageIds.add("4_message_id");

        // Mark messages read
        inbox.markMessagesRead(messageIds);

        List<RichPushMessage> readMessages = inbox.getReadMessages();
        Assert.assertEquals(readMessages.size(), 4);

        List<RichPushMessage> filteredMessages= inbox.getReadMessages(testPredicate);
        Assert.assertEquals(filteredMessages.size(), 2);

        for (RichPushMessage message : filteredMessages) {
            String substring = message.getMessageId().replace("_message_id", "");
            int index = Integer.parseInt(substring);
            Assert.assertEquals(index % 2, 0);
        }
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
