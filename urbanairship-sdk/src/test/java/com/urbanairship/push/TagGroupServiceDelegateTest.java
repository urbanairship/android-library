/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

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
import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class TagGroupServiceDelegateTest extends BaseTestCase {

    private Map<String, Set<String>> addTagsMap;
    private Map<String, Set<String>> removeTagsMap;
    private Bundle addTagsBundle;
    private Bundle removeTagsBundle;

    private TagGroupsApiClient tagGroupsClient;
    private NamedUser namedUser;
    private PushManager pushManager;
    private PreferenceDataStore dataStore;
    private TagGroupServiceDelegate delegate;

    @Before
    public void setUp() {
        tagGroupsClient = Mockito.mock(TagGroupsApiClient.class);
        namedUser = Mockito.mock(NamedUser.class);
        pushManager = Mockito.mock(PushManager.class);
        dataStore = TestApplication.getApplication().preferenceDataStore;

        delegate = new TagGroupServiceDelegate(TestApplication.getApplication(), dataStore,
                tagGroupsClient, pushManager, namedUser);

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
     * Test update channel tag groups succeeds if the status is 200 and clears pending tags.
     */
    @Test
    public void testUpdateChannelTagGroupsSucceed() {
        // Return a channel ID
        when(pushManager.getChannelId()).thenReturn("channelID");

        // Set up a 200 response
        Response response = Mockito.mock(Response.class);
        when(tagGroupsClient.updateChannelTags("channelID", addTagsMap, removeTagsMap)).thenReturn(response);
        when(response.getStatus()).thenReturn(200);

        // Perform the update
        Intent intent = new Intent(PushService.ACTION_UPDATE_CHANNEL_TAG_GROUPS);
        intent.putExtra(PushService.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(PushService.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        delegate.onHandleIntent(intent);

        // Verify updateChannelTags called
        Mockito.verify(tagGroupsClient, Mockito.times(1)).updateChannelTags("channelID", addTagsMap, removeTagsMap);

        // Verify pending tag groups are empty
        assertNull(dataStore.getString(TagGroupServiceDelegate.PENDING_CHANNEL_ADD_TAG_GROUPS_KEY, null));
        assertNull(dataStore.getString(TagGroupServiceDelegate.PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test update channel tag groups without channel fails and save pending tags.
     */
    @Test
    public void testUpdateChannelTagGroupsNoChannelId() throws JsonException {
        // Return a null channel ID
        when(pushManager.getChannelId()).thenReturn(null);

        // Perform the update
        Intent intent = new Intent(PushService.ACTION_UPDATE_CHANNEL_TAG_GROUPS);
        intent.putExtra(PushService.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(PushService.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        delegate.onHandleIntent(intent);

        // Verify updateChannelTags not called when channel ID doesn't exist
        verifyZeroInteractions(tagGroupsClient);

        // Verify pending tags saved
        assertEquals(JsonValue.wrap(addTagsMap).toString(), dataStore.getString(TagGroupServiceDelegate.PENDING_CHANNEL_ADD_TAG_GROUPS_KEY, null));
        assertEquals(JsonValue.wrap(removeTagsMap).toString(), dataStore.getString(TagGroupServiceDelegate.PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test update channel tag groups fails if the status is 500 and save pending tags.
     */
    @Test
    public void testUpdateChannelTagGroupsServerError() throws JsonException {
        // Return a channel ID
        when(pushManager.getChannelId()).thenReturn("channelID");

        // Set up a 500 response
        Response response = Mockito.mock(Response.class);
        when(tagGroupsClient.updateChannelTags("channelID", addTagsMap, removeTagsMap)).thenReturn(response);
        when(response.getStatus()).thenReturn(500);

        // Perform the update
        Intent intent = new Intent(PushService.ACTION_UPDATE_CHANNEL_TAG_GROUPS);
        intent.putExtra(PushService.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(PushService.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        delegate.onHandleIntent(intent);

        // Verify updateChannelTags called
        Mockito.verify(tagGroupsClient).updateChannelTags("channelID", addTagsMap, removeTagsMap);

        // Verify pending tags saved
        assertEquals(JsonValue.wrap(addTagsMap).toString(), dataStore.getString(TagGroupServiceDelegate.PENDING_CHANNEL_ADD_TAG_GROUPS_KEY, null));
        assertEquals(JsonValue.wrap(removeTagsMap).toString(), dataStore.getString(TagGroupServiceDelegate.PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test don't update channel tags if both pendingAddTags and pendingRemoveTags are empty.
     */
    @Test
    public void testNoUpdateWithEmptyTags() {
        // Return a channel ID
        when(pushManager.getChannelId()).thenReturn("channelID");

        // Perform an update without specify new tags
        Intent intent = new Intent(PushService.ACTION_UPDATE_CHANNEL_TAG_GROUPS);
        delegate.onHandleIntent(intent);

        // Verify it didn't cause a client update
        verifyZeroInteractions(tagGroupsClient);
    }

    /**
     * Test update channel tag groups fails if the status is 400 and clears pending tags.
     */
    @Test
    public void testUpdateChannelTagGroupsBadRequest() {
        // Return a channel ID
        when(pushManager.getChannelId()).thenReturn("channelID");

        // Set up a 400 response
        Response response = Mockito.mock(Response.class);
        when(tagGroupsClient.updateChannelTags("channelID", addTagsMap, removeTagsMap)).thenReturn(response);
        when(response.getStatus()).thenReturn(400);

        // Perform the update
        Intent intent = new Intent(PushService.ACTION_UPDATE_CHANNEL_TAG_GROUPS);
        intent.putExtra(PushService.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(PushService.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        delegate.onHandleIntent(intent);

        // Verify updateChannelTags called
        Mockito.verify(tagGroupsClient).updateChannelTags("channelID", addTagsMap, removeTagsMap);

        // Verify pending tag groups are empty
        assertNull(dataStore.getString(TagGroupServiceDelegate.PENDING_CHANNEL_ADD_TAG_GROUPS_KEY, null));
        assertNull(dataStore.getString(TagGroupServiceDelegate.PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test update named user tags succeeds if the status is 200 and clears pending tags.
     */
    @Test
    public void testUpdateNamedUserTagsSucceed() {
        // Return a named user ID
        when(namedUser.getId()).thenReturn("namedUserId");

        // Set up a 200 response
        Response response = Mockito.mock(Response.class);
        when(tagGroupsClient.updateNamedUserTags("namedUserId", addTagsMap, removeTagsMap)).thenReturn(response);
        when(response.getStatus()).thenReturn(200);

        // Perform the update
        Intent intent = new Intent(PushService.ACTION_UPDATE_NAMED_USER_TAGS);
        intent.putExtra(PushService.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(PushService.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        delegate.onHandleIntent(intent);

        // Verify updateNamedUserTags called
        Mockito.verify(tagGroupsClient).updateNamedUserTags("namedUserId", addTagsMap, removeTagsMap);

        // Verify pending tag groups are empty
        assertNull(dataStore.getString(TagGroupServiceDelegate.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, null));
        assertNull(dataStore.getString(TagGroupServiceDelegate.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test update named user tags without named user ID fails and save pending tags.
     */
    @Test
    public void testUpdateNamedUserTagsNoNamedUser() throws JsonException {
        // Return a null named user ID
        when(namedUser.getId()).thenReturn(null);

        // Perform the update
        Intent intent = new Intent(PushService.ACTION_UPDATE_NAMED_USER_TAGS);
        intent.putExtra(PushService.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(PushService.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        delegate.onHandleIntent(intent);

        // Verify updateNamedUserTags not called when channel ID doesn't exist
        verifyZeroInteractions(tagGroupsClient);

        // Verify pending tags saved
        assertEquals(JsonValue.wrap(addTagsMap).toString(), dataStore.getString(TagGroupServiceDelegate.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, null));
        assertEquals(JsonValue.wrap(removeTagsMap).toString(), dataStore.getString(TagGroupServiceDelegate.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test update named user tags fails if the status is 500 and save pending tags.
     */
    @Test
    public void testUpdateNamedUserTagsServerError() throws JsonException {
        // Return a named user ID
        when(namedUser.getId()).thenReturn("namedUserId");

        // Set up a 500 response
        Response response = Mockito.mock(Response.class);
        when(tagGroupsClient.updateNamedUserTags("namedUserId", addTagsMap, removeTagsMap)).thenReturn(response);
        when(response.getStatus()).thenReturn(500);

        // Perform the update
        Intent intent = new Intent(PushService.ACTION_UPDATE_NAMED_USER_TAGS);
        intent.putExtra(PushService.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(PushService.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        delegate.onHandleIntent(intent);

        // Verify updateNamedUserTags called
        Mockito.verify(tagGroupsClient).updateNamedUserTags("namedUserId", addTagsMap, removeTagsMap);

        // Verify pending tags saved
        assertEquals(JsonValue.wrap(addTagsMap).toString(), dataStore.getString(TagGroupServiceDelegate.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, null));
        assertEquals(JsonValue.wrap(removeTagsMap).toString(), dataStore.getString(TagGroupServiceDelegate.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test don't update named user tags if both pendingAddTags and pendingRemoveTags are empty.
     */
    @Test
    public void testNoUpdateNamedUserWithEmptyTags() {
        // Return a named user ID
        when(namedUser.getId()).thenReturn("namedUserId");
        Bundle emptyTagsBundle = new Bundle();

        // Perform an update without specify new tags
        Intent intent = new Intent(PushService.ACTION_UPDATE_NAMED_USER_TAGS);
        delegate.onHandleIntent(intent);

        // Verify it didn't cause a client update
        verifyZeroInteractions(tagGroupsClient);
    }

    /**
     * Test update named user tags fails if the status is 400 and clears pending tags.
     */
    @Test
    public void testUpdateNamedUserTagsBadRequest() {

        // Return a named user ID
        when(namedUser.getId()).thenReturn("namedUserId");

        // Set up a 400 response
        Response response = Mockito.mock(Response.class);
        when(tagGroupsClient.updateNamedUserTags("namedUserId", addTagsMap, removeTagsMap)).thenReturn(response);
        when(response.getStatus()).thenReturn(400);

        // Perform the update
        Intent intent = new Intent(PushService.ACTION_UPDATE_NAMED_USER_TAGS);
        intent.putExtra(PushService.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(PushService.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        delegate.onHandleIntent(intent);

        // Verify updateNamedUserTags called
        Mockito.verify(tagGroupsClient).updateNamedUserTags("namedUserId", addTagsMap, removeTagsMap);

        // Verify pending tag groups are empty
        assertNull(dataStore.getString(TagGroupServiceDelegate.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, null));
        assertNull(dataStore.getString(TagGroupServiceDelegate.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test clear pending named user tags.
     */
    @Test
    public void testClearPendingNamedUserTags() throws JsonException {
        // Set non-empty pending tags
        dataStore.put(TagGroupServiceDelegate.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, JsonValue.wrap(addTagsMap).toString());
        dataStore.put(TagGroupServiceDelegate.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, JsonValue.wrap(removeTagsMap).toString());

        // Perform the update
        Intent intent = new Intent(PushService.ACTION_CLEAR_PENDING_NAMED_USER_TAGS);
        delegate.onHandleIntent(intent);

        // Verify pending tag groups are empty
        assertNull(dataStore.getString(TagGroupServiceDelegate.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, null));
        assertNull(dataStore.getString(TagGroupServiceDelegate.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, null));
    }
}
