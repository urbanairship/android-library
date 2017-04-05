/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.richpush;

import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;
import com.urbanairship.TestRequest;
import com.urbanairship.UAirship;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.job.Job;
import com.urbanairship.push.PushManager;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.Assert.assertNull;
import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class InboxJobHandlerTest extends BaseTestCase {

    private RichPushInbox inbox;

    private InboxJobHandler jobHandler;

    private List<TestRequest> requests;
    private Map<String, Response> responses;

    private TestResultReceiver resultReceiver;
    private PushManager mockPushManager;

    private RichPushUser user;
    private PreferenceDataStore dataStore;

    @Before
    public void setup() {
        user = UAirship.shared().getInbox().getUser();

        inbox = mock(RichPushInbox.class);
        when(inbox.getUser()).thenReturn(user);
        TestApplication.getApplication().setInbox(inbox);

        dataStore = TestApplication.getApplication().preferenceDataStore;
        resultReceiver = new TestResultReceiver();
        requests = new ArrayList<>();
        responses = new HashMap();

        RequestFactory requestFactory = new RequestFactory() {
            @NonNull
            public Request createRequest(String requestMethod, URL url) {
                TestRequest request = new TestRequest();
                request.setURL(url);
                request.setRequestMethod(requestMethod);
                requests.add(request);

                if (responses.containsKey(url.toString())) {
                    request.response =responses.get(url.toString());
                }

                return request;
            }
        };

        mockPushManager = Mockito.mock(PushManager.class);
        TestApplication.getApplication().setPushManager(mockPushManager);

        // Clear any user or password
        user.setUser(null, null);

        jobHandler = new InboxJobHandler(UAirship.shared(),
                TestApplication.getApplication().preferenceDataStore,
                requestFactory, mock(RichPushResolver.class));
    }
    

    /**
     * Test when user has not been created returns an error code.
     */
    @Test
    public void testUserNotCreated() {
        // Clear any user or password
        user.setUser(null, null);

        Job job = Job.newBuilder(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                .putExtra(InboxJobHandler.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver)
                .build();

        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(job));

        // Verify result receiver
        assertEquals("Should return an error code", InboxJobHandler.STATUS_RICH_PUSH_UPDATE_ERROR,
                resultReceiver.lastResultCode);

        // Verify no requests were made
        assertEquals(0, requests.size());
    }

    /**
     * Test updateMessages returns error code when response is null.
     */
    @Test
    public void testUpdateMessagesNull() {
        // set a valid user
        user.setUser("fakeUserId", "password");

        // Set the last refresh time
        dataStore.put(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 300l);

        // Null response
        responses.put("https://device-api.urbanairship.com/api/user/fakeUserId/messages/", null);

        Job job = Job.newBuilder(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                     .putExtra(InboxJobHandler.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver)
                     .build();

        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(job));

        // Verify result receiver
        assertEquals("Should return an error code", InboxJobHandler.STATUS_RICH_PUSH_UPDATE_ERROR,
                resultReceiver.lastResultCode);

        // Verify the request
        TestRequest testRequest = requests.get(0);
        assertEquals("GET", testRequest.getRequestMethod());
        assertEquals("https://device-api.urbanairship.com/api/user/fakeUserId/messages/", testRequest.getURL().toString());
        assertEquals(300l, testRequest.getIfModifiedSince());

        // Verify LAST_MESSAGE_REFRESH_TIME was not updated
        assertEquals(300l, dataStore.getLong(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 0));
    }

    /**
     * Test updateMessages returns success code when response is HTTP_NOT_MODIFIED.
     */
    @Test
    public void testUpdateMessagesNotModified() {
        // Set a valid user
        user.setUser("fakeUserId", "password");

        // Set the last refresh time
        dataStore.put(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 300l);

        // Return a 304 response
        responses.put("https://device-api.urbanairship.com/api/user/fakeUserId/messages/",
                new Response.Builder(HttpURLConnection.HTTP_NOT_MODIFIED).create());

        Job job = Job.newBuilder(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                     .putExtra(InboxJobHandler.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver)
                     .build();

        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(job));

        // Verify result receiver
        assertEquals("Should return a success code", InboxJobHandler.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);

        // Verify the request
        TestRequest testRequest = requests.get(0);
        assertEquals("GET", testRequest.getRequestMethod());
        assertEquals("https://device-api.urbanairship.com/api/user/fakeUserId/messages/", testRequest.getURL().toString());
        assertEquals(300l, testRequest.getIfModifiedSince());

        // Verify LAST_MESSAGE_REFRESH_TIME was not updated
        assertEquals(300l, dataStore.getLong(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 0));
    }

    /**
     * Test that the inbox is updated when the response doesn't contain any messages.
     */
    @Test
    public void testUpdateMessagesEmpty() {
        // Set a valid user
        user.setUser("fakeUserId", "password");

        // Set a channel ID
        when(mockPushManager.getChannelId()).thenReturn("channelID");

        // Set the last refresh time
        dataStore.put(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 300l);

        // Return a 200 message list response with messages
        responses.put("https://device-api.urbanairship.com/api/user/fakeUserId/messages/",
                new Response.Builder(HttpURLConnection.HTTP_OK)
                        .setResponseMessage("OK")
                        .setLastModified(600l)
                        .setResponseBody("{ \"messages\": []}")
                        .create());

        Job job = Job.newBuilder(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                     .putExtra(InboxJobHandler.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver)
                     .build();

        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(job));

        // Verify result receiver
        assertEquals("Should return a success code", InboxJobHandler.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);

        // Verify the request method and url
        TestRequest testRequest = requests.get(0);
        assertEquals("GET", testRequest.getRequestMethod());
        assertEquals("https://device-api.urbanairship.com/api/user/fakeUserId/messages/", testRequest.getURL().toString());
        assertEquals(300l, testRequest.getIfModifiedSince());
        assertEquals("channelID", testRequest.getRequestHeaders().get("X-UA-Channel-ID"));

        // Verify LAST_MESSAGE_REFRESH_TIME was updated
        assertEquals(600l, dataStore.getLong(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 0));

        // Verify we updated the inbox
        verify(inbox).refresh(true);
    }

    /**
     * Test updateMessages returns success code when response is HTTP_OK.
     */
    @Test
    public void testUpdateMessages() {
        // Set a valid user
        user.setUser("fakeUserId", "password");

        // Set a channel ID
        when(mockPushManager.getChannelId()).thenReturn("channelID");

        // Set the last refresh time
        dataStore.put(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 300l);

        // Return a 200 message list response with messages
        responses.put("https://device-api.urbanairship.com/api/user/fakeUserId/messages/",
                new Response.Builder(HttpURLConnection.HTTP_OK)
                        .setResponseMessage("OK")
                        .setLastModified(600l)
                        .setResponseBody("{ \"messages\": [ {\"message_id\": \"some_mesg_id\"," +
                                "\"message_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/\"," +
                                "\"message_body_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/body/\"," +
                                "\"message_read_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/read/\"," +
                                "\"unread\": true, \"message_sent\": \"2010-09-05 12:13 -0000\"," +
                                "\"title\": \"Message title\", \"extra\": { \"some_key\": \"some_value\"}," +
                                "\"content_type\": \"text/html\", \"content_size\": \"128\"}]}")
                        .create());

        Job job = Job.newBuilder(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                     .putExtra(InboxJobHandler.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver)
                     .build();

        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(job));


        // Verify result receiver
        assertEquals("Should return a success code", InboxJobHandler.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);

        // Verify the request method and url
        TestRequest testRequest = requests.get(0);
        assertEquals("GET", testRequest.getRequestMethod());
        assertEquals("https://device-api.urbanairship.com/api/user/fakeUserId/messages/", testRequest.getURL().toString());
        assertEquals(300l, testRequest.getIfModifiedSince());
        assertEquals("channelID", testRequest.getRequestHeaders().get("X-UA-Channel-ID"));

        // Verify LAST_MESSAGE_REFRESH_TIME was updated
        assertEquals(600l, dataStore.getLong(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 0));

        // Verify we updated the inbox
        verify(inbox).refresh(true);
    }

    /**
     * Test updateMessages returns error code when response is HTTP_INTERNAL_ERROR
     */
    @Test
    public void testUpdateMessagesServerError() {
        // Set a valid user
        user.setUser("fakeUserId", "password");

        // Set the last refresh time
        dataStore.put(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 300l);

        // Return a 500 internal server error
        responses.put("https://device-api.urbanairship.com/api/user/fakeUserId/messages/",
                new Response.Builder(HttpURLConnection.HTTP_INTERNAL_ERROR)
                        .setResponseBody("{ failed }")
                .create());

        Job job = Job.newBuilder(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                     .putExtra(InboxJobHandler.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver)
                     .build();

        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(job));


        // Verify result receiver
        assertEquals("Should return an error code", InboxJobHandler.STATUS_RICH_PUSH_UPDATE_ERROR,
                resultReceiver.lastResultCode);

        // Verify the request method and url
        TestRequest testRequest = requests.get(0);
        assertEquals("GET", testRequest.getRequestMethod());
        assertEquals("https://device-api.urbanairship.com/api/user/fakeUserId/messages/", testRequest.getURL().toString());
        assertEquals(300l, testRequest.getIfModifiedSince());

        // Verify LAST_MESSAGE_REFRESH_TIME was not updated
        assertEquals(300l, dataStore.getLong(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 0));
    }


    @Test
    public void testSyncReadMessageState() {
        // Set a valid user
        user.setUser("fakeUserId", "password");

        // Set the last refresh time
        dataStore.put(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 300l);

        // Return a 500 internal server error
        responses.put("https://device-api.urbanairship.com/api/user/fakeUserId/messages/",
                new Response.Builder(HttpURLConnection.HTTP_INTERNAL_ERROR)
                        .setResponseBody("{ failed }")
                        .create());

        Job job = Job.newBuilder(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                     .putExtra(InboxJobHandler.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver)
                     .build();

        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(job));

        // Verify result receiver
        assertEquals("Should return an error code", InboxJobHandler.STATUS_RICH_PUSH_UPDATE_ERROR,
                resultReceiver.lastResultCode);

        // Verify the request method and url
        TestRequest testRequest = requests.get(0);
        assertEquals("GET", testRequest.getRequestMethod());
        assertEquals("https://device-api.urbanairship.com/api/user/fakeUserId/messages/", testRequest.getURL().toString());
        assertEquals(300l, testRequest.getIfModifiedSince());

        // Verify LAST_MESSAGE_REFRESH_TIME was not updated
        assertEquals(300l, dataStore.getLong(InboxJobHandler.LAST_MESSAGE_REFRESH_TIME, 0));
    }

    /**
     * Test create user when PushManager has a amazon channel.
     */
    @Test
    public void testCreateUserWithAmazonChannel() throws IOException {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);
        when(mockPushManager.getChannelId()).thenReturn("ba7beaaf-b6e9-416c-a1f9-a6ff5a81f588");

        responses.put("https://device-api.urbanairship.com/api/user/",
                new Response.Builder(HttpURLConnection.HTTP_CREATED)
                .setResponseMessage("Created")
                .setResponseBody("{ \"user_id\": \"someUserId\", \"password\": \"someUserToken\" }")
                .create());


        Job job = Job.newBuilder(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE)
                     .putExtra(InboxJobHandler.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver)
                     .build();

        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(job));


        // Verify user name and user token was set
        assertEquals("someUserId", user.getId());
        assertEquals("someUserToken", user.getPassword());

        // Verify result receiver
        assertEquals("Should return success code", InboxJobHandler.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);

        // Verify the request
        assertEquals(requests.size(), 1);
        assertEquals("POST", requests.get(0).getRequestMethod());
        assertEquals("https://device-api.urbanairship.com/api/user/", requests.get(0).getURL().toString());
        assertEquals("{\"amazon_channels\":[\"ba7beaaf-b6e9-416c-a1f9-a6ff5a81f588\"]}", requests.get(0).getRequestBody());
        assertEquals("application/vnd.urbanairship+json; version=3;", requests.get(0).getRequestHeaders().get("Accept"));
    }

    /**
     * Test create user when PushManager has a android channel.
     */
    @Test
    public void testCreateUserWithAndroidChannel() throws IOException {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);
        when(mockPushManager.getChannelId()).thenReturn("ba7beaaf-b6e9-416c-a1f9-a6ff5a81f588");

        responses.put("https://device-api.urbanairship.com/api/user/",
                new Response.Builder(HttpURLConnection.HTTP_CREATED)
                        .setResponseMessage("Created")
                        .setResponseBody("{ \"user_id\": \"someUserId\", \"password\": \"someUserToken\" }")
                        .create());


        Job job = Job.newBuilder(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE)
                     .putExtra(InboxJobHandler.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver)
                     .build();

        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(job));

        // Verify user name and user token was set
        assertEquals("someUserId", user.getId());
        assertEquals("someUserToken", user.getPassword());

        // Verify result receiver
        assertEquals("Should return success code", InboxJobHandler.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);

        // Verify the request
        assertEquals(requests.size(), 1);
        assertEquals("POST", requests.get(0).getRequestMethod());
        assertEquals("https://device-api.urbanairship.com/api/user/", requests.get(0).getURL().toString());
        assertEquals("{\"android_channels\":[\"ba7beaaf-b6e9-416c-a1f9-a6ff5a81f588\"]}", requests.get(0).getRequestBody());
        assertEquals("application/vnd.urbanairship+json; version=3;", requests.get(0).getRequestHeaders().get("Accept"));
    }

    /**
     * Test create user when PushManager when a channel has not been created.
     */
    @Test
    public void testCreateUserNoChannel() throws IOException {
        when(mockPushManager.getChannelId()).thenReturn(null);

        responses.put("https://device-api.urbanairship.com/api/user/",
                new Response.Builder(HttpURLConnection.HTTP_CREATED)
                        .setResponseMessage("Created")
                        .setResponseBody("{ \"user_id\": \"someUserId\", \"password\": \"someUserToken\" }")
                        .create());


        Job job = Job.newBuilder(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE)
                     .putExtra(InboxJobHandler.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver)
                     .build();

        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(job));

        // Verify we did not create the user
        assertNull(user.getId());
        assertNull(user.getPassword());
    }

    /**
     * Test create user failed.
     */
    @Test
    public void testCreateUserFailed() {
        when(mockPushManager.getChannelId()).thenReturn("ba7beaaf-b6e9-416c-a1f9-a6ff5a81f588");

        // Set a error response
        responses.put("https://device-api.urbanairship.com/api/user/",
                new Response.Builder(HttpURLConnection.HTTP_INTERNAL_ERROR).create());

        Job job = Job.newBuilder(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE)
                     .putExtra(InboxJobHandler.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver)
                     .build();

        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(job));

        // Verify we did not create the user
        assertNull(user.getId());
        assertNull(user.getPassword());

        // Verify result receiver
        assertEquals(InboxJobHandler.STATUS_RICH_PUSH_UPDATE_ERROR, resultReceiver.lastResultCode);
    }

    /**
     * Test user update on amazon.
     */
    @Test
    public void testUpdateUserAmazon() throws IOException {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);
        when(mockPushManager.getChannelId()).thenReturn("ba7beaaf-b6e9-416c-a1f9-a6ff5a81f588");

        // Set a user
        user.setUser("someUserId", "someUserToken");

        // Set a successful response
        responses.put("https://device-api.urbanairship.com/api/user/someUserId/",
                new Response.Builder(HttpURLConnection.HTTP_OK)
                        .setResponseMessage("OK")
                        .setResponseBody("{ \"ok\" }")
                        .create());

        Job job = Job.newBuilder(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE)
                     .putExtra(InboxJobHandler.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver)
                     .build();

        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(job));

        // Verify result receiver
        assertEquals("Should return success code", InboxJobHandler.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);

        // Verify the request
        assertEquals(requests.size(), 1);
        assertEquals("POST", requests.get(0).getRequestMethod());
        assertEquals("https://device-api.urbanairship.com/api/user/someUserId/", requests.get(0).getURL().toString());
        assertEquals("{\"amazon_channels\":{\"add\":[\"ba7beaaf-b6e9-416c-a1f9-a6ff5a81f588\"]}}", requests.get(0).getRequestBody());
        assertEquals("application/vnd.urbanairship+json; version=3;", requests.get(0).getRequestHeaders().get("Accept"));
    }

    /**
     * Test user update on android.
     */
    @Test
    public void testUpdateUserAndroid() throws IOException {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);
        when(mockPushManager.getChannelId()).thenReturn("ba7beaaf-b6e9-416c-a1f9-a6ff5a81f588");

        // Set a user
        user.setUser("someUserId", "someUserToken");

        // Set a successful response
        responses.put("https://device-api.urbanairship.com/api/user/someUserId/",
                new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\" }")
                .create());

        Job job = Job.newBuilder(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE)
                     .putExtra(InboxJobHandler.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver)
                     .build();

        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(job));

        // Verify result receiver
        assertEquals("Should return success code", InboxJobHandler.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);

        // Verify the request
        assertEquals(requests.size(), 1);
        assertEquals("POST", requests.get(0).getRequestMethod());
        assertEquals("https://device-api.urbanairship.com/api/user/someUserId/", requests.get(0).getURL().toString());
        assertEquals("{\"android_channels\":{\"add\":[\"ba7beaaf-b6e9-416c-a1f9-a6ff5a81f588\"]}}", requests.get(0).getRequestBody());
        assertEquals("application/vnd.urbanairship+json; version=3;", requests.get(0).getRequestHeaders().get("Accept"));
    }

    /**
     * Test user update without a channel should not update.
     */
    @Test
    public void testUpdateUserNoChannel() throws IOException {
        // Set a user
        user.setUser("someUserId", "someUserToken");

        // Return a null channel
        when(mockPushManager.getChannelId()).thenReturn(null);

        Job job = Job.newBuilder(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE)
                     .putExtra(InboxJobHandler.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver)
                     .build();

        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(job));

        // Verify result receiver
        assertEquals("Should return error code", InboxJobHandler.STATUS_RICH_PUSH_UPDATE_ERROR,
                resultReceiver.lastResultCode);
    }

    /**
     * Test user update failed request.
     */
    @Test
    public void testUpdateUserRequestFail() throws IOException, JSONException {
        // Set a user
        user.setUser("someUserId", "someUserToken");

        // Set a error response
        responses.put("https://device-api.urbanairship.com/api/user/someUserId/",
                new Response.Builder(HttpURLConnection.HTTP_INTERNAL_ERROR).create());

        Job job = Job.newBuilder(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE)
                     .putExtra(InboxJobHandler.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver)
                     .build();

        assertEquals(Job.JOB_FINISHED, jobHandler.performJob(job));

        // Verify result receiver
        assertEquals("Should return error code", InboxJobHandler.STATUS_RICH_PUSH_UPDATE_ERROR,
                resultReceiver.lastResultCode);
    }

    class TestResultReceiver extends ResultReceiver {

        public Bundle lastResultData;
        public int lastResultCode;

        public TestResultReceiver() {
            super(new Handler());
        }

        @Override
        public void onReceiveResult(int resultCode, Bundle resultData) {
            this.lastResultCode = resultCode;
            this.lastResultData = resultData;
        }
    }


}
