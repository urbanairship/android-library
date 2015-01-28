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

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.push.PushManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.io.IOException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RichPushUpdateServiceTest extends RichPushBaseTestCase {

    private final String fakeUserId = "someUserId";
    private final String fakeUserToken = "someUserToken";
    private final String fakeChannelId = "ba7beaaf-b6e9-416c-a1f9-a6ff5a81f588";

    RichPushUpdateService updateService;
    AirshipConfigOptions options;
    TestResultReceiver resultReceiver;
    RichPushUserPreferences preferences;
    UserAPIClient mockClient;
    PushManager mockPushManager;

    RichPushManager richPushManager;

    @Override
    public void setUp() {
        super.setUp();

        mockClient = Mockito.mock(UserAPIClient.class);
        mockPushManager = Mockito.mock(PushManager.class);


        TestApplication.getApplication().setPushManager(mockPushManager);

        options = UAirship.shared().getAirshipConfigOptions();
        resultReceiver = new TestResultReceiver();

        updateService = new RichPushUpdateService() {
            @Override
            public void onHandleIntent(Intent intent) {
                super.onHandleIntent(intent);
            }
        };

        updateService.userClient = mockClient;

        richPushManager = UAirship.shared().getRichPushManager();
        preferences = richPushManager.getRichPushUser().preferences;
        // Clear any user or password
        preferences.setUserCredentials(null, null);


    }

    @After
    public void tearDown() {
        // Clear any user or password
        preferences.setUserCredentials(null, null);
    }

    /**
     * Test create user when PushManager has a amazon channel.
     */
    @Test
    public void testCreateUserWithAmazonChannel() throws IOException {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);
        when(mockPushManager.getChannelId()).thenReturn(fakeChannelId);

        // Set up user response
        UserResponse response = Mockito.mock(UserResponse.class);
        when(response.getUserId()).thenReturn(fakeUserId);
        when(response.getUserToken()).thenReturn(fakeUserToken);

        // Return the response
        when(mockClient.createUser(argThat(new ArgumentMatcher<JSONObject>() {
            @Override
            public boolean matches(Object argument) {
                JSONObject payload = (JSONObject) argument;
                JSONArray jsonArray = payload.optJSONArray("amazon_channels");

                try {
                    return jsonArray != null && jsonArray.length() == 1 && jsonArray.getString(0).equals(fakeChannelId);
                } catch (JSONException e) {
                    return false;
                }
            }
        }))).thenReturn(response);

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_USER_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        updateService.onHandleIntent(intent);

        // Verify user name and user token was set
        assertEquals("Should update the user name", richPushManager.getRichPushUser().getId(), fakeUserId);
        assertEquals("Should update the user token", richPushManager.getRichPushUser().getPassword(), fakeUserToken);

        // Verify result receiver
        assertEquals("Should return success code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);
    }


    /**
     * Test create user when PushManager has a android channel.
     */
    @Test
    public void testCreateUserWithAndroidChannel() throws IOException {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);
        when(mockPushManager.getChannelId()).thenReturn(fakeChannelId);

        // Set up user response
        UserResponse response = Mockito.mock(UserResponse.class);
        when(response.getUserId()).thenReturn(fakeUserId);
        when(response.getUserToken()).thenReturn(fakeUserToken);

        // Return the response
        when(mockClient.createUser(argThat(new ArgumentMatcher<JSONObject>() {
            @Override
            public boolean matches(Object argument) {
                JSONObject payload = (JSONObject) argument;
                JSONArray jsonArray = payload.optJSONArray("android_channels");

                try {
                    return jsonArray != null && jsonArray.length() == 1 && jsonArray.getString(0).equals(fakeChannelId);
                } catch (JSONException e) {
                    return false;
                }
            }
        }))).thenReturn(response);

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_USER_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        updateService.onHandleIntent(intent);

        // Verify user name and user token was set
        assertEquals("Should update the user name", richPushManager.getRichPushUser().getId(), fakeUserId);
        assertEquals("Should update the user token", richPushManager.getRichPushUser().getPassword(), fakeUserToken);

        // Verify result receiver
        assertEquals("Should return success code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);
    }

    /**
     * Test create user when PushManager when a channel has not been created.
     */
    @Test
    public void testCreateUserNoChannel() throws IOException {
        when(mockPushManager.getChannelId()).thenReturn(null);

        // Set up user response
        UserResponse response = Mockito.mock(UserResponse.class);
        when(response.getUserId()).thenReturn(fakeUserId);
        when(response.getUserToken()).thenReturn(fakeUserToken);

        // Return the response
        when(mockClient.createUser(argThat(new ArgumentMatcher<JSONObject>() {
            @Override
            public boolean matches(Object argument) {
                JSONObject payload = (JSONObject) argument;

                return payload.length() == 0; // Empty
            }
        }))).thenReturn(response);

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_USER_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        updateService.onHandleIntent(intent);

        // Verify user name and user token was set
        assertEquals("Should update the user name", RichPushManager.shared().getRichPushUser().getId(), fakeUserId);
        assertEquals("Should update the user token", RichPushManager.shared().getRichPushUser().getPassword(), fakeUserToken);

        // Verify result receiver
        assertEquals("Should return success code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);
    }

    /**
     * Test create user failed.
     */
    @Test
    public void testCreateUserFailed() {
        // Return the response
        when(mockClient.createUser(any(JSONObject.class))).thenReturn(null);

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_USER_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        updateService.onHandleIntent(intent);

        assertNull("Should not update the user name", RichPushManager.shared().getRichPushUser().getId());
        assertNull("Should not update the user token", RichPushManager.shared().getRichPushUser().getPassword());

        // Verify result receiver
        assertEquals("Should return error code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_ERROR,
                resultReceiver.lastResultCode);
    }


    /**
     * Test user update on amazon.
     */
    @Test
    public void testUpdateUserAmazon() throws IOException {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);

        // Set a user
        preferences.setUserCredentials(fakeUserId, fakeUserToken);

        // Set the Channel
        when(mockPushManager.getChannelId()).thenReturn(fakeChannelId);

        // Set up the client
        when(mockClient.updateUser(argThat(new ArgumentMatcher<JSONObject>() {
            @Override
            public boolean matches(Object argument) {
                JSONObject payload = (JSONObject) argument;

                try {
                    JSONArray channels = payload.getJSONObject("amazon_channels").getJSONArray("add");
                    return channels.length() == 1 && channels.getString(0).equals(fakeChannelId);
                } catch (JSONException e) {
                    return false;
                }
            }
        }), eq(fakeUserId), eq(fakeUserToken))).thenReturn(true);

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_USER_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        updateService.onHandleIntent(intent);

        // Verify result receiver
        assertEquals("Should return success code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);
    }

    /**
     * Test user update on android.
     */
    @Test
    public void testUpdateUserAndroid() throws IOException {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        // Set a user
        preferences.setUserCredentials(fakeUserId, fakeUserToken);

        // Set the Channel
        when(mockPushManager.getChannelId()).thenReturn(fakeChannelId);

        // Set up the client
        when(mockClient.updateUser(argThat(new ArgumentMatcher<JSONObject>() {
            @Override
            public boolean matches(Object argument) {
                JSONObject payload = (JSONObject) argument;

                try {
                    JSONArray channels = payload.getJSONObject("android_channels").getJSONArray("add");
                    return channels.length() == 1 && channels.getString(0).equals(fakeChannelId);
                } catch (JSONException e) {
                    return false;
                }
            }
        }), eq(fakeUserId), eq(fakeUserToken))).thenReturn(true);

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_USER_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        updateService.onHandleIntent(intent);

        // Verify result receiver
        assertEquals("Should return success code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);
    }

    /**
     * Test user update without a channel should not update.
     */
    @Test
    public void testUpdateUserNoChannel() throws IOException {
        // Set a user
        preferences.setUserCredentials(fakeUserId, fakeUserToken);

        // Return a null channel
        when(mockPushManager.getChannelId()).thenReturn(null);

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_USER_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        updateService.onHandleIntent(intent);

        // Verify result receiver
        assertEquals("Should return error code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_ERROR,
                resultReceiver.lastResultCode);

        // Verify we do not update the user
        verify(mockClient, times(0)).updateUser(any(JSONObject.class), any(String.class), any(String.class));
    }

    /**
     * Test user update failed request.
     */
    @Test
    public void testUpdateUserRequestFail() throws IOException, JSONException {
        // Set a user
        preferences.setUserCredentials(fakeUserId, fakeUserToken);

        // Return the response
        when(mockClient.updateUser(any(JSONObject.class), any(String.class), any(String.class))).thenReturn(false);

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_USER_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        updateService.onHandleIntent(intent);

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
