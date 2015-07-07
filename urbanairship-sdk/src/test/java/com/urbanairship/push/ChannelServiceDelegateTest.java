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

package com.urbanairship.push;

import android.content.Intent;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplication;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

public class ChannelServiceDelegateTest extends BaseTestCase {
    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeChannelLocation = "https://go.urbanairship.com/api/channels/AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";

    PushPreferences pushPref;
    PushManager pushManager;
    ChannelAPIClient client;
    ChannelServiceDelegate serviceDelegate;

    @Before
    public void setUp() {
        client = Mockito.mock(ChannelAPIClient.class);


        pushManager = UAirship.shared().getPushManager();
        pushPref = pushManager.getPreferences();

        // Extend it to make onHandleIntent public so we can call it directly
        serviceDelegate = new ChannelServiceDelegate(TestApplication.getApplication(),
                TestApplication.getApplication().preferenceDataStore, client, UAirship.shared());

        Shadows.shadowOf(RuntimeEnvironment.application).clearStartedServices();
    }

    /**
     * Test update registration will create a new channel for Amazon platform
     */
    @Test
    public void testUpdateRegistrationCreateChannelAmazon() {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);

        // Verify channel doesn't exist in preferences
        assertNull("Channel ID should be null in preferences", pushPref.getChannelId());
        assertNull("Channel location should be null in preferences", pushPref.getChannelLocation());

        // Set up channel response
        ChannelResponse response = Mockito.mock(ChannelResponse.class);
        when(response.getStatus()).thenReturn(HttpStatus.SC_CREATED);
        when(response.getChannelId()).thenReturn(fakeChannelId);
        when(response.getChannelLocation()).thenReturn(fakeChannelLocation);

        // Ensure payload is different, so we don't get a null payload
        pushPref.setAlias("someAlias");
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        Intent intent = new Intent(PushService.ACTION_UPDATE_CHANNEL_REGISTRATION);
        serviceDelegate.onHandleIntent(intent);

