package com.urbanairship.push;

import android.content.Intent;

import com.urbanairship.RobolectricGradleTestRunner;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)
public class PushServiceTest {

    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeChannelLocation = "https://go.urbanairship.com/api/channels/AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeNamedUserId = "fake-named-user-id";
    private final String superFakeNamedUserId = "super-fake-named-user-id";
    private final String fakeToken = "FAKEAAAA-BBBB-CCCC-DDDD-TOKENEEEEEEE";

    PushPreferences pushPref;
    PushManager pushManager;
    PushService pushService;
    ChannelAPIClient client;
    NamedUserAPIClient namedUserClient;
    NamedUser namedUser;

    @Before
    public void setUp() {
        client = Mockito.mock(ChannelAPIClient.class);
        namedUserClient = Mockito.mock(NamedUserAPIClient.class);
        pushManager = UAirship.shared().getPushManager();
        pushPref = pushManager.getPreferences();
        namedUser = pushManager.getNamedUser();

        // Extend it to make onHandleIntent public so we can call it directly
        pushService = new PushService(client, namedUserClient) {
            @Override
            public void onHandleIntent(Intent intent) {
                super.onHandleIntent(intent);
            }
        };
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

        Intent intent = new Intent(PushService.ACTION_UPDATE_REGISTRATION);
        pushService.onHandleIntent(intent);

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

        Intent intent = new Intent(PushService.ACTION_UPDATE_REGISTRATION);
        pushService.onHandleIntent(intent);

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

        Intent intent = new Intent(PushService.ACTION_UPDATE_REGISTRATION);
        pushService.onHandleIntent(intent);

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

        Intent intent = new Intent(PushService.ACTION_UPDATE_REGISTRATION);
        pushService.onHandleIntent(intent);

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

        Intent intent = new Intent(PushService.ACTION_UPDATE_REGISTRATION);
        pushService.onHandleIntent(intent);

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
        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);

        // Ensure payload is different, so we don't get a null payload
        pushPref.setAlias("someAlias");
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        URL channelLocation = new URL(fakeChannelLocation);
        // Return the response
        when(client.updateChannelWithPayload(channelLocation, payload)).thenReturn(response);

        Intent intent = new Intent(PushService.ACTION_UPDATE_REGISTRATION);
        pushService.onHandleIntent(intent);

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
        when(conflictResponse.getStatus()).thenReturn(HttpStatus.SC_CONFLICT);
        when(client.updateChannelWithPayload(Mockito.eq(new URL(fakeChannelLocation)), Mockito.any(ChannelRegistrationPayload.class))).thenReturn(conflictResponse);

        // Set up a new channel creation response
        ChannelResponse createResponse = Mockito.mock(ChannelResponse.class);
        when(createResponse.getStatus()).thenReturn(HttpStatus.SC_CREATED);
        when(createResponse.getChannelId()).thenReturn("new channel id");
        when(createResponse.getChannelLocation()).thenReturn("channel://new");
        when(client.createChannelWithPayload(Mockito.any(ChannelRegistrationPayload.class))).thenReturn(createResponse);

        Intent intent = new Intent(PushService.ACTION_UPDATE_REGISTRATION);
        pushService.onHandleIntent(intent);

        assertEquals("Channel ID should be the new channel", "new channel id", pushPref.getChannelId());
        assertEquals("Channel location should be the new channel", "channel://new", pushPref.getChannelLocation());

