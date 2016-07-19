/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.content.Intent;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushUser;

import org.junit.Assert;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ChannelIntentHandlerTest extends BaseTestCase {
    private static final String CHANNEL_LOCATION_KEY = "Location";

    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeChannelLocation = "https://go.urbanairship.com/api/channels/AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeResponseBody = "{\"channel_id\": \"AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE\"}";

    PreferenceDataStore dataStore;
    PushManager pushManager;
    ChannelApiClient client;
    ChannelIntentHandler intentHandler;
    RichPushInbox richPushInbox;
    RichPushUser richPushUser;


    @Before
    public void setUp() {
        client = mock(ChannelApiClient.class);

        richPushInbox = mock(RichPushInbox.class);
        TestApplication.getApplication().setInbox(richPushInbox);

        richPushUser = mock(RichPushUser.class);
        when(richPushInbox.getUser()).thenReturn(richPushUser);

        pushManager = UAirship.shared().getPushManager();
        dataStore = TestApplication.getApplication().preferenceDataStore;


        // Extend it to make handleIntent public so we can call it directly
        intentHandler = new ChannelIntentHandler(TestApplication.getApplication(), UAirship.shared(),
                TestApplication.getApplication().preferenceDataStore, client);

        Shadows.shadowOf(RuntimeEnvironment.application).clearStartedServices();
    }

    /**
     * Test update registration will create a new channel for Amazon platform
     */
    @Test
    public void testUpdateRegistrationCreateChannelAmazon() {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);

        // Verify channel doesn't exist in preferences
        assertNull("Channel ID should be null in preferences", pushManager.getChannelId());
        assertNull("Channel location should be null in preferences", pushManager.getChannelLocation());

        // Set up channel response
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_CREATED);
        when(response.getResponseBody()).thenReturn(fakeResponseBody);
        when(response.getResponseHeader(CHANNEL_LOCATION_KEY)).thenReturn(fakeChannelLocation);

        // Ensure payload is different, so we don't get a null payload
        pushManager.setAlias("someAlias");
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        Intent intent = new Intent(ChannelIntentHandler.ACTION_UPDATE_CHANNEL_REGISTRATION);
        intentHandler.handleIntent(intent);

        assertEquals("Channel ID should exist in preferences", fakeChannelId, pushManager.getChannelId());
        assertEquals("Channel location should exist in preferences", fakeChannelLocation,
                pushManager.getChannelLocation());
    }

    /**
     * Test update registration with channel ID and null channel location will create a new channel
     */
    @Test
    public void testUpdateRegistrationNullChannelLocation() {
        // Verify channel doesn't exist in preferences
        assertNull("Channel ID should be null in preferences", pushManager.getChannelId());
        assertNull("Channel location should be null in preferences", pushManager.getChannelLocation());

        // Only set the channel ID
        pushManager.setChannel(fakeChannelId, null);

        assertEquals("Channel ID should be set in preferences", fakeChannelId, pushManager.getChannelId());
        assertNull("Channel location should be null in preferences", pushManager.getChannelLocation());

        // Set up channel response
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_CREATED);
        when(response.getResponseBody()).thenReturn(fakeResponseBody);
        when(response.getResponseHeader(CHANNEL_LOCATION_KEY)).thenReturn(fakeChannelLocation);

        // Ensure payload is different, so we don't get a null payload
        pushManager.setAlias("someAlias");
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        Intent intent = new Intent(ChannelIntentHandler.ACTION_UPDATE_CHANNEL_REGISTRATION);
        intentHandler.handleIntent(intent);

        assertEquals("Channel ID should match in preferences", fakeChannelId, pushManager.getChannelId());
        assertEquals("Channel location should match in preferences", fakeChannelLocation,
                pushManager.getChannelLocation());
    }

    /**
     * Test creating channel accepts a 200
     */
    @Test
    public void testCreateChannel200() {
        // Set up channel response
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_OK);
        when(response.getResponseBody()).thenReturn(fakeResponseBody);
        when(response.getResponseHeader(CHANNEL_LOCATION_KEY)).thenReturn(fakeChannelLocation);

        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        Intent intent = new Intent(ChannelIntentHandler.ACTION_UPDATE_CHANNEL_REGISTRATION);
        intentHandler.handleIntent(intent);

        assertEquals("Channel ID should match in preferences", fakeChannelId, pushManager.getChannelId());
        assertEquals("Channel location should match in preferences", fakeChannelLocation,
                pushManager.getChannelLocation());

        // Verify we update the user
        verify(richPushInbox.getUser()).update(true);
    }


    /**
     * Test update registration fail to create a channel when channel response code is not successful
     */
    @Test
    public void testUpdateRegistrationResponseCodeFail() {
        // Verify channel doesn't exist in preferences
        assertNull("Channel ID should be null in preferences", pushManager.getChannelId());
        assertNull("Channel location should be null in preferences", pushManager.getChannelLocation());

        // Set up channel response
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);
        when(response.getResponseBody()).thenReturn(fakeResponseBody);
        when(response.getResponseHeader(CHANNEL_LOCATION_KEY)).thenReturn(fakeChannelLocation);

        // Ensure payload is different, so we don't get a null payload
        pushManager.setAlias("someAlias");
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        Intent intent = new Intent(ChannelIntentHandler.ACTION_UPDATE_CHANNEL_REGISTRATION);
        intentHandler.handleIntent(intent);

        // Verify channel creation failed
        assertNull("Channel ID should be null in preferences", pushManager.getChannelId());
        assertNull("Channel location should be null in preferences", pushManager.getChannelLocation());
    }

    /**
     * Test update registration fail to create a channel when channel ID from response is null
     */
    @Test
    public void testUpdateRegistrationResponseNullChannelId() {
        // Verify channel doesn't exist in preferences
        assertNull("Channel ID should be null in preferences", pushManager.getChannelId());
        assertNull("Channel location should be null in preferences", pushManager.getChannelLocation());

        // Set up channel response
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_CREATED);
        when(response.getResponseBody()).thenReturn(null);
        when(response.getResponseHeader(CHANNEL_LOCATION_KEY)).thenReturn(fakeChannelLocation);

        // Ensure payload is different, so we don't get a null payload
        pushManager.setAlias("someAlias");
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        Intent intent = new Intent(ChannelIntentHandler.ACTION_UPDATE_CHANNEL_REGISTRATION);
        intentHandler.handleIntent(intent);

        // Verify channel creation failed
        assertNull("Channel ID should be null in preferences", pushManager.getChannelId());
        assertNull("Channel location should be null in preferences", pushManager.getChannelLocation());
    }

    /**
     * Test updating a channel succeeds
     */
    @Test
    public void testUpdateChannelSucceed() throws MalformedURLException {
        // Set Channel ID and channel location
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        assertEquals("Channel ID should exist in preferences", pushManager.getChannelId(), fakeChannelId);
        assertEquals("Channel location should exist in preferences", pushManager.getChannelLocation(), fakeChannelLocation);

        long lastRegistrationTime = dataStore.getLong("com.urbanairship.push.LAST_REGISTRATION_TIME", 0);

        // Set up channel response
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_OK);

        // Ensure payload is different, so we don't get a null payload
        pushManager.setAlias("someAlias");
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        URL channelLocation = new URL(fakeChannelLocation);
        // Return the response
        when(client.updateChannelWithPayload(channelLocation, payload)).thenReturn(response);

        Intent intent = new Intent(ChannelIntentHandler.ACTION_UPDATE_CHANNEL_REGISTRATION);
        intentHandler.handleIntent(intent);

        // Verify channel update succeeded
        assertNotSame("Last registration time should be updated", dataStore.getLong("com.urbanairship.push.LAST_REGISTRATION_TIME", 0), lastRegistrationTime);
    }

    /**
     * Test updating channel returns a 409 recreates the channel.
     */
    @Test
    public void testChannelConflict() throws MalformedURLException {
        // Set Channel ID and channel location
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);


        // Set up a conflict response
        Response conflictResponse = mock(Response.class);
        when(conflictResponse.getStatus()).thenReturn(HttpURLConnection.HTTP_CONFLICT);
        when(client.updateChannelWithPayload(Mockito.eq(new URL(fakeChannelLocation)), Mockito.any(ChannelRegistrationPayload.class))).thenReturn(conflictResponse);

        Intent intent = new Intent(ChannelIntentHandler.ACTION_UPDATE_CHANNEL_REGISTRATION);
        intentHandler.handleIntent(intent);

        // Verify update was called
        Mockito.verify(client).updateChannelWithPayload(Mockito.eq(new URL(fakeChannelLocation)), Mockito.any(ChannelRegistrationPayload.class));

        // Verify the channel was cleared
        assertNull("Channel ID should be null", pushManager.getChannelId());
        assertNull("Channel location should be null", pushManager.getChannelLocation());

        // Verify the push service intent to update the channel was started
        ShadowApplication application = Shadows.shadowOf(RuntimeEnvironment.application);

        Intent serviceIntent;
        while((serviceIntent = application.getNextStartedService()) != null) {
            if (serviceIntent.getAction().equals(ChannelIntentHandler.ACTION_UPDATE_CHANNEL_REGISTRATION)) {
                break;
            }
        }

        Assert.assertEquals(ChannelIntentHandler.ACTION_UPDATE_CHANNEL_REGISTRATION, serviceIntent.getAction());
    }
}
