/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.content.Intent;
import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class ChannelIntentHandlerTest extends BaseTestCase {
    private static final String CHANNEL_LOCATION_KEY = "Location";

    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeChannelLocation = "https://go.urbanairship.com/api/channels/AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeResponseBody = "{\"channel_id\": \"AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE\"}";

    private Map<String, Set<String>> addTagsMap;
    private Map<String, Set<String>> removeTagsMap;
    private Bundle addTagsBundle;
    private Bundle removeTagsBundle;

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

        Set<String> addTags = new HashSet<>();
        addTags.add("tag1");
        addTags.add("tag2");
        addTagsMap = new HashMap<>();
        addTagsMap.put("tagGroup", addTags);

        Set<String> removeTags = new HashSet<>();
        removeTags.add("tag4");
        removeTags.add("tag5");
        removeTagsMap = new HashMap<>();
        removeTagsMap.put("tagGroup", removeTags);

        addTagsBundle = new Bundle();
        for (Map.Entry<String, Set<String>> entry : addTagsMap.entrySet()) {
            addTagsBundle.putStringArrayList(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        removeTagsBundle = new Bundle();
        for (Map.Entry<String, Set<String>> entry : removeTagsMap.entrySet()) {
            removeTagsBundle.putStringArrayList(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
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
        while ((serviceIntent = application.getNextStartedService()) != null) {
            if (serviceIntent.getAction().equals(ChannelIntentHandler.ACTION_UPDATE_CHANNEL_REGISTRATION)) {
                break;
            }
        }

        Assert.assertEquals(ChannelIntentHandler.ACTION_UPDATE_CHANNEL_REGISTRATION, serviceIntent.getAction());
    }

    /**
     * Test apply tag group changes updates the pending tag groups.
     */
    @Test
    public void testApplyTagGroupChanges() throws JsonException {
        // Apply tag groups
        Intent intent = new Intent(ChannelIntentHandler.ACTION_APPLY_TAG_GROUP_CHANGES);
        intent.putExtra(TagGroupsEditor.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(TagGroupsEditor.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        intentHandler.handleIntent(intent);

        // Verify pending tags are saved
        assertEquals(JsonValue.wrap(addTagsMap).toString(), dataStore.getString(ChannelIntentHandler.PENDING_CHANNEL_ADD_TAG_GROUPS_KEY, null));
        assertEquals(JsonValue.wrap(removeTagsMap).toString(), dataStore.getString(ChannelIntentHandler.PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test apply tag group changes schedules a tag group update request.
     */
    @Test
    public void testApplyTagGroupChangesSchedulesUpload() throws JsonException {
        // Set Channel ID and channel location
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        // Apply tag groups
        Intent intent = new Intent(ChannelIntentHandler.ACTION_APPLY_TAG_GROUP_CHANGES);
        intent.putExtra(TagGroupsEditor.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(TagGroupsEditor.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        intentHandler.handleIntent(intent);

        // Verify pending tags are saved
        assertEquals(JsonValue.wrap(addTagsMap).toString(), dataStore.getString(ChannelIntentHandler.PENDING_CHANNEL_ADD_TAG_GROUPS_KEY, null));
        assertEquals(JsonValue.wrap(removeTagsMap).toString(), dataStore.getString(ChannelIntentHandler.PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY, null));


        // Verify the push service intent to update the channel was started
        ShadowApplication application = Shadows.shadowOf(RuntimeEnvironment.application);

        Intent serviceIntent;
        while ((serviceIntent = application.getNextStartedService()) != null) {
            if (serviceIntent.getAction().equals(ChannelIntentHandler.ACTION_UPDATE_TAG_GROUPS)) {
                break;
            }
        }

        assertEquals(ChannelIntentHandler.ACTION_UPDATE_TAG_GROUPS, serviceIntent.getAction());
    }

    /**
     * Test update channel tag groups succeeds if the status is 200 and clears pending tags.
     */
    @Test
    public void testUpdateChannelTagGroupsSucceed() throws JsonException {
        // Return a channel ID
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        // Provide pending changes
        dataStore.put(ChannelIntentHandler.PENDING_CHANNEL_ADD_TAG_GROUPS_KEY, JsonValue.wrap(addTagsMap).toString());
        dataStore.put(ChannelIntentHandler.PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY, JsonValue.wrap(removeTagsMap).toString());

        // Set up a 200 response
        Response response = Mockito.mock(Response.class);
        when(client.updateTagGroups(fakeChannelId, addTagsMap, removeTagsMap)).thenReturn(response);
        when(response.getStatus()).thenReturn(200);

        // Perform the update
        Intent intent = new Intent(ChannelIntentHandler.ACTION_UPDATE_TAG_GROUPS);
        intentHandler.handleIntent(intent);

        // Verify updateChannelTags called
        Mockito.verify(client, Mockito.times(1)).updateTagGroups(fakeChannelId, addTagsMap, removeTagsMap);

        // Verify pending tag groups are empty
        assertNull(dataStore.getString(ChannelIntentHandler.PENDING_CHANNEL_ADD_TAG_GROUPS_KEY, null));
        assertNull(dataStore.getString(ChannelIntentHandler.PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test update channel tag groups without channel fails and save pending tags.
     */
    @Test
    public void testUpdateChannelTagGroupsNoChannelId() throws JsonException {
        // Provide pending changes
        dataStore.put(ChannelIntentHandler.PENDING_CHANNEL_ADD_TAG_GROUPS_KEY, JsonValue.wrap(addTagsMap).toString());
        dataStore.put(ChannelIntentHandler.PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY, JsonValue.wrap(removeTagsMap).toString());

        // Perform the update
        Intent intent = new Intent(ChannelIntentHandler.ACTION_UPDATE_TAG_GROUPS);
        intentHandler.handleIntent(intent);

        // Verify updateChannelTags not called when channel ID doesn't exist
        verifyZeroInteractions(client);

        // Verify pending tags saved
        assertEquals(JsonValue.wrap(addTagsMap).toString(), dataStore.getString(ChannelIntentHandler.PENDING_CHANNEL_ADD_TAG_GROUPS_KEY, null));
        assertEquals(JsonValue.wrap(removeTagsMap).toString(), dataStore.getString(ChannelIntentHandler.PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test update channel tag groups fails if the status is 500 and save pending tags.
     */
    @Test
    public void testUpdateChannelTagGroupsServerError() throws JsonException {
        // Return a channel ID
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        // Provide pending changes
        dataStore.put(ChannelIntentHandler.PENDING_CHANNEL_ADD_TAG_GROUPS_KEY, JsonValue.wrap(addTagsMap).toString());
        dataStore.put(ChannelIntentHandler.PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY, JsonValue.wrap(removeTagsMap).toString());

        // Set up a 500 response
        Response response = Mockito.mock(Response.class);
        when(client.updateTagGroups(fakeChannelId, addTagsMap, removeTagsMap)).thenReturn(response);
        when(response.getStatus()).thenReturn(500);

        // Perform the update
        Intent intent = new Intent(ChannelIntentHandler.ACTION_UPDATE_TAG_GROUPS);
        intentHandler.handleIntent(intent);

        // Verify updateChannelTags called
        Mockito.verify(client).updateTagGroups(fakeChannelId, addTagsMap, removeTagsMap);
    }

    /**
     * Test don't update channel tags if both pendingAddTags and pendingRemoveTags are empty.
     */
    @Test
    public void testNoUpdateWithEmptyTags() {
        // Return a channel ID
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        // Provide pending changes
        dataStore.remove(ChannelIntentHandler.PENDING_CHANNEL_ADD_TAG_GROUPS_KEY);
        dataStore.remove(ChannelIntentHandler.PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY);

        // Perform an update without specify new tags
        Intent intent = new Intent(ChannelIntentHandler.ACTION_UPDATE_TAG_GROUPS);
        intentHandler.handleIntent(intent);

        // Verify it didn't cause a client update
        verifyZeroInteractions(client);
    }

    /**
     * Test update channel tag groups fails if the status is 400 and clears pending tags.
     */
    @Test
    public void testUpdateChannelTagGroupsBadRequest() throws JsonException {
        // Return a channel ID
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        // Provide pending changes
        dataStore.put(ChannelIntentHandler.PENDING_CHANNEL_ADD_TAG_GROUPS_KEY, JsonValue.wrap(addTagsMap).toString());
        dataStore.put(ChannelIntentHandler.PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY, JsonValue.wrap(removeTagsMap).toString());

        // Set up a 400 response
        Response response = Mockito.mock(Response.class);
        when(client.updateTagGroups(fakeChannelId, addTagsMap, removeTagsMap)).thenReturn(response);
        when(response.getStatus()).thenReturn(400);

        // Perform the update
        Intent intent = new Intent(ChannelIntentHandler.ACTION_UPDATE_TAG_GROUPS);
        intentHandler.handleIntent(intent);

        // Verify updateChannelTags called
        Mockito.verify(client).updateTagGroups(fakeChannelId, addTagsMap, removeTagsMap);

        // Verify pending tag groups are empty
        junit.framework.Assert.assertNull(dataStore.getString(ChannelIntentHandler.PENDING_CHANNEL_ADD_TAG_GROUPS_KEY, null));
        junit.framework.Assert.assertNull(dataStore.getString(ChannelIntentHandler.PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY, null));
    }
}
