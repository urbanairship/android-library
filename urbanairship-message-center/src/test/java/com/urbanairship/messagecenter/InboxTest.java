/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.Context;

import com.urbanairship.Cancelable;
import com.urbanairship.Predicate;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.app.ApplicationListener;
import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.AirshipChannelListener;
import com.urbanairship.channel.ChannelRegistrationPayload;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.job.JobResult;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.SQLiteMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
@LooperMode(LooperMode.Mode.LEGACY)
public class InboxTest {

    private Inbox inbox;
    private Predicate<Message> testPredicate;
    private User mockUser;
    private JobDispatcher mockDispatcher;
    private AirshipChannel mockChannel;
    private MessageDao mockMessageDao;
    private ArrayList<MessageEntity> messageEntities;

    GlobalActivityMonitor spyActivityMonitor;

    @Before
    public void setUp() {
        mockDispatcher = mock(JobDispatcher.class);
        mockUser = mock(User.class);
        mockChannel = mock(AirshipChannel.class);
        mockMessageDao = mock(MessageDao.class);

        MessageCenterTestUtils.setup();

        Context context = ApplicationProvider.getApplicationContext();
        spyActivityMonitor = Mockito.spy(GlobalActivityMonitor.shared(context));

        Executor executor = new Executor() {
            @Override
            public void execute(Runnable runnable) {
                runnable.run();
            }
        };

        PreferenceDataStore dataStore = PreferenceDataStore.inMemoryStore(context);
        inbox = new Inbox(context, dataStore, mockDispatcher, mockUser, mockMessageDao, executor, spyActivityMonitor, mockChannel);
        inbox.setEnabled(true);

        messageEntities = new ArrayList<>();

        // Only the "even" messages
        testPredicate = new Predicate<Message>() {
            @Override
            public boolean apply(@NonNull Message message) {
                String substring = message.getMessageId().replace("_message_id", "");
                int index = Integer.parseInt(substring);
                return index % 2 == 0;
            }
        };

        // Populate the MCRAP database with 10 messages
        for (int i = 0; i < 10; i++) {
            Message message = MessageCenterTestUtils.createMessage(String.valueOf(i + 1) + "_message_id", null, false);
            messageEntities.add(MessageEntity.createMessageFromPayload(message.getMessageId(), message.getRawMessageJson()));
        }

        // Put some expired messages in there (these should not show up after refresh)
        for (int i = 10; i < 15; i++) {
            Message message = MessageCenterTestUtils.createMessage(String.valueOf(i + 1) + "_message_id", null, true);
            messageEntities.add(MessageEntity.createMessageFromPayload(message.getMessageId(), message.getRawMessageJson()));
        }

        when(mockMessageDao.getMessages()).thenReturn(messageEntities);
        inbox.refresh(false);
        Mockito.clearInvocations(mockMessageDao);
    }

