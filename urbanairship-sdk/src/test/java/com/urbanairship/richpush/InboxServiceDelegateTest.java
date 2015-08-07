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

import android.content.ContentValues;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.push.PushManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.net.HttpURLConnection;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class InboxServiceDelegateTest extends BaseTestCase {

    AirshipConfigOptions options;
    TestResultReceiver resultReceiver;
    RichPushUserPreferences preferences;
    UserAPIClient mockClient;
    PushManager mockPushManager;
    PreferenceDataStore dataStore;
    RichPushResolver resolver;
    RichPushUser richPushUser;

    RichPushManager richPushManager;
    private InboxServiceDelegate serviceDelegate;

    @Before
    public void setup() {
        mockClient = Mockito.mock(UserAPIClient.class);
        mockPushManager = Mockito.mock(PushManager.class);

        TestApplication.getApplication().setPushManager(mockPushManager);

        options = UAirship.shared().getAirshipConfigOptions();
        resultReceiver = new TestResultReceiver();
        dataStore = TestApplication.getApplication().preferenceDataStore;
        resolver = new RichPushResolver(TestApplication.getApplication());
        richPushManager = UAirship.shared().getRichPushManager();
        richPushManager.getRichPushInbox().updateCache();
        richPushUser = richPushManager.getRichPushUser();
        preferences = richPushManager.getRichPushUser().preferences;
        // Clear any user or password
        preferences.setUserCredentials(null, null);

        serviceDelegate = new InboxServiceDelegate(TestApplication.getApplication(), dataStore,
                mockClient, resolver, UAirship.shared());
    }

    /**
     * Test when user has not been created returns an error code.
     */
    @Test
    public void testUserNotCreated() {
        // Clear any user or password
        preferences.setUserCredentials(null, null);

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        serviceDelegate.onHandleIntent(intent);

        // Verify result receiver
        assertEquals("Should return an error code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_ERROR,
                resultReceiver.lastResultCode);
    }

    /**
     * Test updateMessages returns error code when response is null.
     */
    @Test
    public void testUpdateMessagesNull() {
        // Fake a user
        preferences.setUserCredentials("fakeUserId", "fakeUserToken");
        richPushUser = richPushManager.getRichPushUser();

        // Return the response
        when(mockClient.getMessages(any(String.class), any(String.class), any(Long.class))).thenReturn(null);

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        serviceDelegate.onHandleIntent(intent);

        // Verify result receiver
        assertEquals("Should return an error code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_ERROR,
                resultReceiver.lastResultCode);
    }

    /**
     * Test updateMessages returns success code when response is HTTP_NOT_MODIFIED.
     */
    @Test
    public void testUpdateMessagesNotModified() {
        // Fake a user
        preferences.setUserCredentials("fakeUserId", "fakeUserToken");
        richPushUser = richPushManager.getRichPushUser();

        MessageListResponse response = new MessageListResponse(new ContentValues[] { new ContentValues() },
                                                               HttpURLConnection.HTTP_NOT_MODIFIED,
                                                               System.currentTimeMillis());

        // Return the response
        when(mockClient.getMessages(any(String.class), any(String.class), any(Long.class))).thenReturn(response);

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        serviceDelegate.onHandleIntent(intent);

        // Verify result receiver
        assertEquals("Should return a success code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);
    }

    /**
     * Test updateMessages returns success code when response is HTTP_OK.
     */
    @Test
    public void testUpdateMessagesOk() {
        // Fake a user
        preferences.setUserCredentials("fakeUserId", "fakeUserToken");
        richPushUser = richPushManager.getRichPushUser();

        ContentValues contentValues = new ContentValues();
        contentValues.put("message_id", "some_mesg_id");
        contentValues.put("message_url", "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/");
        contentValues.put("message_body_url", "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/body/");
        contentValues.put("message_read_url", "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/read/");
        contentValues.put("extra", "");

        MessageListResponse response = new MessageListResponse(new ContentValues[] { contentValues },
                                                               HttpURLConnection.HTTP_OK,
                                                               System.currentTimeMillis());
        // Return the response
        when(mockClient.getMessages(any(String.class), any(String.class), any(Long.class))).thenReturn(response);

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        serviceDelegate.onHandleIntent(intent);

        // Verify result receiver
        assertEquals("Should return a success code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS,
                resultReceiver.lastResultCode);
    }

    /**
     * Test updateMessages returns error code when response is HTTP_INTERNAL_ERROR
     */
    @Test
    public void testUpdateMessagesServerError() {
        // Fake a user
        preferences.setUserCredentials("fakeUserId", "fakeUserToken");
        richPushUser = richPushManager.getRichPushUser();

        MessageListResponse response = new MessageListResponse(new ContentValues[] { new ContentValues() },
                                                               HttpURLConnection.HTTP_INTERNAL_ERROR,
                                                               System.currentTimeMillis());
        // Return the response
        when(mockClient.getMessages(any(String.class), any(String.class), any(Long.class))).thenReturn(response);

        Intent intent = new Intent(RichPushUpdateService.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        serviceDelegate.onHandleIntent(intent);

        // Verify result receiver
        assertEquals("Should return an error code", RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_ERROR,
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
