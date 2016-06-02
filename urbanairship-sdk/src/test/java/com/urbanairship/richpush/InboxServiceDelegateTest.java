/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.richpush;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;
import com.urbanairship.TestRequest;
import com.urbanairship.UAirship;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.push.PushManager;

import org.junit.Before;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class InboxServiceDelegateTest extends BaseTestCase {

    private TestResultReceiver resultReceiver;

    private RichPushInbox inbox;

    private InboxServiceDelegate serviceDelegate;

    private List<TestRequest> requests;
    private Map<String, Response> responses;
    private PreferenceDataStore dataStore;

    @Before
    public void setup() {
        resultReceiver = new TestResultReceiver();
        requests = new ArrayList<>();
        responses = new HashMap();

        RequestFactory requestFactory = new RequestFactory() {
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

        dataStore = TestApplication.getApplication().preferenceDataStore;

        PushManager pushManager = mock(PushManager.class);
        when(pushManager.getChannelId()).thenReturn("channelID");
        TestApplication.getApplication().setPushManager(pushManager);

        inbox = mock(RichPushInbox.class);
        TestApplication.getApplication().setInbox(inbox);

        RichPushUser user = new RichPushUser(dataStore);
        user.setUser("fakeUserId", "fakeUserToken");
        when(inbox.getUser()).thenReturn(user);

        RichPushResolver resolver = mock(RichPushResolver.class);

        serviceDelegate = new InboxServiceDelegate(TestApplication.getApplication(),
                dataStore,
                requestFactory,
                resolver,
                UAirship.shared());
    }

    /**
     * Test when user has not been created returns an error code.
     */
    @Test
    public void testUserNotCreated() {
        // Clear any user or password
        inbox.getUser().setUser(null, null);

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        serviceDelegate.onHandleIntent(intent);

        // Verify result receiver
        assertEquals("Should return an error code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_ERROR,
                resultReceiver.lastResultCode);

        // Verify no requests were made
        assertEquals(0, requests.size());
    }

    /**
     * Test updateMessages returns error code when response is null.
     */
    @Test
    public void testUpdateMessagesNull() {
        // Set the last refresh time
        dataStore.put(RichPushUpdateService.LAST_MESSAGE_REFRESH_TIME, 300l);

        // Null response
        responses.put("https://device-api.urbanairship.com/api/user/fakeUserId/messages/", null);

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        serviceDelegate.onHandleIntent(intent);

        // Verify result receiver
        assertEquals("Should return an error code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_ERROR,
                resultReceiver.lastResultCode);

        // Verify the request
        TestRequest testRequest = requests.get(0);
        assertEquals("GET", testRequest.getRequestMethod());
        assertEquals("https://device-api.urbanairship.com/api/user/fakeUserId/messages/", testRequest.getURL().toString());
        assertEquals(300l, testRequest.getIfModifiedSince());

        // Verify LAST_MESSAGE_REFRESH_TIME was not updated
        assertEquals(300l, dataStore.getLong(RichPushUpdateService.LAST_MESSAGE_REFRESH_TIME, 0));
    }

    /**
     * Test updateMessages returns success code when response is HTTP_NOT_MODIFIED.
     */
    @Test
    public void testUpdateMessagesNotModified() {
        // Set the last refresh time
        dataStore.put(RichPushUpdateService.LAST_MESSAGE_REFRESH_TIME, 300l);

        // Return a 304 response
        responses.put("https://device-api.urbanairship.com/api/user/fakeUserId/messages/",
                new Response.Builder(HttpURLConnection.HTTP_NOT_MODIFIED).create());

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        serviceDelegate.onHandleIntent(intent);

        // Verify result receiver
        assertEquals("Should return a success code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);

        // Verify the request
        TestRequest testRequest = requests.get(0);
        assertEquals("GET", testRequest.getRequestMethod());
        assertEquals("https://device-api.urbanairship.com/api/user/fakeUserId/messages/", testRequest.getURL().toString());
        assertEquals(300l, testRequest.getIfModifiedSince());

        // Verify LAST_MESSAGE_REFRESH_TIME was not updated
        assertEquals(300l, dataStore.getLong(RichPushUpdateService.LAST_MESSAGE_REFRESH_TIME, 0));
    }

    /**
     * Test that the inbox is updated when the response doesn't contain any messages.
     */
    @Test
    public void testUpdateMessagesEmpty() {
        // Set the last refresh time
        dataStore.put(RichPushUpdateService.LAST_MESSAGE_REFRESH_TIME, 300l);

        // Return a 200 message list response with messages
        responses.put("https://device-api.urbanairship.com/api/user/fakeUserId/messages/",
                new Response.Builder(HttpURLConnection.HTTP_OK)
                        .setResponseMessage("OK")
                        .setLastModified(600l)
                        .setResponseBody("{ \"messages\": []}")
                        .create());

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        serviceDelegate.onHandleIntent(intent);

        // Verify result receiver
        assertEquals("Should return a success code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);

        // Verify the request method and url
        TestRequest testRequest = requests.get(0);
        assertEquals("GET", testRequest.getRequestMethod());
        assertEquals("https://device-api.urbanairship.com/api/user/fakeUserId/messages/", testRequest.getURL().toString());
        assertEquals(300l, testRequest.getIfModifiedSince());
        assertEquals("channelID", testRequest.getRequestHeaders().get("X-UA-Channel-ID"));

        // Verify LAST_MESSAGE_REFRESH_TIME was updated
        assertEquals(600l, dataStore.getLong(RichPushUpdateService.LAST_MESSAGE_REFRESH_TIME, 0));

        // Verify we updated the inbox
        verify(inbox).refresh(true);
    }

    /**
     * Test updateMessages returns success code when response is HTTP_OK.
     */
    @Test
    public void testUpdateMessages() {
        // Set the last refresh time
        dataStore.put(RichPushUpdateService.LAST_MESSAGE_REFRESH_TIME, 300l);

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

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        serviceDelegate.onHandleIntent(intent);

        // Verify result receiver
        assertEquals("Should return a success code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);

        // Verify the request method and url
        TestRequest testRequest = requests.get(0);
        assertEquals("GET", testRequest.getRequestMethod());
        assertEquals("https://device-api.urbanairship.com/api/user/fakeUserId/messages/", testRequest.getURL().toString());
        assertEquals(300l, testRequest.getIfModifiedSince());
        assertEquals("channelID", testRequest.getRequestHeaders().get("X-UA-Channel-ID"));

        // Verify LAST_MESSAGE_REFRESH_TIME was updated
        assertEquals(600l, dataStore.getLong(RichPushUpdateService.LAST_MESSAGE_REFRESH_TIME, 0));

        // Verify we updated the inbox
        verify(inbox).refresh(true);
    }

    /**
     * Test updateMessages returns error code when response is HTTP_INTERNAL_ERROR
     */
    @Test
    public void testUpdateMessagesServerError() {
        // Set the last refresh time
        dataStore.put(RichPushUpdateService.LAST_MESSAGE_REFRESH_TIME, 300l);

        // Return a 500 internal server error
        responses.put("https://device-api.urbanairship.com/api/user/fakeUserId/messages/",
                new Response.Builder(HttpURLConnection.HTTP_INTERNAL_ERROR)
                        .setResponseBody("{ failed }")
                .create());

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        serviceDelegate.onHandleIntent(intent);

        // Verify result receiver
        assertEquals("Should return an error code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_ERROR,
                resultReceiver.lastResultCode);

        // Verify the request method and url
        TestRequest testRequest = requests.get(0);
        assertEquals("GET", testRequest.getRequestMethod());
        assertEquals("https://device-api.urbanairship.com/api/user/fakeUserId/messages/", testRequest.getURL().toString());
        assertEquals(300l, testRequest.getIfModifiedSince());

        // Verify LAST_MESSAGE_REFRESH_TIME was not updated
        assertEquals(300l, dataStore.getLong(RichPushUpdateService.LAST_MESSAGE_REFRESH_TIME, 0));
    }


    @Test
    public void testSyncReadMessageState() {
        // Set the last refresh time
        dataStore.put(RichPushUpdateService.LAST_MESSAGE_REFRESH_TIME, 300l);

        // Return a 500 internal server error
        responses.put("https://device-api.urbanairship.com/api/user/fakeUserId/messages/",
                new Response.Builder(HttpURLConnection.HTTP_INTERNAL_ERROR)
                        .setResponseBody("{ failed }")
                        .create());

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        serviceDelegate.onHandleIntent(intent);

        // Verify result receiver
        assertEquals("Should return an error code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_ERROR,
                resultReceiver.lastResultCode);

        // Verify the request method and url
        TestRequest testRequest = requests.get(0);
        assertEquals("GET", testRequest.getRequestMethod());
        assertEquals("https://device-api.urbanairship.com/api/user/fakeUserId/messages/", testRequest.getURL().toString());
        assertEquals(300l, testRequest.getIfModifiedSince());

        // Verify LAST_MESSAGE_REFRESH_TIME was not updated
        assertEquals(300l, dataStore.getLong(RichPushUpdateService.LAST_MESSAGE_REFRESH_TIME, 0));
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
