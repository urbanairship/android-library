/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;
import com.urbanairship.job.Job;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class NamedUserIntentHandlerTest extends BaseTestCase {

    private NamedUserApiClient namedUserClient;
    private NamedUser namedUser;
    private PushManager pushManager;
    private PreferenceDataStore dataStore;
    private NamedUserIntentHandler intentHandler;

    private String changeToken;

    private Map<String, Set<String>> addTagsMap;
    private Map<String, Set<String>> removeTagsMap;
    private Bundle addTagsBundle;
    private Bundle removeTagsBundle;
    private JobDispatcher mockDispatcher;

    @Before
    public void setup() {
        mockDispatcher = mock(JobDispatcher.class);
        namedUserClient = Mockito.mock(NamedUserApiClient.class);
        namedUser = Mockito.mock(NamedUser.class);
        pushManager = Mockito.mock(PushManager.class);

        TestApplication.getApplication().setNamedUser(namedUser);
        TestApplication.getApplication().setPushManager(pushManager);

        dataStore = TestApplication.getApplication().preferenceDataStore;

        changeToken = UUID.randomUUID().toString();
        when(namedUser.getChangeToken()).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return changeToken;
            }
        });

        intentHandler = new NamedUserIntentHandler(TestApplication.getApplication(), UAirship.shared(),
                dataStore, mockDispatcher, namedUserClient);

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

        Shadows.shadowOf(RuntimeEnvironment.application).clearStartedServices();
    }

    /**
     * Test associate named user succeeds if the status is 2xx.
     */
    @Test
    public void testAssociateNamedUserSucceed() {
        when(pushManager.getChannelId()).thenReturn("channelID");
        when(namedUser.getId()).thenReturn("namedUserID");

        for (int statusCode = 200; statusCode < 300; statusCode++) {
            // Set a new change token to force an update
            changeToken = UUID.randomUUID().toString();

            // Set up a 2xx response
            Response response = Mockito.mock(Response.class);
            when(namedUserClient.associate("namedUserID", "channelID")).thenReturn(response);
            when(response.getStatus()).thenReturn(statusCode);

            // Perform the update
            Job job = Job.newBuilder(NamedUserIntentHandler.ACTION_UPDATE_NAMED_USER).build();
            Assert.assertEquals(Job.JOB_FINISHED, intentHandler.performJob(job));

            // Verify the update was performed
            verify(namedUserClient).associate("namedUserID", "channelID");

            // Verify the last change token was updated
            assertEquals(changeToken, dataStore.getString(NamedUserIntentHandler.LAST_UPDATED_TOKEN_KEY, null));

            // Reset the mocks so we can verify again
            reset(namedUserClient);
        }
    }

    /**
     * Test associate named user fails if the status is 403
     */
    @Test
    public void testAssociateNamedUserFailed() {
        when(pushManager.getChannelId()).thenReturn("channelID");
        when(namedUser.getId()).thenReturn("namedUserID");

        // Set up a 403 response
        Response response = Mockito.mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_FORBIDDEN);
        when(namedUserClient.associate("namedUserID", "channelID")).thenReturn(response);

        // Perform the update
        Job job = Job.newBuilder(NamedUserIntentHandler.ACTION_UPDATE_NAMED_USER).build();
        Assert.assertEquals(Job.JOB_FINISHED, intentHandler.performJob(job));

        // Verify the update was performed
        verify(namedUserClient).associate("namedUserID", "channelID");

        // Verify the last change token was not updated
        assertNotEquals(changeToken, dataStore.getString(NamedUserIntentHandler.LAST_UPDATED_TOKEN_KEY, null));
    }

    /**
     * Test associate named user fails if the status is 500
     */
    @Test
    public void testAssociateNamedUserFailedRetry() {
        when(pushManager.getChannelId()).thenReturn("channelID");
        when(namedUser.getId()).thenReturn("namedUserID");

        // Set up a 500 response
        Response response = Mockito.mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_INTERNAL_ERROR);
        when(namedUserClient.associate("namedUserID", "channelID")).thenReturn(response);

        // Perform the update
        Job job = Job.newBuilder(NamedUserIntentHandler.ACTION_UPDATE_NAMED_USER).build();
        Assert.assertEquals(Job.JOB_RETRY, intentHandler.performJob(job));

        // Verify the update was performed
        verify(namedUserClient).associate("namedUserID", "channelID");

        // Verify the last change token was not updated
        assertNotEquals(changeToken, dataStore.getString(NamedUserIntentHandler.LAST_UPDATED_TOKEN_KEY, null));
    }

    /**
     * Test disassociate named user succeeds if the status is 2xx.
     */
    @Test
    public void testDisassociateNamedUserSucceed() {
        when(pushManager.getChannelId()).thenReturn("channelID");
        when(namedUser.getId()).thenReturn(null);

        for (int statusCode = 200; statusCode < 300; statusCode++) {
            // Set a new change token to force an update
            changeToken = UUID.randomUUID().toString();

            // Set up a 2xx response
            Response response = Mockito.mock(Response.class);
            when(namedUserClient.disassociate("channelID")).thenReturn(response);
            when(response.getStatus()).thenReturn(statusCode);

            // Perform the update
            Job job = Job.newBuilder(NamedUserIntentHandler.ACTION_UPDATE_NAMED_USER).build();
            Assert.assertEquals(Job.JOB_FINISHED, intentHandler.performJob(job));

            // Verify the update was performed
            verify(namedUserClient).disassociate("channelID");

            // Verify the last change token was updated
            assertEquals(changeToken, dataStore.getString(NamedUserIntentHandler.LAST_UPDATED_TOKEN_KEY, null));

            // Reset the mocks so we can verify again
            reset(namedUserClient);
        }
    }

    /**
     * Test disassociate named user fails if status is not 200.
     */
    @Test
    public void testDisassociateNamedUserFailed() {
        when(pushManager.getChannelId()).thenReturn("channelID");
        when(namedUser.getId()).thenReturn(null);

        // Set up a 404 response
        Response response = Mockito.mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);
        when(namedUserClient.disassociate("channelID")).thenReturn(response);

        // Perform the update
        Job job = Job.newBuilder(NamedUserIntentHandler.ACTION_UPDATE_NAMED_USER).build();
        Assert.assertEquals(Job.JOB_FINISHED, intentHandler.performJob(job));

        // Verify the update was performed
        verify(namedUserClient).disassociate("channelID");

        // Verify the last change token was not updated
        assertNotEquals(changeToken, dataStore.getString(NamedUserIntentHandler.LAST_UPDATED_TOKEN_KEY, null));
    }

    /**
     * Test associate named user fails if the status is 500
     */
    @Test
    public void testDisassociateNamedUserFailedRetry() {
        when(pushManager.getChannelId()).thenReturn("channelID");
        when(namedUser.getId()).thenReturn(null);

        // Set up a 500 response
        Response response = Mockito.mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_INTERNAL_ERROR);
        when(namedUserClient.disassociate("channelID")).thenReturn(response);

        // Perform the update
        Job job = Job.newBuilder(NamedUserIntentHandler.ACTION_UPDATE_NAMED_USER).build();
        Assert.assertEquals(Job.JOB_RETRY, intentHandler.performJob(job));

        // Verify the update was performed
        verify(namedUserClient).disassociate("channelID");

        // Verify the last change token was not updated
        assertNotEquals(changeToken, dataStore.getString(NamedUserIntentHandler.LAST_UPDATED_TOKEN_KEY, null));
    }

    /**
     * Test associate without channel fails.
     */
    @Test
    public void testAssociateNamedUserFailedNoChannel() {
        when(pushManager.getChannelId()).thenReturn(null);
        when(namedUser.getId()).thenReturn("namedUserID");

        // Perform the update
        Job job = Job.newBuilder(NamedUserIntentHandler.ACTION_UPDATE_NAMED_USER).build();
        Assert.assertEquals(Job.JOB_FINISHED, intentHandler.performJob(job));

        // Verify associate not called when channel ID doesn't exist
        verifyZeroInteractions(namedUserClient);

        // Verify the last change token was not updated
        assertNotEquals(changeToken, dataStore.getString(NamedUserIntentHandler.LAST_UPDATED_TOKEN_KEY, null));
    }

    /**
     * Test disassociate without channel fails.
     */
    @Test
    public void testDisassociateNamedUserFailedNoChannel() {
        when(pushManager.getChannelId()).thenReturn(null);
        when(namedUser.getId()).thenReturn(null);

        // Perform the update
        Job job = Job.newBuilder(NamedUserIntentHandler.ACTION_UPDATE_NAMED_USER).build();
        Assert.assertEquals(Job.JOB_FINISHED, intentHandler.performJob(job));

        // Verify associate not called when channel ID doesn't exist
        verifyZeroInteractions(namedUserClient);

        // Verify the last change token was not updated
        assertNotEquals(changeToken, dataStore.getString(NamedUserIntentHandler.LAST_UPDATED_TOKEN_KEY, null));
    }

    /**
     * Test apply tag group changes updates the pending tag groups.
     */
    @Test
    public void testApplyTagGroupChanges() throws JsonException {
        // Apply tag groups
        Job job = Job.newBuilder(NamedUserIntentHandler.ACTION_APPLY_TAG_GROUP_CHANGES)
                     .putExtra(TagGroupsEditor.EXTRA_ADD_TAG_GROUPS, addTagsBundle)
                     .putExtra(TagGroupsEditor.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle)
                     .build();

        Assert.assertEquals(Job.JOB_FINISHED, intentHandler.performJob(job));


        // Verify pending tags are saved
        assertEquals(JsonValue.wrap(addTagsMap).toString(), dataStore.getString(NamedUserIntentHandler.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, null));
        assertEquals(JsonValue.wrap(removeTagsMap).toString(), dataStore.getString(NamedUserIntentHandler.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test apply tag group changes schedules a tag group update request.
     */
    @Test
    public void testApplyTagGroupChangesSchedulesUpload() throws JsonException {
        // Return a named user ID
        when(namedUser.getId()).thenReturn("namedUserId");

        // Apply tag groups
        Job job = Job.newBuilder(NamedUserIntentHandler.ACTION_APPLY_TAG_GROUP_CHANGES)
                     .putExtra(TagGroupsEditor.EXTRA_ADD_TAG_GROUPS, addTagsBundle)
                     .putExtra(TagGroupsEditor.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle)
                     .build();

        Assert.assertEquals(Job.JOB_FINISHED, intentHandler.performJob(job));

        // Verify pending tags are saved
        assertEquals(JsonValue.wrap(addTagsMap).toString(), dataStore.getString(NamedUserIntentHandler.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, null));
        assertEquals(JsonValue.wrap(removeTagsMap).toString(), dataStore.getString(NamedUserIntentHandler.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, null));

        // Verify a new job to update tag group registration is dispatched
        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<Job>() {
            @Override
            public boolean matches(Object argument) {
                Job job = (Job) argument;
                return job.getAction().equals(NamedUserIntentHandler.ACTION_UPDATE_TAG_GROUPS);
            }
        }));
    }

    /**
     * Test update named user tags succeeds if the status is 200 and clears pending tags.
     */
    @Test
    public void testUpdateNamedUserTagsSucceed() throws JsonException {
        // Return a named user ID
        when(namedUser.getId()).thenReturn("namedUserId");

        // Provide pending changes
        dataStore.put(NamedUserIntentHandler.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, JsonValue.wrap(addTagsMap).toString());
        dataStore.put(NamedUserIntentHandler.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, JsonValue.wrap(removeTagsMap).toString());

        // Set up a 200 response
        Response response = Mockito.mock(Response.class);
        when(namedUserClient.updateTagGroups("namedUserId", addTagsMap, removeTagsMap)).thenReturn(response);
        when(response.getStatus()).thenReturn(200);

        // Perform the update
        Job job = Job.newBuilder(NamedUserIntentHandler.ACTION_UPDATE_TAG_GROUPS).build();
        Assert.assertEquals(Job.JOB_FINISHED, intentHandler.performJob(job));

        // Verify updateNamedUserTags called
        Mockito.verify(namedUserClient).updateTagGroups("namedUserId", addTagsMap, removeTagsMap);

        // Verify pending tag groups are empty
        assertNull(dataStore.getString(NamedUserIntentHandler.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, null));
        assertNull(dataStore.getString(NamedUserIntentHandler.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test update named user tags without named user ID fails and save pending tags.
     */
    @Test
    public void testUpdateNamedUserTagsNoNamedUser() throws JsonException {
        // Return a null named user ID
        when(namedUser.getId()).thenReturn(null);

        // Provide pending changes
        dataStore.put(NamedUserIntentHandler.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, JsonValue.wrap(addTagsMap).toString());
        dataStore.put(NamedUserIntentHandler.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, JsonValue.wrap(removeTagsMap).toString());

        // Perform the update
        Job job = Job.newBuilder(NamedUserIntentHandler.ACTION_UPDATE_TAG_GROUPS).build();
        Assert.assertEquals(Job.JOB_FINISHED, intentHandler.performJob(job));

        // Verify updateNamedUserTags not called when channel ID doesn't exist
        verifyZeroInteractions(namedUserClient);
    }

    /**
     * Test update named user tags fails if the status is 500.
     */
    @Test
    public void testUpdateNamedUserTagsServerError() throws JsonException {
        // Return a named user ID
        when(namedUser.getId()).thenReturn("namedUserId");

        // Provide pending changes
        dataStore.put(NamedUserIntentHandler.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, JsonValue.wrap(addTagsMap).toString());
        dataStore.put(NamedUserIntentHandler.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, JsonValue.wrap(removeTagsMap).toString());

        // Set up a 500 response
        Response response = Mockito.mock(Response.class);
        when(namedUserClient.updateTagGroups("namedUserId", addTagsMap, removeTagsMap)).thenReturn(response);
        when(response.getStatus()).thenReturn(500);

        // Perform the update
        Job job = Job.newBuilder(NamedUserIntentHandler.ACTION_UPDATE_TAG_GROUPS).build();
        Assert.assertEquals(Job.JOB_RETRY, intentHandler.performJob(job));

        // Verify updateNamedUserTags called
        Mockito.verify(namedUserClient).updateTagGroups("namedUserId", addTagsMap, removeTagsMap);

        // Verify pending tags persist
        assertEquals(JsonValue.wrap(addTagsMap).toString(), dataStore.getString(NamedUserIntentHandler.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, null));
        assertEquals(JsonValue.wrap(removeTagsMap).toString(), dataStore.getString(NamedUserIntentHandler.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test don't update named user tags if both pendingAddTags and pendingRemoveTags are empty.
     */
    @Test
    public void testNoUpdateNamedUserWithEmptyTags() {
        // Return a named user ID
        when(namedUser.getId()).thenReturn("namedUserId");

        // Clear pending changes
        dataStore.remove(NamedUserIntentHandler.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY);
        dataStore.remove(NamedUserIntentHandler.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY);

        // Perform the update
        Job job = Job.newBuilder(NamedUserIntentHandler.ACTION_UPDATE_TAG_GROUPS).build();
        Assert.assertEquals(Job.JOB_FINISHED, intentHandler.performJob(job));

        // Verify it didn't cause a client update
        verifyZeroInteractions(namedUserClient);
    }

    /**
     * Test update named user tags fails if the status is 400 and clears pending tags.
     */
    @Test
    public void testUpdateNamedUserTagsBadRequest() throws JsonException {
        // Return a named user ID
        when(namedUser.getId()).thenReturn("namedUserId");

        // Provide pending changes
        dataStore.put(NamedUserIntentHandler.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, JsonValue.wrap(addTagsMap).toString());
        dataStore.put(NamedUserIntentHandler.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, JsonValue.wrap(removeTagsMap).toString());

        // Set up a 400 response
        Response response = Mockito.mock(Response.class);
        when(namedUserClient.updateTagGroups("namedUserId", addTagsMap, removeTagsMap)).thenReturn(response);
        when(response.getStatus()).thenReturn(400);

        // Perform the update
        Job job = Job.newBuilder(NamedUserIntentHandler.ACTION_UPDATE_TAG_GROUPS).build();
        Assert.assertEquals(Job.JOB_FINISHED, intentHandler.performJob(job));

        // Verify updateNamedUserTags called
        Mockito.verify(namedUserClient).updateTagGroups("namedUserId", addTagsMap, removeTagsMap);

        // Verify pending tag groups are empty
        assertNull(dataStore.getString(NamedUserIntentHandler.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, null));
        assertNull(dataStore.getString(NamedUserIntentHandler.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test clear pending named user tags.
     */
    @Test
    public void testClearPendingNamedUserTags() throws JsonException {
        // Set non-empty pending tags
        dataStore.put(NamedUserIntentHandler.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, JsonValue.wrap(addTagsMap).toString());
        dataStore.put(NamedUserIntentHandler.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, JsonValue.wrap(removeTagsMap).toString());

        // Perform the update
        Job job = Job.newBuilder(NamedUserIntentHandler.ACTION_CLEAR_PENDING_NAMED_USER_TAGS).build();
        Assert.assertEquals(Job.JOB_FINISHED, intentHandler.performJob(job));

        // Verify pending tag groups are empty
        assertNull(dataStore.getString(NamedUserIntentHandler.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, null));
        assertNull(dataStore.getString(NamedUserIntentHandler.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, null));
    }
}
