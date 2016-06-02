/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.richpush;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.TestRequest;
import com.urbanairship.UAirship;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.push.PushManager;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static junit.framework.Assert.assertNull;
import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;

public class UserServiceDelegateTest extends BaseTestCase {

    private TestResultReceiver resultReceiver;
    private PushManager mockPushManager;
    private RichPushUser richPushuser;
    private UserServiceDelegate serviceDelegate;
    private TestRequest testRequest;

    @Before
    public void setup() {
        testRequest = new TestRequest();

        RequestFactory requestFactory = new RequestFactory() {
            public Request createRequest(String requestMethod, URL url) {
                testRequest.setURL(url);
                testRequest.setRequestMethod(requestMethod);
                return testRequest;
            }
        };

        mockPushManager = Mockito.mock(PushManager.class);

        TestApplication.getApplication().setPushManager(mockPushManager);

        resultReceiver = new TestResultReceiver();
        richPushuser = UAirship.shared().getInbox().getUser();
        // Clear any user or password
        richPushuser.setUser(null, null);

        serviceDelegate = new UserServiceDelegate(TestApplication.getApplication(),
                TestApplication.getApplication().preferenceDataStore,
                requestFactory,
                UAirship.shared());
    }

    /**
     * Test create user when PushManager has a amazon channel.
     */
    @Test
    public void testCreateUserWithAmazonChannel() throws IOException {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);
        when(mockPushManager.getChannelId()).thenReturn("ba7beaaf-b6e9-416c-a1f9-a6ff5a81f588");

        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_CREATED)
                .setResponseMessage("Created")
                .setResponseBody("{ \"user_id\": \"someUserId\", \"password\": \"someUserToken\" }")
                .create();

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_USER_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        serviceDelegate.onHandleIntent(intent);

        // Verify user name and user token was set
        assertEquals("someUserId", richPushuser.getId());
        assertEquals("someUserToken", richPushuser.getPassword());

        // Verify result receiver
        assertEquals("Should return success code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);

        // Verify the request
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://device-api.urbanairship.com/api/user/", testRequest.getURL().toString());
        assertEquals("{\"amazon_channels\":[\"ba7beaaf-b6e9-416c-a1f9-a6ff5a81f588\"]}", testRequest.getRequestBody());
        assertEquals("application/vnd.urbanairship+json; version=3;", testRequest.getRequestHeaders().get("Accept"));
    }

    /**
     * Test create user when PushManager has a android channel.
     */
    @Test
    public void testCreateUserWithAndroidChannel() throws IOException {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);
        when(mockPushManager.getChannelId()).thenReturn("ba7beaaf-b6e9-416c-a1f9-a6ff5a81f588");

        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_CREATED)
                .setResponseMessage("Created")
                .setResponseBody("{ \"user_id\": \"someUserId\", \"password\": \"someUserToken\" }")
                .create();

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_USER_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        serviceDelegate.onHandleIntent(intent);

        // Verify user name and user token was set
        assertEquals("someUserId", richPushuser.getId());
        assertEquals("someUserToken", richPushuser.getPassword());

        // Verify result receiver
        assertEquals("Should return success code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);

        // Verify the request
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://device-api.urbanairship.com/api/user/", testRequest.getURL().toString());
        assertEquals("{\"android_channels\":[\"ba7beaaf-b6e9-416c-a1f9-a6ff5a81f588\"]}", testRequest.getRequestBody());
        assertEquals("application/vnd.urbanairship+json; version=3;", testRequest.getRequestHeaders().get("Accept"));
    }

    /**
     * Test create user when PushManager when a channel has not been created.
     */
    @Test
    public void testCreateUserNoChannel() throws IOException {
        when(mockPushManager.getChannelId()).thenReturn(null);

        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_CREATED)
                .setResponseMessage("Created")
                .setResponseBody("{ \"user_id\": \"someUserId\", \"password\": \"someUserToken\" }")
                .create();

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_USER_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        serviceDelegate.onHandleIntent(intent);

        // Verify we did not create the user
        assertNull(richPushuser.getId());
        assertNull(richPushuser.getPassword());
    }

    /**
     * Test create user failed.
     */
    @Test
    public void testCreateUserFailed() {
        when(mockPushManager.getChannelId()).thenReturn("ba7beaaf-b6e9-416c-a1f9-a6ff5a81f588");

        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_INTERNAL_ERROR).create();

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_USER_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        serviceDelegate.onHandleIntent(intent);

        // Verify we did not create the user
        assertNull(richPushuser.getId());
        assertNull(richPushuser.getPassword());

        // Verify result receiver
        assertEquals(RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_ERROR, resultReceiver.lastResultCode);
    }

    /**
     * Test user update on amazon.
     */
    @Test
    public void testUpdateUserAmazon() throws IOException {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);
        when(mockPushManager.getChannelId()).thenReturn("ba7beaaf-b6e9-416c-a1f9-a6ff5a81f588");

        // Set a user
        richPushuser.setUser("someUserId", "someUserToken");

        // Set a successful response
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\" }")
                .create();

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_USER_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        serviceDelegate.onHandleIntent(intent);

        // Verify result receiver
        assertEquals("Should return success code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);

        // Verify the request
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://device-api.urbanairship.com/api/user/someUserId/", testRequest.getURL().toString());
        assertEquals("{\"amazon_channels\":{\"add\":[\"ba7beaaf-b6e9-416c-a1f9-a6ff5a81f588\"]}}", testRequest.getRequestBody());
        assertEquals("application/vnd.urbanairship+json; version=3;", testRequest.getRequestHeaders().get("Accept"));
    }

    /**
     * Test user update on android.
     */
    @Test
    public void testUpdateUserAndroid() throws IOException {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);
        when(mockPushManager.getChannelId()).thenReturn("ba7beaaf-b6e9-416c-a1f9-a6ff5a81f588");

        // Set a user
        richPushuser.setUser("someUserId", "someUserToken");

        // Set a successful response
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\" }")
                .create();

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_USER_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        serviceDelegate.onHandleIntent(intent);

        // Verify result receiver
        assertEquals("Should return success code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);

        // Verify the request
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://device-api.urbanairship.com/api/user/someUserId/", testRequest.getURL().toString());
        assertEquals("{\"android_channels\":{\"add\":[\"ba7beaaf-b6e9-416c-a1f9-a6ff5a81f588\"]}}", testRequest.getRequestBody());
        assertEquals("application/vnd.urbanairship+json; version=3;", testRequest.getRequestHeaders().get("Accept"));
    }

    /**
     * Test user update without a channel should not update.
     */
    @Test
    public void testUpdateUserNoChannel() throws IOException {
        // Set a user
        richPushuser.setUser("someUserId", "someUserToken");

        // Return a null channel
        when(mockPushManager.getChannelId()).thenReturn(null);

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_USER_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        serviceDelegate.onHandleIntent(intent);

        // Verify result receiver
        assertEquals("Should return error code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_ERROR,
                resultReceiver.lastResultCode);
    }

    /**
     * Test user update failed request.
     */
    @Test
    public void testUpdateUserRequestFail() throws IOException, JSONException {
        // Set a user
        richPushuser.setUser("someUserId", "someUserToken");

        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_INTERNAL_ERROR).create();

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_USER_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        serviceDelegate.onHandleIntent(intent);

        // Verify result receiver
        assertEquals("Should return error code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_ERROR,
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