    /**
     * Test init dispatches the user update job if necessary.
     */
    @Test
    public void testInitUserShouldUpdate() {
        when(mockUser.shouldUpdate()).thenReturn(true);

        inbox.init();

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE);
            }
        }));
    }

    /**
     * Test channel registration extender adds the user id.
     */
    @Test
    public void testChannelRegistrationDisabledTokenRegistration() {
        ArgumentCaptor<AirshipChannel.ChannelRegistrationPayloadExtender> argument = ArgumentCaptor.forClass(AirshipChannel.ChannelRegistrationPayloadExtender.class);
        inbox.init();
        verify(mockChannel).addChannelRegistrationPayloadExtender(argument.capture());

        AirshipChannel.ChannelRegistrationPayloadExtender extender = argument.getValue();
        assertNotNull(extender);

        when(mockUser.getId()).thenReturn("cool");

        ChannelRegistrationPayload.Builder builder = new ChannelRegistrationPayload.Builder();
        ChannelRegistrationPayload payload = extender.extend(builder).build();

        ChannelRegistrationPayload expected = new ChannelRegistrationPayload.Builder()
                .setUserId("cool")
                .build();

        assertEquals(expected, payload);
    }

    /**
     * Test channel creation updates the user.
     */
    @Test
    public void testChannelCreateUpdatesUser() {
        ArgumentCaptor<AirshipChannelListener> argument = ArgumentCaptor.forClass(AirshipChannelListener.class);
        inbox.init();
        verify(mockChannel).addChannelListener(argument.capture());
        AirshipChannelListener listener = argument.getValue();
        assertNotNull(listener);

        clearInvocations(mockDispatcher);

        listener.onChannelCreated("some-channel");

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE);
            }
        }));
    }

    /**
     * Test user updates refresh the inbox.
     */
    @Test
    public void testUserUpdateRefreshesInbox() {
        ArgumentCaptor<User.Listener> argument = ArgumentCaptor.forClass(User.Listener.class);
        inbox.init();

        verify(mockUser).addListener(argument.capture());
        User.Listener listener = argument.getValue();
        assertNotNull(listener);

        clearInvocations(mockDispatcher);

        listener.onUserUpdated(true);

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE);
            }
        }));
    }

    /**
     * Tests the inbox reports the correct
     * number of messages.
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
    public void testMarkMessagesDeleted() {
        assertEquals(10, inbox.getCount());

        Set<String> deletedIds = new HashSet<>();
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
    public void testMarkMessagesRead() {
        Set<String> markedReadIds = new HashSet<>();
        markedReadIds.add("1_message_id");
        markedReadIds.add("3_message_id");
        markedReadIds.add("6_message_id");

        inbox.markMessagesRead(markedReadIds);

        assertEquals(3, inbox.getReadCount());

        // Should have 3 read messages
        assertEquals(10, inbox.getCount());
        assertEquals(7, inbox.getUnreadCount());
        assertEquals(3, inbox.getReadCount());

        Map<String, Message> readMessages = createIdToMessageMap(inbox.getReadMessages());
        Map<String, Message> unreadMessages = createIdToMessageMap(inbox.getUnreadMessages());

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
    public void testMarkMessagesUnread() {
        Set<String> messageIds = new HashSet<>();
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
        Inbox.FetchMessagesCallback callback = mock(Inbox.FetchMessagesCallback.class);

        // Start refreshing messages
        inbox.fetchMessages();

        // Force another update
        inbox.fetchMessages(callback);

        // Verify we dispatched only 1 job
        verify(mockDispatcher, times(1)).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE) && jobInfo.getConflictStrategy() == JobInfo.REPLACE;
            }
        }));
    }

    /**
     * Test fetch message request with a callback
     */
    @Test
    public void testRefreshMessagesWithCallback() {
        Inbox.FetchMessagesCallback callback = mock(Inbox.FetchMessagesCallback.class);

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
        Inbox.FetchMessagesCallback callback = mock(Inbox.FetchMessagesCallback.class);

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
        Inbox.FetchMessagesCallback callback = mock(Inbox.FetchMessagesCallback.class);

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


        verifyNoInteractions(callback);
    }

    /**
     * Test getting messages with or without a predicate
     */
    @Test
    public void testGetMessages() {

        // regular style

        List<Message> messages = inbox.getMessages();
        Assert.assertEquals(messages.size(), inbox.getCount());

        // filtered style
        List<Message> filteredMessages = inbox.getMessages(testPredicate);

        Assert.assertEquals(filteredMessages.size(), inbox.getCount() / 2);
    }

    @Test
    public void testGetUnreadMessages() {
        Set<String> messageIds = new HashSet<>();
        messageIds.add("1_message_id");
        messageIds.add("2_message_id");
        messageIds.add("3_message_id");
        messageIds.add("4_message_id");
        // Mark messages read
        inbox.markMessagesRead(messageIds);

        List<Message> unreadMessages = inbox.getUnreadMessages();
        assertEquals(unreadMessages.size(), 6);

        List<Message> filteredMessages = inbox.getUnreadMessages(testPredicate);
        assertEquals(filteredMessages.size(), 3);

        for (Message message : filteredMessages) {
            String substring = message.getMessageId().replace("_message_id", "");
            int index = Integer.parseInt(substring);
            assertEquals(index % 2, 0);
        }
    }

    @Test
    public void testGetReadMessages() {
        Set<String> messageIds = new HashSet<>();
        messageIds.add("1_message_id");
        messageIds.add("2_message_id");
        messageIds.add("3_message_id");
        messageIds.add("4_message_id");

        // Mark messages read
        inbox.markMessagesRead(messageIds);

        List<Message> readMessages = inbox.getReadMessages();
        assertEquals(readMessages.size(), 4);

        List<Message> filteredMessages = inbox.getReadMessages(testPredicate);
        assertEquals(filteredMessages.size(), 2);

        for (Message message : filteredMessages) {
            String substring = message.getMessageId().replace("_message_id", "");
            int index = Integer.parseInt(substring);
            assertEquals(index % 2, 0);
        }
    }

    /**
     * Test init doesn't update the user or refresh if FEATURE_MESSAGE_CENTER is disabled.
     */
    @Test
    public void testInitWhenDisabledDispatchesNoJobs() {
        inbox.setEnabled(false);

        inbox.init();

        verify(mockDispatcher, never()).dispatch(any(JobInfo.class));
    }

    /**
     * Verify that calls to onPerformJob are no-ops if FEATURE_MESSAGE_CENTER is disabled.
     */
    @Test
    public void testOnPerformJobWhenDisabled() {
        InboxJobHandler jobHandler = mock(InboxJobHandler.class);

        inbox.setEnabled(false);
        inbox.inboxJobHandler = jobHandler;

        JobResult jobResult = inbox.onPerformJob(mock(UAirship.class), mock(JobInfo.class));
        assertEquals(JobResult.SUCCESS, jobResult);

        verify(jobHandler, never()).performJob(any(JobInfo.class));
    }

    /**
     * Verify updateEnabledState when disabled.
     */
    @Test
    public void testUpdateEnabledStateNotEnabled() {
        inbox.setEnabled(false);

        inbox.updateEnabledState();

        verify(mockMessageDao).deleteAllMessages();
        verify(spyActivityMonitor).removeApplicationListener(any(ApplicationListener.class));
        verify(mockChannel).removeChannelListener(any(AirshipChannelListener.class));
        verify(mockChannel).removeChannelRegistrationPayloadExtender(any(AirshipChannel.ChannelRegistrationPayloadExtender.class));
        verify(mockUser).removeListener(any(User.Listener.class));
    }

    /**
     * Verify updateEnabledState when enabled.
     */
    @Test
    public void testUpdateEnabledStateEnabled() {
        inbox.setEnabled(true);

        inbox.updateEnabledState();
        // Update again to make sure that we don't restart the Inbox if already started.
        inbox.updateEnabledState();

        // Verify that Inbox was started once.
        verify(mockUser).addListener(any(User.Listener.class));
        verify(mockMessageDao).getMessages();
        verify(spyActivityMonitor).addApplicationListener(any(ApplicationListener.class));
        verify(mockChannel).addChannelListener(any(AirshipChannelListener.class));
        verify(mockChannel).addChannelRegistrationPayloadExtender(any(AirshipChannel.ChannelRegistrationPayloadExtender.class));
    }

    /**
     * Helper method to convert a list of rich push messages
     * to a map of message ids to messages
     *
     * @param messages List of messages to convert
     * @return A map of rich push messages
     */
    private static Map<String, Message> createIdToMessageMap(List<Message> messages) {
        Map<String, Message> messageMap = new HashMap<>();

        for (Message message : messages) {
            messageMap.put(message.getMessageId(), message);
        }

        return messageMap;
    }

}
