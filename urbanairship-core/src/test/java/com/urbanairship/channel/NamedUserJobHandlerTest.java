/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.channel.NamedUser;
import com.urbanairship.channel.NamedUserApiClient;
import com.urbanairship.channel.NamedUserJobHandler;
import com.urbanairship.channel.TagGroupRegistrar;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobInfo;
import com.urbanairship.push.PushManager;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;

import java.net.HttpURLConnection;
import java.util.UUID;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class NamedUserJobHandlerTest extends BaseTestCase {

    private NamedUserApiClient namedUserClient;
    private NamedUser namedUser;
    private AirshipChannel airshipChannel;
    private PreferenceDataStore dataStore;
    private NamedUserJobHandler jobHandler;
    private TagGroupRegistrar tagGroupRegistrar;

    private String changeToken;

    @Before
    public void setup() {
        namedUserClient = Mockito.mock(NamedUserApiClient.class);
        namedUser = Mockito.mock(NamedUser.class);
        airshipChannel = Mockito.mock(AirshipChannel.class);

        TestApplication.getApplication().setNamedUser(namedUser);
        TestApplication.getApplication().setChannel(airshipChannel);

        dataStore = TestApplication.getApplication().preferenceDataStore;

        changeToken = UUID.randomUUID().toString();
        when(namedUser.getChangeToken()).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return changeToken;
            }
        });

        tagGroupRegistrar = Mockito.mock(TagGroupRegistrar.class);

        jobHandler = new NamedUserJobHandler(UAirship.shared(), dataStore, tagGroupRegistrar, namedUserClient);

        Shadows.shadowOf(RuntimeEnvironment.application).clearStartedServices();
    }

    /**
     * Test associate named user succeeds if the status is 2xx.
     */
    @Test
    public void testAssociateNamedUserSucceed() {
        when(airshipChannel.getId()).thenReturn("channelID");
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
        when(airshipChannel.getId()).thenReturn("channelID");
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
        when(airshipChannel.getId()).thenReturn("channelID");
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
     * Test associate named user retries if the status is 429
     */
    @Test
    public void testAssociateNamedUserTooManyRequests() {
        when(airshipChannel.getId()).thenReturn("channelID");
        when(namedUser.getId()).thenReturn("namedUserID");

        // Set up a 429 response
        Response response = Mockito.mock(Response.class);
        when(response.getStatus()).thenReturn(Response.HTTP_TOO_MANY_REQUESTS);
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
        when(airshipChannel.getId()).thenReturn("channelID");
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
        when(airshipChannel.getId()).thenReturn("channelID");
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
        when(airshipChannel.getId()).thenReturn("channelID");
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
     * Test associate named user retries if the status is 429
     */
    @Test
    public void testDisassociateNamedUserTooManyRequests() {
        when(airshipChannel.getId()).thenReturn("channelID");
        when(namedUser.getId()).thenReturn(null);

        // Set up a 429 response
        Response response = Mockito.mock(Response.class);
        when(response.getStatus()).thenReturn(Response.HTTP_TOO_MANY_REQUESTS);
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
        when(airshipChannel.getId()).thenReturn(null);
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
        when(airshipChannel.getId()).thenReturn(null);
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
     * Test update named user tags succeeds when the registrar returns true.
     */
    @Test
    public void testUpdateNamedUserTagsSucceed() {
        // Return a named user ID
        when(namedUser.getId()).thenReturn("namedUserId");

        when(tagGroupRegistrar.uploadMutations(TagGroupRegistrar.NAMED_USER, "namedUserId")).thenReturn(true);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUserJobHandler.ACTION_UPDATE_TAG_GROUPS).build();
        Assert.assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));
    }

    /**
     * Test update named user tags without named user ID fails.
     */
    @Test
    public void testUpdateNamedUserTagsNoNamedUser() {
        // Return a null named user ID
        when(namedUser.getId()).thenReturn(null);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUserJobHandler.ACTION_UPDATE_TAG_GROUPS).build();
        Assert.assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));

        // Verify updateNamedUserTags not called when channel ID doesn't exist
        verifyZeroInteractions(tagGroupRegistrar);
    }

    /**
     * Test update named user retries when the upload fails.
     */
    @Test
    public void testUpdateNamedUserTagsRetry() {
        // Return a named user ID
        when(namedUser.getId()).thenReturn("namedUserId");

        // Provide pending changes
        when(tagGroupRegistrar.uploadMutations(TagGroupRegistrar.NAMED_USER, "namedUserId")).thenReturn(false);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUserJobHandler.ACTION_UPDATE_TAG_GROUPS).build();
        Assert.assertEquals(JobInfo.JOB_RETRY, jobHandler.performJob(jobInfo));
    }

}