        // Verify we called both create and update
        Mockito.verify(client, Mockito.times(1)).createChannelWithPayload(Mockito.any(ChannelRegistrationPayload.class));
        Mockito.verify(client, Mockito.times(1)).updateChannelWithPayload(Mockito.eq(new URL(fakeChannelLocation)), Mockito.any(ChannelRegistrationPayload.class));
    }

    /**
     * Test associate named user succeeds if the status is 200.
     */
    @Test
    public void testAssociateNamedUserSucceed() {
        namedUser.setId(null);
        pushManager.getNamedUser().setId(fakeNamedUserId);
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        // Set up a 200 response
        Response response = Mockito.mock(Response.class);
        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        when(namedUserClient.associate(fakeNamedUserId, fakeChannelId)).thenReturn(response);

        Intent intent = new Intent(PushService.ACTION_UPDATE_NAMED_USER);

        pushService.onHandleIntent(intent);

        assertEquals("The named user ID should match",
                fakeNamedUserId, pushManager.getNamedUser().getId());
        assertEquals("The token should match",
                pushManager.getNamedUser().getCurrentToken(), pushManager.getNamedUser().getLastUpdatedToken());
        Mockito.verify(namedUserClient, Mockito.times(1)).associate(Mockito.any(String.class), Mockito.any(String.class));
    }

    /**
     * Test associate named user fails if the status is 403
     */
    @Test
    public void testAssociateNamedUserFailed() {
        namedUser.setLastUpdatedToken(null);
        pushManager.getNamedUser().setId(superFakeNamedUserId);
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        // Set up a 403 response
        Response response = Mockito.mock(Response.class);
        when(response.getStatus()).thenReturn(HttpStatus.SC_FORBIDDEN);
        when(namedUserClient.associate(superFakeNamedUserId, fakeChannelId)).thenReturn(response);

        Intent intent = new Intent(PushService.ACTION_UPDATE_NAMED_USER);

        pushService.onHandleIntent(intent);

        assertEquals("The current named user ID should match",
                superFakeNamedUserId, pushManager.getNamedUser().getId());
        assertNull("The token should stay the same", pushManager.getNamedUser().getLastUpdatedToken());
        Mockito.verify(namedUserClient, Mockito.times(1)).associate(Mockito.any(String.class), Mockito.any(String.class));
    }

    /**
     * Test disassociate named user succeeds if the status is 200.
     */
    @Test
    public void testDisassociateNamedUserSucceed() {
        namedUser.setLastUpdatedToken(fakeToken);
        pushManager.getNamedUser().setId(null);
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        // Set up a 200 response
        Response response = Mockito.mock(Response.class);
        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        when(namedUserClient.disassociate(fakeChannelId)).thenReturn(response);

        Intent intent = new Intent(PushService.ACTION_UPDATE_NAMED_USER);

        pushService.onHandleIntent(intent);

        assertNull("Current named user ID should be null", pushManager.getNamedUser().getId());
        assertEquals("The token should match",
                pushManager.getNamedUser().getCurrentToken(), pushManager.getNamedUser().getLastUpdatedToken());
        Mockito.verify(namedUserClient, Mockito.times(1)).disassociate(Mockito.any(String.class));
    }

    /**
     * Test disassociate named user fails if status is not 200.
     */
    @Test
    public void testDisassociateNamedUserFailed() {
        namedUser.setLastUpdatedToken(fakeToken);
        pushManager.getNamedUser().setId(null);
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        // Set up a 404 response
        Response response = Mockito.mock(Response.class);
        when(response.getStatus()).thenReturn(HttpStatus.SC_NOT_FOUND);
        when(namedUserClient.disassociate(fakeChannelId)).thenReturn(response);

        Intent intent = new Intent(PushService.ACTION_UPDATE_NAMED_USER);

        pushService.onHandleIntent(intent);

        assertNull("Current named user ID should be null", pushManager.getNamedUser().getId());
        assertEquals("The token should stay the same",
                fakeToken, pushManager.getNamedUser().getLastUpdatedToken());
        Mockito.verify(namedUserClient, Mockito.times(1)).disassociate(Mockito.any(String.class));
    }

    /**
     * Test associate without channel fails.
     */
    @Test
    public void testAssociateNamedUserFailedNoChannel() {
        namedUser.setLastUpdatedToken(fakeToken);
        pushManager.setChannel(null, null);

        pushManager.getNamedUser().setId(superFakeNamedUserId);

        // Verify associate not called when channel ID doesn't exist
        Mockito.verify(namedUserClient, Mockito.times(0)).associate(Mockito.any(String.class), Mockito.any(String.class));
        assertEquals("The token should stay the same",
                fakeToken, pushManager.getNamedUser().getLastUpdatedToken());
        assertEquals("The named user ID should be set", superFakeNamedUserId, pushManager.getNamedUser().getId());
    }

    /**
     * Test disassociate without channel fails.
     */
    @Test
    public void testDisassociateNamedUserFailedNoChannel() {
        namedUser.setLastUpdatedToken(fakeToken);
        pushManager.setChannel(null, null);

        pushManager.getNamedUser().setId(null);

        // Verify disassociate not called when channel ID doesn't exist
        Mockito.verify(namedUserClient, Mockito.times(0)).disassociate(Mockito.any(String.class));
        assertEquals("The token should stay the same",
                fakeToken, pushManager.getNamedUser().getLastUpdatedToken());
        assertNull("The named user ID should be null", pushManager.getNamedUser().getId());
    }
}
