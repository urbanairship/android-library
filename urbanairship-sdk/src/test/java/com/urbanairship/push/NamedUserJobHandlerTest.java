/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import com.google.common.collect.Lists;
import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;

import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.UUID;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class NamedUserJobHandlerTest extends BaseTestCase {

    private NamedUserApiClient namedUserClient;
    private NamedUser namedUser;
    private PushManager pushManager;
    private PreferenceDataStore dataStore;
    private NamedUserJobHandler jobHandler;

    private String changeToken;

    @Before
    public void setup() {
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

        TagGroupMutationStore tagGroupStore = new TagGroupMutationStore(dataStore, "test");
        when(namedUser.getTagGroupStore()).thenReturn(tagGroupStore);

        jobHandler = new NamedUserJobHandler(UAirship.shared(), dataStore, namedUserClient);

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
            JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUserJobHandler.ACTION_UPDATE_NAMED_USER).build();
            Assert.assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));

            // Verify the update was performed
            verify(namedUserClient).associate("namedUserID", "channelID");

            // Verify the last change token was updated
            assertEquals(changeToken, dataStore.getString(NamedUserJobHandler.LAST_UPDATED_TOKEN_KEY, null));

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
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUserJobHandler.ACTION_UPDATE_NAMED_USER).build();
        Assert.assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));

        // Verify the update was performed
        verify(namedUserClient).associate("namedUserID", "channelID");

        // Verify the last change token was not updated
        assertNotEquals(changeToken, dataStore.getString(NamedUserJobHandler.LAST_UPDATED_TOKEN_KEY, null));
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
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUserJobHandler.ACTION_UPDATE_NAMED_USER).build();
        Assert.assertEquals(JobInfo.JOB_RETRY, jobHandler.performJob(jobInfo));

        // Verify the update was performed
        verify(namedUserClient).associate("namedUserID", "channelID");

        // Verify the last change token was not updated
        assertNotEquals(changeToken, dataStore.getString(NamedUserJobHandler.LAST_UPDATED_TOKEN_KEY, null));
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
            JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUserJobHandler.ACTION_UPDATE_NAMED_USER).build();
            Assert.assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));

            // Verify the update was performed
            verify(namedUserClient).disassociate("channelID");

            // Verify the last change token was updated
            assertEquals(changeToken, dataStore.getString(NamedUserJobHandler.LAST_UPDATED_TOKEN_KEY, null));

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
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUserJobHandler.ACTION_UPDATE_NAMED_USER).build();
        Assert.assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));

        // Verify the update was performed
        verify(namedUserClient).disassociate("channelID");

        // Verify the last change token was not updated
        assertNotEquals(changeToken, dataStore.getString(NamedUserJobHandler.LAST_UPDATED_TOKEN_KEY, null));
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
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUserJobHandler.ACTION_UPDATE_NAMED_USER).build();
        Assert.assertEquals(JobInfo.JOB_RETRY, jobHandler.performJob(jobInfo));

        // Verify the update was performed
        verify(namedUserClient).disassociate("channelID");

        // Verify the last change token was not updated
        assertNotEquals(changeToken, dataStore.getString(NamedUserJobHandler.LAST_UPDATED_TOKEN_KEY, null));
    }

    /**
     * Test associate without channel fails.
     */
    @Test
    public void testAssociateNamedUserFailedNoChannel() {
        when(pushManager.getChannelId()).thenReturn(null);
        when(namedUser.getId()).thenReturn("namedUserID");

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUserJobHandler.ACTION_UPDATE_NAMED_USER).build();
        Assert.assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));

        // Verify associate not called when channel ID doesn't exist
        verifyZeroInteractions(namedUserClient);

        // Verify the last change token was not updated
        assertNotEquals(changeToken, dataStore.getString(NamedUserJobHandler.LAST_UPDATED_TOKEN_KEY, null));
    }

    /**
     * Test disassociate without channel fails.
     */
    @Test
    public void testDisassociateNamedUserFailedNoChannel() {
        when(pushManager.getChannelId()).thenReturn(null);
        when(namedUser.getId()).thenReturn(null);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUserJobHandler.ACTION_UPDATE_NAMED_USER).build();
        Assert.assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));

        // Verify associate not called when channel ID doesn't exist
        verifyZeroInteractions(namedUserClient);

        // Verify the last change token was not updated
        assertNotEquals(changeToken, dataStore.getString(NamedUserJobHandler.LAST_UPDATED_TOKEN_KEY, null));
    }


    /**
     * Test update named user tags succeeds if the status is 200 and clears pending tags.
     */
    @Test
    public void testUpdateNamedUserTagsSucceed() throws JsonException {
        // Return a named user ID
        when(namedUser.getId()).thenReturn("namedUserId");

        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));
        TagGroupMutationStore tagGroupStore = namedUser.getTagGroupStore();

        tagGroupStore.clear();
        tagGroupStore.push(mutation);

        // Set up a 200 response
        Response response = Mockito.mock(Response.class);
        when(namedUserClient.updateTagGroups("namedUserId", mutation)).thenReturn(response);
        when(response.getStatus()).thenReturn(200);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUserJobHandler.ACTION_UPDATE_TAG_GROUPS).build();
        Assert.assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));

        // Verify updateNamedUserTags called
        Mockito.verify(namedUserClient).updateTagGroups("namedUserId", mutation);

        // Verify pending tag groups are empty
        assertTrue(tagGroupStore.getMutations().isEmpty());
    }

    /**
     * Test update named user tags without named user ID fails and save pending tags.
     */
    @Test
    public void testUpdateNamedUserTagsNoNamedUser() throws JsonException {
        // Return a null named user ID
        when(namedUser.getId()).thenReturn(null);

        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));
        TagGroupMutationStore tagGroupStore = namedUser.getTagGroupStore();

        tagGroupStore.clear();
        tagGroupStore.push(mutation);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUserJobHandler.ACTION_UPDATE_TAG_GROUPS).build();
        Assert.assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));

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
        TagGroupMutationStore tagGroupStore = namedUser.getTagGroupStore();
        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));

        tagGroupStore.clear();
        tagGroupStore.push(mutation);

        // Set up a 500 response
        Response response = Mockito.mock(Response.class);
        when(namedUserClient.updateTagGroups("namedUserId", mutation)).thenReturn(response);
        when(response.getStatus()).thenReturn(500);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUserJobHandler.ACTION_UPDATE_TAG_GROUPS).build();
        Assert.assertEquals(JobInfo.JOB_RETRY, jobHandler.performJob(jobInfo));

        // Verify updateNamedUserTags called
        Mockito.verify(namedUserClient).updateTagGroups("namedUserId", mutation);

        // Verify pending tags persist
        assertEquals(1, tagGroupStore.getMutations().size());
        assertEquals(mutation, tagGroupStore.getMutations().get(0));
    }

    /**
     * Test don't update named user tags if pending tag group mutations are empty.
     */
    @Test
    public void testNoUpdateNamedUserWithEmptyTags() {
        // Return a named user ID
        when(namedUser.getId()).thenReturn("namedUserId");

        // Clear pending changes
        namedUser.getTagGroupStore().clear();

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUserJobHandler.ACTION_UPDATE_TAG_GROUPS).build();
        Assert.assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));

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
        TagGroupMutationStore tagGroupStore = namedUser.getTagGroupStore();
        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));

        tagGroupStore.clear();
        tagGroupStore.push(mutation);

        // Set up a 400 response
        Response response = Mockito.mock(Response.class);
        when(namedUserClient.updateTagGroups("namedUserId", mutation)).thenReturn(response);
        when(response.getStatus()).thenReturn(400);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUserJobHandler.ACTION_UPDATE_TAG_GROUPS).build();
        Assert.assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));

        // Verify updateNamedUserTags called
        Mockito.verify(namedUserClient).updateTagGroups("namedUserId", mutation);

        // Verify pending tag groups are empty
        assertTrue(tagGroupStore.getMutations().isEmpty());
    }

}
