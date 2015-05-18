package com.urbanairship.push;

import android.content.Intent;
import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

public class PushServiceTest extends BaseTestCase {

    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeChannelLocation = "https://go.urbanairship.com/api/channels/AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeNamedUserId = "fake-named-user-id";
    private final String superFakeNamedUserId = "super-fake-named-user-id";
    private final String fakeToken = "FAKEAAAA-BBBB-CCCC-DDDD-TOKENEEEEEEE";
    private Set<String> addTags = new HashSet<>();
    private Map<String, Set<String>> pendingAddTags = new HashMap<>();
    private Set<String> removeTags = new HashSet<>();
    private Map<String, Set<String>> pendingRemoveTags = new HashMap<>();
    private Bundle addTagsBundle = new Bundle();
    private Bundle removeTagsBundle = new Bundle();

    PushPreferences pushPref;
    PushManager pushManager;
    PushService pushService;
    ChannelAPIClient client;
    NamedUserAPIClient namedUserClient;
    NamedUser namedUser;
    TagGroupsAPIClient tagGroupsClient;

    @Before
    public void setUp() {
        client = Mockito.mock(ChannelAPIClient.class);
        namedUserClient = Mockito.mock(NamedUserAPIClient.class);
        tagGroupsClient = Mockito.mock(TagGroupsAPIClient.class);
        pushManager = UAirship.shared().getPushManager();
        pushPref = pushManager.getPreferences();
        namedUser = pushManager.getNamedUser();

        addTags.add("tag1");
        addTags.add("tag2");
        pendingAddTags.put("tagGroup", addTags);

        removeTags.add("tag4");
        removeTags.add("tag5");
        pendingRemoveTags.put("tagGroup", removeTags);

        for (Map.Entry<String, Set<String>> entry : pendingAddTags.entrySet()) {
            addTagsBundle.putStringArrayList(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        for (Map.Entry<String, Set<String>> entry : pendingRemoveTags.entrySet()) {
            removeTagsBundle.putStringArrayList(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        // Extend it to make onHandleIntent public so we can call it directly
        pushService = new PushService(client, namedUserClient, tagGroupsClient) {
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
     * Test associate named user succeeds if the status is 2xx.
     */
    @Test
    public void testAssociateNamedUserSucceed() {
        for (int statusCode = 200; statusCode < 300; statusCode++) {
            // Set up a 2xx response
            Response response = Mockito.mock(Response.class);
            when(namedUserClient.associate(fakeNamedUserId, fakeChannelId)).thenReturn(response);
            when(response.getStatus()).thenReturn(statusCode);

            namedUser.setLastUpdatedToken(fakeToken);
            namedUser.setId(fakeNamedUserId);
            pushManager.setChannel(fakeChannelId, fakeChannelLocation);

            Intent intent = new Intent(PushService.ACTION_UPDATE_NAMED_USER);

            pushService.onHandleIntent(intent);

            assertEquals("The named user ID should match",
                    fakeNamedUserId, pushManager.getNamedUser().getId());
            assertEquals("The tokens should match",
                    pushManager.getNamedUser().getChangeToken(), pushManager.getNamedUser().getLastUpdatedToken());
        }
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

        assertEquals("The named user ID should match",
                superFakeNamedUserId, pushManager.getNamedUser().getId());
        assertNull("The token should stay the same", pushManager.getNamedUser().getLastUpdatedToken());
        Mockito.verify(namedUserClient, Mockito.times(1)).associate(Mockito.any(String.class), Mockito.any(String.class));
    }

    /**
     * Test disassociate named user succeeds if the status is 2xx.
     */
    @Test
    public void testDisassociateNamedUserSucceed() {
        for (int statusCode = 200; statusCode < 300; statusCode++) {
            // Set up a 2xx response
            Response response = Mockito.mock(Response.class);
            when(namedUserClient.disassociate(fakeChannelId)).thenReturn(response);
            when(response.getStatus()).thenReturn(statusCode);

            namedUser.setLastUpdatedToken(fakeToken);
            pushManager.getNamedUser().setId(null);
            pushManager.setChannel(fakeChannelId, fakeChannelLocation);

            Intent intent = new Intent(PushService.ACTION_UPDATE_NAMED_USER);

            pushService.onHandleIntent(intent);

            assertNull("Current named user ID should be null", pushManager.getNamedUser().getId());
            assertEquals("The tokens should match",
                    pushManager.getNamedUser().getChangeToken(), pushManager.getNamedUser().getLastUpdatedToken());
        }
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

        assertNull("Named user ID should be null", pushManager.getNamedUser().getId());
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

    /**
     * Test update channel tag groups succeeds if the status is 200 and clears pending tags.
     */
    @Test
    public void testUpdateChannelTagGroupsSucceed() {
        // Set up a 200 response
        Response response = Mockito.mock(Response.class);
        when(tagGroupsClient.updateChannelTags(fakeChannelId, pendingAddTags, pendingRemoveTags)).thenReturn(response);
        when(response.getStatus()).thenReturn(200);

        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        Intent intent = new Intent(PushService.ACTION_UPDATE_CHANNEL_TAG_GROUPS);
        intent.putExtra(PushService.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(PushService.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        pushService.onHandleIntent(intent);

        // Verify updateChannelTags called
        Mockito.verify(tagGroupsClient, Mockito.times(1)).updateChannelTags(Mockito.any(String.class), Mockito.any(HashMap.class), Mockito.any(HashMap.class));

        // Verify pending tags cleared
        Map<String, Set<String>> emptyTags = new HashMap<>();
        assertEquals("Pending add tags should be empty", emptyTags, pushPref.getPendingAddTagGroups());
        assertEquals("Pending remove tags should be empty", emptyTags, pushPref.getPendingRemoveTagGroups());
    }

    /**
     * Test update channel tag groups without channel fails and save pending tags.
     */
    @Test
    public void testUpdateChannelTagGroupsNoChannel() {
        pushManager.setChannel(null, null);

        Intent intent = new Intent(PushService.ACTION_UPDATE_CHANNEL_TAG_GROUPS);
        intent.putExtra(PushService.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(PushService.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        pushService.onHandleIntent(intent);

        // Verify updateChannelTags not called when channel ID doesn't exist
        Mockito.verify(tagGroupsClient, Mockito.times(0)).updateChannelTags(Mockito.any(String.class), Mockito.any(HashMap.class), Mockito.any(HashMap.class));

        // Verify pending tags saved
        assertEquals("Pending add tags should be saved", pendingAddTags, pushPref.getPendingAddTagGroups());
        assertEquals("Pending remove tags should be saved", pendingRemoveTags, pushPref.getPendingRemoveTagGroups());

    }

    /**
     * Test update channel tag groups fails if the status is 500 and save pending tags.
     */
    @Test
    public void testUpdateChannelTagGroupsServerError() {
        // Set up a 500 response
        Response response = Mockito.mock(Response.class);
        when(tagGroupsClient.updateChannelTags(fakeChannelId, pendingAddTags, pendingRemoveTags)).thenReturn(response);
        when(response.getStatus()).thenReturn(500);

        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        Intent intent = new Intent(PushService.ACTION_UPDATE_CHANNEL_TAG_GROUPS);
        intent.putExtra(PushService.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(PushService.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        pushService.onHandleIntent(intent);

        // Verify updateChannelTags called
        Mockito.verify(tagGroupsClient, Mockito.times(1)).updateChannelTags(Mockito.any(String.class), Mockito.any(HashMap.class), Mockito.any(HashMap.class));

        // Verify pending tags saved
        assertEquals("Pending add tags should be saved", pendingAddTags, pushPref.getPendingAddTagGroups());
        assertEquals("Pending remove tags should be saved", pendingRemoveTags, pushPref.getPendingRemoveTagGroups());
    }

    /**
     * Test update channel tag groups succeeds if the status is 400 and clears pending tags.
     */
    @Test
    public void testUpdateChannelTagGroupsBadRequest() {
        // Set up a 400 response
        Response response = Mockito.mock(Response.class);
        when(tagGroupsClient.updateChannelTags(fakeChannelId, pendingAddTags, pendingRemoveTags)).thenReturn(response);
        when(response.getStatus()).thenReturn(400);

        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        Intent intent = new Intent(PushService.ACTION_UPDATE_CHANNEL_TAG_GROUPS);
        intent.putExtra(PushService.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(PushService.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        pushService.onHandleIntent(intent);

        // Verify updateChannelTags called
        Mockito.verify(tagGroupsClient, Mockito.times(1)).updateChannelTags(Mockito.any(String.class), Mockito.any(HashMap.class), Mockito.any(HashMap.class));

        // Verify pending tags cleared
        Map<String, Set<String>> emptyTags = new HashMap<>();
        assertEquals("Pending add tags should be empty", emptyTags, pushPref.getPendingAddTagGroups());
        assertEquals("Pending remove tags should be empty", emptyTags, pushPref.getPendingRemoveTagGroups());
    }
}
