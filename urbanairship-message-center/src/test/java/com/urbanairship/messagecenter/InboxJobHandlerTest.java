/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.Context;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.UAirship;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobInfo;
import com.urbanairship.job.JobResult;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertFalse;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class InboxJobHandlerTest {

    private Inbox inbox;

    private InboxJobHandler jobHandler;

    private AirshipChannel mockChannel;
    private MessageDao mockMessageDao;
    private InboxApiClient mockInboxApiClient;

    private User user;
    private PreferenceDataStore dataStore;
    private TestUserListener userListener;
    private TestAirshipRuntimeConfig runtimeConfig;

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        dataStore = PreferenceDataStore.inMemoryStore(context);
        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        mockChannel = Mockito.mock(AirshipChannel.class);
        mockMessageDao = Mockito.mock(MessageDao.class);

        userListener = new TestUserListener();
        user = new User(dataStore, mockChannel);
        user.addListener(userListener);

        inbox = mock(Inbox.class);
        when(inbox.getUser()).thenReturn(user);

        mockInboxApiClient = mock(InboxApiClient.class);

        // Clear any user or password
        user.setUser(null, null);

        jobHandler = new InboxJobHandler(inbox, user, mockChannel, dataStore,
                mockMessageDao, mockInboxApiClient);
    }

    /**
     * Test when user has not been created returns an error code.
     */
    @Test
    public void testUserNotCreated() throws RequestException {
        // Clear any user or password
        user.setUser(null, null);

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                                 .build();

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo));

        // Verify result receiver
        verify(inbox).onUpdateMessagesFinished(false);

        // Verify no requests were made
        verify(mockInboxApiClient, never()).createUser(anyString());
    }

    /**
     * Test updateMessages returns success code when response is HTTP_NOT_MODIFIED.
     */
    @Test
    public void testUpdateMessagesNotModified() throws RequestException {
        // Set a valid user
        user.setUser("fakeUserId", "password");

        // Set a channel ID
        when(mockChannel.getId()).thenReturn("channelId");

        // Set the last refresh time
        dataStore.put(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 300L);

        // Return a 304 response
        when(mockInboxApiClient.fetchMessages(user, "channelId", 300L))
                .thenReturn(new Response.Builder<JsonList>(HttpURLConnection.HTTP_NOT_MODIFIED).build());

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                                 .build();

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo));

        // Verify result receiver
        verify(inbox).onUpdateMessagesFinished(true);

        // Verify LAST_MESSAGE_REFRESH_TIME was not updated
        assertEquals(300L, dataStore.getLong(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 0));
    }

    /**
     * Test that the inbox is updated when the response doesn't contain any messages.
     */
    @Test
    public void testUpdateMessagesEmpty() throws RequestException, JsonException {
        // Set a valid user
        user.setUser("fakeUserId", "password");

        // Set a channel ID
        when(mockChannel.getId()).thenReturn("channelId");

        // Set the last refresh time
        dataStore.put(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 300L);

        String responseBody = "{ \"messages\": []}";

        // Return a 200 message list response with messages
        when(mockInboxApiClient.fetchMessages(user, "channelId", 300L))
                .thenReturn(new Response.Builder<JsonList>(HttpURLConnection.HTTP_OK)
                        .setLastModified(600L)
                        .setResponseBody(responseBody)
                        .setResult(JsonValue.parseString(responseBody).optMap().opt("messages").getList())
                        .build());

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                                 .build();

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo));

        // Verify result receiver
        verify(inbox).onUpdateMessagesFinished(true);

        // Verify LAST_MESSAGE_REFRESH_TIME was updated
        assertEquals(600L, dataStore.getLong(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 0));

        // Verify we updated the inbox
        verify(inbox).refresh(true);
    }

    /**
     * Test updateMessages returns success code when response is HTTP_OK.
     */
    @Test
    public void testUpdateMessages() throws RequestException, JsonException {
        // Set a valid user
        user.setUser("fakeUserId", "password");

        // Set a channel ID
        when(mockChannel.getId()).thenReturn("channelId");

        // Set the last refresh time
        dataStore.put(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 300L);

        String responseBody = "{ \"messages\": [ {\"message_id\": \"some_mesg_id\"," +
                "\"message_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/\"," +
                "\"message_body_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/body/\"," +
                "\"message_read_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/read/\"," +
                "\"unread\": true, \"message_sent\": \"2010-09-05 12:13 -0000\"," +
                "\"title\": \"Message title\", \"extra\": { \"some_key\": \"some_value\"}," +
                "\"content_type\": \"text/html\", \"content_size\": \"128\"}]}";

        // Return a 200 message list response with messages
        when(mockInboxApiClient.fetchMessages(user, "channelId", 300L))
                .thenReturn(new Response.Builder<JsonList>(HttpURLConnection.HTTP_OK)
                        .setLastModified(600L)
                        .setResponseBody(responseBody)
                        .setResult(JsonValue.parseString(responseBody).optMap().opt("messages").getList())
                        .build());

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                                 .build();

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo));

        // Verify result receiver
        verify(inbox).onUpdateMessagesFinished(true);

        // Verify LAST_MESSAGE_REFRESH_TIME was updated
        assertEquals(600L, dataStore.getLong(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 0));

        // Verify we updated the inbox
        verify(inbox).refresh(true);
    }

    /**
     * Test updateMessages returns error code when response is HTTP_INTERNAL_ERROR
     */
    @Test
    public void testUpdateMessagesServerError() throws RequestException {
        // Set a valid user
        user.setUser("fakeUserId", "password");

        // Set a channel ID
        when(mockChannel.getId()).thenReturn("channelId");

        // Set the last refresh time
        dataStore.put(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 300L);

        // Return a 500 internal server error
        when(mockInboxApiClient.fetchMessages(user, "channelId", 300L))
                .thenReturn(new Response.Builder<JsonList>(HttpURLConnection.HTTP_INTERNAL_ERROR)
                        .setResponseBody("{ failed }")
                        .build());

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                                 .build();

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo));

        // Verify result receiver
        verify(inbox).onUpdateMessagesFinished(false);

        // Verify LAST_MESSAGE_REFRESH_TIME was not updated
        assertEquals(300L, dataStore.getLong(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 0));

        // Verify we updated the inbox
        verify(inbox).refresh(true);
    }

    @Test
    public void testSyncDeletedMessageStateServerError() throws RequestException, JsonException {
        // Set a valid user
        user.setUser("fakeUserId", "password");

        // Set a channel ID
        when(mockChannel.getId()).thenReturn("channelId");

        // Set the last refresh time
        dataStore.put(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 300L);

        String responseBody = "{ \"messages\": []}";

        // Return a 200 message list response with messages
        when(mockInboxApiClient.fetchMessages(user, "channelId", 300L))
                .thenReturn(new Response.Builder<JsonList>(HttpURLConnection.HTTP_OK)
                        .setLastModified(300L)
                        .setResponseBody(responseBody)
                        .setResult(JsonValue.parseString(responseBody).optMap().opt("messages").getList())
                        .build());

        ArrayList<String> idsToDelete = new ArrayList<>();
        Message messageToDelete = createFakeMessage("id1", false, true);
        Message messageToDelete2 = createFakeMessage("id2", false, true);
        ArrayList<MessageEntity> messageCollection = new ArrayList<>();
        List<JsonValue> reportingsToDelete = new ArrayList<>();
        messageCollection.add(MessageEntity.createMessageFromPayload(messageToDelete.getMessageId(), messageToDelete.getRawMessageJson()));
        messageCollection.add(MessageEntity.createMessageFromPayload(messageToDelete2.getMessageId(), messageToDelete2.getRawMessageJson()));

        for (MessageEntity message : messageCollection) {
            reportingsToDelete.add(message.getMessageReporting());
            idsToDelete.add(message.getMessageId());
        }

        when(mockMessageDao.getLocallyDeletedMessages()).thenReturn(messageCollection);

        // Return a 500 internal server error
        when(mockInboxApiClient.syncDeletedMessageState(user, "channelId", reportingsToDelete))
                .thenReturn(new Response.Builder<Void>(HttpURLConnection.HTTP_INTERNAL_ERROR)
                        .setResponseBody("{ failed }")
                        .build());

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                                 .build();

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo));

        verify(mockMessageDao, never()).deleteMessages(idsToDelete);

        // Verify LAST_MESSAGE_REFRESH_TIME was not updated
        assertEquals(300L, dataStore.getLong(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 0));
    }

    @Test
    public void testSyncDeletedMessageStateSucceeds() throws RequestException, JsonException {
        // Set a valid user
        user.setUser("fakeUserId", "password");

        // Set a channel ID
        when(mockChannel.getId()).thenReturn("channelId");

        // Set the last refresh time
        dataStore.put(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 300L);

        String responseBody = "{ \"messages\": []}";

        // Return a 200 message list response with messages
        when(mockInboxApiClient.fetchMessages(user, "channelId", 300L))
                .thenReturn(new Response.Builder<JsonList>(HttpURLConnection.HTTP_OK)
                        .setLastModified(300L)
                        .setResponseBody(responseBody)
                        .setResult(JsonValue.parseString(responseBody).optMap().opt("messages").getList())
                        .build());

        ArrayList<String> idsToDelete = new ArrayList<>();
        List<JsonValue> reportingsToDelete = new ArrayList<>();
        Message messageToDelete = createFakeMessage("id1", false, true);
        Message messageToDelete2 = createFakeMessage("id2", false, true);
        ArrayList<MessageEntity> messagesToDelete = new ArrayList<>();
        messagesToDelete.add(MessageEntity.createMessageFromPayload(messageToDelete.getMessageId(), messageToDelete.getRawMessageJson()));
        messagesToDelete.add(MessageEntity.createMessageFromPayload(messageToDelete2.getMessageId(), messageToDelete2.getRawMessageJson()));

        for (MessageEntity message : messagesToDelete) {
            reportingsToDelete.add(message.getMessageReporting());
            idsToDelete.add(message.getMessageId());
        }
        when(mockMessageDao.getLocallyDeletedMessages()).thenReturn(messagesToDelete);

        // Return a 200 message list response with messages
        when(mockInboxApiClient.syncDeletedMessageState(user, "channelId", reportingsToDelete))
                .thenReturn(new Response.Builder<Void>(HttpURLConnection.HTTP_OK)
                        .setResponseBody(responseBody)
                        .build());

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                                 .build();

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo));

        verify(mockMessageDao).deleteMessages(idsToDelete);

        // Verify LAST_MESSAGE_REFRESH_TIME was not updated
        assertEquals(300L, dataStore.getLong(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 0));
    }

    @Test
    public void testSyncReadMessageStateServerError() throws RequestException, JsonException {
        // Set a valid user
        user.setUser("fakeUserId", "password");

        // Set a channel ID
        when(mockChannel.getId()).thenReturn("channelId");

        // Set the last refresh time
        dataStore.put(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 300L);

        String responseBody = "{ \"messages\": []}";

        // Return a 200 message list response with messages
        when(mockInboxApiClient.fetchMessages(user, "channelId", 300L))
                .thenReturn(new Response.Builder<JsonList>(HttpURLConnection.HTTP_OK)
                        .setLastModified(300L)
                        .setResponseBody(responseBody)
                        .setResult(JsonValue.parseString(responseBody).optMap().opt("messages").getList())
                        .build());

        ArrayList<String> idsToUpdate = new ArrayList<>();
        List<JsonValue> reportingsToUpdate = new ArrayList<>();
        Message messageToUpdate = createFakeMessage("id1", false, false);
        Message messageToUpdate2 = createFakeMessage("id2", false, false);
        ArrayList<MessageEntity> messagesToUpdate = new ArrayList<>();
        messagesToUpdate.add(MessageEntity.createMessageFromPayload(messageToUpdate.getMessageId(), messageToUpdate.getRawMessageJson()));
        messagesToUpdate.add(MessageEntity.createMessageFromPayload(messageToUpdate2.getMessageId(), messageToUpdate2.getRawMessageJson()));

        for (MessageEntity message : messagesToUpdate) {
            reportingsToUpdate.add(message.getMessageReporting());
            idsToUpdate.add(message.getMessageId());
        }
        when(mockMessageDao.getLocallyReadMessages()).thenReturn(messagesToUpdate);

        // Return a 500 internal server error
        when(mockInboxApiClient.syncReadMessageState(user, "channelId", reportingsToUpdate))
                .thenReturn(new Response.Builder<Void>(HttpURLConnection.HTTP_INTERNAL_ERROR)
                        .setResponseBody("{ failed }")
                        .build());

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                                 .build();

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo));

        verify(mockMessageDao, never()).markMessagesReadOrigin(idsToUpdate);

        // Verify LAST_MESSAGE_REFRESH_TIME was not updated
        assertEquals(300L, dataStore.getLong(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 0));
    }

    @Test
    public void testSyncReadMessageStateSucceeds() throws RequestException, JsonException {
        // Set a valid user
        user.setUser("fakeUserId", "password");

        // Set a channel ID
        when(mockChannel.getId()).thenReturn("channelId");

        // Set the last refresh time
        dataStore.put(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 300L);

        String responseBody = "{ \"messages\": []}";

        // Return a 200 message list response with messages
        when(mockInboxApiClient.fetchMessages(user, "channelId", 300L))
                .thenReturn(new Response.Builder<JsonList>(HttpURLConnection.HTTP_OK)
                        .setLastModified(300L)
                        .setResponseBody(responseBody)
                        .setResult(JsonValue.parseString(responseBody).optMap().opt("messages").getList())
                        .build());

        ArrayList<String> idsToUpdate = new ArrayList<>();
        List<JsonValue> reportingsToUpdate = new ArrayList<>();
        Message messageToUpdate = createFakeMessage("id1", false, false);
        Message messageToUpdate2 = createFakeMessage("id2", false, false);
        ArrayList<MessageEntity> messagesToUpdate = new ArrayList<>();
        messagesToUpdate.add(MessageEntity.createMessageFromPayload(messageToUpdate.getMessageId(), messageToUpdate.getRawMessageJson()));


        for (MessageEntity message : messagesToUpdate) {
            reportingsToUpdate.add(message.getMessageReporting());
            idsToUpdate.add(message.getMessageId());
        }
        when(mockMessageDao.getLocallyReadMessages()).thenReturn(messagesToUpdate);

        // Return a 200 message list response with messages
        when(mockInboxApiClient.syncReadMessageState(user, "channelId", reportingsToUpdate))
                .thenReturn(new Response.Builder<Void>(HttpURLConnection.HTTP_OK)
                        .setResponseBody("{ \"messages\": []}")
                        .build());

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                                 .build();

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo));

        verify(mockMessageDao).markMessagesReadOrigin(idsToUpdate);

        // Verify LAST_MESSAGE_REFRESH_TIME was not updated
        assertEquals(300L, dataStore.getLong(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 0));
    }

    /**
     * Test create user when PushManager has a amazon channel.
     */
    @Test
    public void testCreateUserWithAmazonChannel() throws RequestException {
        runtimeConfig.setPlatform(UAirship.AMAZON_PLATFORM);
        when(mockChannel.getId()).thenReturn("channelId");

        String responseBody = "{ \"user_id\": \"someUserId\", \"password\": \"someUserToken\" }";

        when(mockInboxApiClient.createUser("channelId"))
                .thenReturn(new Response.Builder<UserCredentials>(HttpURLConnection.HTTP_CREATED)
                        .setResponseBody(responseBody)
                        .setResult(new UserCredentials("someUserId", "someUserToken"))
                        .build());

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE)
                                 .build();

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo));
        assertTrue(userListener.lastUpdateUserResult);

        // Verify user name and user token was set
        assertEquals("someUserId", user.getId());
        assertEquals("someUserToken", user.getPassword());
    }

    /**
     * Test create user when PushManager has a android channel.
     */
    @Test
    public void testCreateUserWithAndroidChannel() throws RequestException {
        runtimeConfig.setPlatform(UAirship.ANDROID_PLATFORM);
        when(mockChannel.getId()).thenReturn("channelId");

        String responseBody = "{ \"user_id\": \"someUserId\", \"password\": \"someUserToken\" }";

        when(mockInboxApiClient.createUser("channelId"))
                .thenReturn(new Response.Builder<UserCredentials>(HttpURLConnection.HTTP_CREATED)
                        .setResponseBody(responseBody)
                        .setResult(new UserCredentials("someUserId", "someUserToken"))
                        .build());

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE)
                                 .build();

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo));
        assertTrue(userListener.lastUpdateUserResult);

        // Verify user name and user token was set
        assertEquals("someUserId", user.getId());
        assertEquals("someUserToken", user.getPassword());
    }

    /**
     * Test create user when PushManager when a channel has not been created.
     */
    @Test
    public void testCreateUserNoChannel() {
        when(mockChannel.getId()).thenReturn(null);

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE)
                                 .build();

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo));
        assertFalse(userListener.lastUpdateUserResult);

        // Verify we did not create the user
        assertNull(user.getId());
        assertNull(user.getPassword());
    }

    /**
     * Test create user failed.
     */
    @Test
    public void testCreateUserFailed() throws RequestException {
        when(mockChannel.getId()).thenReturn("channelId");

        // Set a error response
        when(mockInboxApiClient.createUser("channelId"))
                .thenReturn(new Response.Builder<UserCredentials>(HttpURLConnection.HTTP_INTERNAL_ERROR).build());

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE)
                                 .build();

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo));
        assertFalse(userListener.lastUpdateUserResult);

        // Verify we did not create the user
        assertNull(user.getId());
        assertNull(user.getPassword());
    }

    /**
     * Test user update on amazon.
     */
    @Test
    public void testUpdateUserAmazon() throws RequestException {
        runtimeConfig.setPlatform(UAirship.AMAZON_PLATFORM);
        when(mockChannel.getId()).thenReturn("channelId");

        // Set a user
        user.setUser("someUserId", "someUserToken");

        // Set a successful response
        when(mockInboxApiClient.updateUser(user,"channelId"))
                .thenReturn(new Response.Builder<Void>(HttpURLConnection.HTTP_OK)
                        .setResponseBody("{ \"ok\" }")
                        .build());

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE)
                                 .build();

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo));
        assertTrue(userListener.lastUpdateUserResult);
    }

    /**
     * Test user update on android.
     */
    @Test
    public void testUpdateUserAndroid() throws RequestException {
        runtimeConfig.setPlatform(UAirship.ANDROID_PLATFORM);
        when(mockChannel.getId()).thenReturn("channelId");

        // Set a user
        user.setUser("someUserId", "someUserToken");

        // Set a successful response
        when(mockInboxApiClient.updateUser(user,"channelId"))
                .thenReturn(new Response.Builder<Void>(HttpURLConnection.HTTP_OK)
                        .setResponseBody("{ \"ok\" }")
                        .build());

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE)
                                 .build();

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo));
        assertTrue(userListener.lastUpdateUserResult);
    }

    /**
     * Test user update without a channel should not update.
     */
    @Test
    public void testUpdateUserNoChannel() {
        // Set a user
        user.setUser("someUserId", "someUserToken");

        // Return a null channel
        when(mockChannel.getId()).thenReturn(null);

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE)
                                 .build();

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo));
        assertFalse(userListener.lastUpdateUserResult);
    }

    /**
     * Test user update failed request.
     */
    @Test
    public void testUpdateUserRequestFail() throws RequestException {
        // Set a user
        user.setUser("someUserId", "someUserToken");

        when(mockChannel.getId()).thenReturn("channelId");

        // Set a error response
        when(mockInboxApiClient.updateUser(user,"channelId"))
                .thenReturn(new Response.Builder<Void>(HttpURLConnection.HTTP_INTERNAL_ERROR).build());

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE)
                                 .build();

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo));
        assertFalse(userListener.lastUpdateUserResult);
    }

    /**
     * Test user is recreated on unauthorized response from update.
     */
    @Test
    public void testUpdateUserRequestUnauthorizedRecreatesUser() throws RequestException {
        String unauthorizedUserId = "unauthorizedUserId";
        String unauthorizedToken = "unauthorizedToken";
        String recreatedUserId = "recreatedUserId";
        String recreatedToken = "recreatedToken";

        String channelId = "channelId";

        // Set a user
        user.setUser(unauthorizedUserId, unauthorizedToken);

        when(mockChannel.getId()).thenReturn(channelId);

        // Set error response for user update
        when(mockInboxApiClient.updateUser(user, channelId))
                .thenReturn(new Response.Builder<Void>(HttpURLConnection.HTTP_UNAUTHORIZED).build());

        // Set success response for user create
        UserCredentials result = new UserCredentials(recreatedUserId, recreatedToken);
        String responseBody = String.format("{ \"user_id\": \"%s\", \"password\": \"%s\" }",
                recreatedUserId, recreatedToken);
        when(mockInboxApiClient.createUser(channelId))
                .thenReturn(new Response.Builder<UserCredentials>(HttpURLConnection.HTTP_CREATED)
                        .setResponseBody(responseBody)
                        .setResult(result)
                        .build());

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE)
                                 .build();

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo));
        assertTrue(userListener.lastUpdateUserResult);

        // Verify user name and user token was set
        assertEquals(recreatedUserId, user.getId());
        assertEquals(recreatedToken, user.getPassword());

        // Sanity check requests were made as expected
        InOrder inOrder = inOrder(mockInboxApiClient);
        inOrder.verify(mockInboxApiClient, times(1)).updateUser(any(User.class), eq(channelId));
        inOrder.verify(mockInboxApiClient, times(1)).createUser(channelId);
        inOrder.verifyNoMoreInteractions();
    }

    private Message createFakeMessage(String messageId, boolean unread, boolean deleted) throws JsonException {
        JsonValue messageJson = JsonValue.parseString("{\"message_id\": \"" + messageId + "\"," +
                "\"message_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/\"," +
                "\"message_body_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/body/\"," +
                "\"message_read_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/read/\"," +
                "\"message_reporting\": {\n" +
                "                 \"message_id\": \"" + messageId + "\"" +
                "               }," +
                "\"unread\": true, \"message_sent\": \"2010-09-05 12:13 -0000\"," +
                "\"title\": \"Message title\", \"extra\": { \"some_key\": \"some_value\"}," +
                "\"content_type\": \"text/html\", \"content_size\": \"128\"}");
        return Message.create(messageJson, unread, deleted);
    }

    /**
     * Listener that captures the last update user result
     */
    private class TestUserListener implements User.Listener {

        Boolean lastUpdateUserResult = null;

        @Override
        public void onUserUpdated(boolean success) {
            lastUpdateUserResult = success;
        }

    }

}