        assertEquals("Channel ID should exist in preferences", fakeChannelId, pushPref.getChannelId());
        assertEquals("Channel location should exist in preferences", fakeChannelLocation,
                pushPref.getChannelLocation());
    }

    /**
     * Test update registration with channel ID and null channel location will create a new channel
     */
    @Test
    public void testUpdateRegistrationNullChannelLocation() {
        // Verify channel doesn't exist in preferences
        assertNull("Channel ID should be null in preferences", pushPref.getChannelId());
        assertNull("Channel location should be null in preferences", pushPref.getChannelLocation());

        // Only set the channel ID
        pushPref.setChannelId(fakeChannelId);

        assertEquals("Channel ID should be set in preferences", fakeChannelId, pushPref.getChannelId());
        assertNull("Channel location should be null in preferences", pushPref.getChannelLocation());

        // Set up channel response
        ChannelResponse response = Mockito.mock(ChannelResponse.class);
        when(response.getStatus()).thenReturn(HttpStatus.SC_CREATED);
        when(response.getChannelId()).thenReturn(fakeChannelId);
        when(response.getChannelLocation()).thenReturn(fakeChannelLocation);

        // Ensure payload is different, so we don't get a null payload
        pushPref.setAlias("someAlias");
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        Intent intent = new Intent(PushService.ACTION_UPDATE_CHANNEL_REGISTRATION);
        serviceDelegate.onHandleIntent(intent);

        assertEquals("Channel ID should match in preferences", fakeChannelId, pushPref.getChannelId());
        assertEquals("Channel location should match in preferences", fakeChannelLocation,
                pushPref.getChannelLocation());
    }

    /**
     * Test creating channel accepts a 200
     */
    @Test
    public void testCreateChannel200() {
        // Set up channel response
        ChannelResponse response = Mockito.mock(ChannelResponse.class);
        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        when(response.getChannelId()).thenReturn(fakeChannelId);
        when(response.getChannelLocation()).thenReturn(fakeChannelLocation);

        pushPref.setLastRegistrationPayload(null);
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        Intent intent = new Intent(PushService.ACTION_UPDATE_CHANNEL_REGISTRATION);
        serviceDelegate.onHandleIntent(intent);

        assertEquals("Channel ID should match in preferences", fakeChannelId, pushPref.getChannelId());
        assertEquals("Channel location should match in preferences", fakeChannelLocation,
                pushPref.getChannelLocation());
    }


    /**
     * Test update registration fail to create a channel when channel response code is not successful
     */
    @Test
    public void testUpdateRegistrationChannelResponseCodeFail() {
        // Verify channel doesn't exist in preferences
        assertNull("Channel ID should be null in preferences", pushPref.getChannelId());
        assertNull("Channel location should be null in preferences", pushPref.getChannelLocation());

        // Set up channel response
        ChannelResponse response = Mockito.mock(ChannelResponse.class);
        when(response.getStatus()).thenReturn(HttpStatus.SC_NOT_FOUND);
        when(response.getChannelId()).thenReturn(fakeChannelId);
        when(response.getChannelLocation()).thenReturn(fakeChannelLocation);

        // Ensure payload is different, so we don't get a null payload
        pushPref.setAlias("someAlias");
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        Intent intent = new Intent(PushService.ACTION_UPDATE_CHANNEL_REGISTRATION);
        serviceDelegate.onHandleIntent(intent);

        // Verify channel creation failed
        assertNull("Channel ID should be null in preferences", pushPref.getChannelId());
        assertNull("Channel location should be null in preferences", pushPref.getChannelLocation());
    }

    /**
     * Test update registration fail to create a channel when channel ID from response is null
     */
    @Test
    public void testUpdateRegistrationChannelResponseNullChannelId() {
        // Verify channel doesn't exist in preferences
        assertNull("Channel ID should be null in preferences", pushPref.getChannelId());
        assertNull("Channel location should be null in preferences", pushPref.getChannelLocation());

        // Set up channel response
        ChannelResponse response = Mockito.mock(ChannelResponse.class);
        when(response.getStatus()).thenReturn(HttpStatus.SC_CREATED);
        when(response.getChannelId()).thenReturn(null);
        when(response.getChannelLocation()).thenReturn(fakeChannelLocation);

        // Ensure payload is different, so we don't get a null payload
        pushPref.setAlias("someAlias");
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        Intent intent = new Intent(PushService.ACTION_UPDATE_CHANNEL_REGISTRATION);
        serviceDelegate.onHandleIntent(intent);

        // Verify channel creation failed
        assertNull("Channel ID should be null in preferences", pushPref.getChannelId());
        assertNull("Channel location should be null in preferences", pushPref.getChannelLocation());
    }

    /**
     * Test updating a channel succeeds
     */
    @Test
    public void testUpdateChannelSucceed() throws MalformedURLException {
        // Set Channel ID and channel location
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        assertEquals("Channel ID should exist in preferences", pushPref.getChannelId(), fakeChannelId);
        assertEquals("Channel location should exist in preferences", pushPref.getChannelLocation(), fakeChannelLocation);

        long lastRegistrationTime = pushPref.getLastRegistrationTime();

        // Set up channel response
        ChannelResponse response = Mockito.mock(ChannelResponse.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_OK);

        // Ensure payload is different, so we don't get a null payload
        pushPref.setAlias("someAlias");
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        URL channelLocation = new URL(fakeChannelLocation);
        // Return the response
        when(client.updateChannelWithPayload(channelLocation, payload)).thenReturn(response);

        Intent intent = new Intent(PushService.ACTION_UPDATE_CHANNEL_REGISTRATION);
        serviceDelegate.onHandleIntent(intent);

        // Verify channel update succeeded
        assertNotSame("Last registration time should be updated", pushPref.getLastRegistrationTime(), lastRegistrationTime);
    }

    /**
     * Test updating channel returns a 409 recreates the channel.
     */
    @Test
    public void testChannelConflict() throws MalformedURLException {
        // Set Channel ID and channel location
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        // Set a last registration payload so we can verify it was cleared
        pushPref.setLastRegistrationPayload(new ChannelRegistrationPayload.Builder().build());

        // Set up a conflict response
        ChannelResponse conflictResponse = Mockito.mock(ChannelResponse.class);
        when(conflictResponse.getStatus()).thenReturn(HttpURLConnection.HTTP_CONFLICT);
        when(client.updateChannelWithPayload(Mockito.eq(new URL(fakeChannelLocation)), Mockito.any(ChannelRegistrationPayload.class))).thenReturn(conflictResponse);

        Intent intent = new Intent(PushService.ACTION_UPDATE_CHANNEL_REGISTRATION);
        serviceDelegate.onHandleIntent(intent);

        // Verify update was called
        Mockito.verify(client).updateChannelWithPayload(Mockito.eq(new URL(fakeChannelLocation)), Mockito.any(ChannelRegistrationPayload.class));

        // Verify the channel was cleared
        assertNull("Channel ID should be null", pushPref.getChannelId());
        assertNull("Channel location should be null", pushPref.getChannelLocation());

        // Verify the push service intent to update the channel was started
        ShadowApplication application = Shadows.shadowOf(RuntimeEnvironment.application);

        Intent serviceIntent;
        while((serviceIntent = application.getNextStartedService()) != null) {
            if (serviceIntent.getAction().equals(PushService.ACTION_UPDATE_CHANNEL_REGISTRATION)) {
                break;
            }
        }

        assertEquals(PushService.ACTION_UPDATE_CHANNEL_REGISTRATION, serviceIntent.getAction());
    }
}