/* Copyright Airship and Contributors */

package com.urbanairship.push;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonMap;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushUser;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class PushManagerJobHandlerTest extends BaseTestCase {

    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeResponseBody = JsonMap.newBuilder()
                                                   .put("channel_id", "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE")
                                                   .build()
                                                   .toString();

    private PreferenceDataStore dataStore;
    private PushManager pushManager;
    private ChannelApiClient client;
    private PushManagerJobHandler jobHandler;
    private RichPushInbox richPushInbox;
    private RichPushUser richPushUser;
    private TagGroupRegistrar tagGroupRegistrar;

    @Before
    public void setUp() {
        client = mock(ChannelApiClient.class);
        richPushInbox = mock(RichPushInbox.class);
        TestApplication.getApplication().setInbox(richPushInbox);

        richPushUser = mock(RichPushUser.class);
        when(richPushInbox.getUser()).thenReturn(richPushUser);

        pushManager = UAirship.shared().getPushManager();
        dataStore = TestApplication.getApplication().preferenceDataStore;

        tagGroupRegistrar = mock(TagGroupRegistrar.class);

        // Extend it to make handleIntent public so we can call it directly
        jobHandler = new PushManagerJobHandler(TestApplication.getApplication(), UAirship.shared(),
                TestApplication.getApplication().preferenceDataStore, tagGroupRegistrar, client);
    }

    /**
     * Test update registration will create a new channel for Amazon platform
     */
    @Test
    public void testUpdateRegistrationCreateChannelAmazon() {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);

        // Verify channel doesn't exist in preferences
        assertNull("Channel ID should be null in preferences", pushManager.getChannelId());

        // Set up channel response
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_CREATED);
        when(response.getResponseBody()).thenReturn(fakeResponseBody);

        // Ensure payload is different, so we don't get a null payload
        pushManager.editTags().addTag("someTag").apply();
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(PushManagerJobHandler.ACTION_UPDATE_CHANNEL_REGISTRATION)
                                 .build();

        assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));

        assertEquals("Channel ID should exist in preferences", fakeChannelId, pushManager.getChannelId());
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

        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        JobInfo jobInfo = JobInfo.newBuilder().setAction(PushManagerJobHandler.ACTION_UPDATE_CHANNEL_REGISTRATION).build();
        assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));

        assertEquals("Channel ID should match in preferences", fakeChannelId, pushManager.getChannelId());

        // Verify we update the user
        verify(richPushInbox.getUser()).update(true);
    }

    /**
     * Test creating channel returns a retry when the status code is 429.
     */
    @Test
    public void testCreateChannelTooManyRequests() {
        // Set up channel response
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(Response.HTTP_TOO_MANY_REQUESTS);

        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        JobInfo jobInfo = JobInfo.newBuilder().setAction(PushManagerJobHandler.ACTION_UPDATE_CHANNEL_REGISTRATION).build();
        assertEquals(JobInfo.JOB_RETRY, jobHandler.performJob(jobInfo));
    }

    /**
     * Test update registration fail to create a channel when channel response code is not successful
     */
    @Test
    public void testUpdateRegistrationResponseCodeFail() {
        // Verify channel doesn't exist in preferences
        assertNull("Channel ID should be null in preferences", pushManager.getChannelId());

        // Set up channel response
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);
        when(response.getResponseBody()).thenReturn(fakeResponseBody);

        // Ensure payload is different, so we don't get a null payload
        pushManager.editTags().addTag("someTag").apply();
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        JobInfo jobInfo = JobInfo.newBuilder().setAction(PushManagerJobHandler.ACTION_UPDATE_CHANNEL_REGISTRATION).build();
        assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));

        // Verify channel creation failed
        assertNull("Channel ID should be null in preferences", pushManager.getChannelId());
    }

    /**
     * Test update registration returns a retry when the status is 429.
     */
    @Test
    public void testUpdateRegistrationTooManyRequests() {
        // Verify channel doesn't exist in preferences
        assertNull("Channel ID should be null in preferences", pushManager.getChannelId());

        // Set up channel response
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(Response.HTTP_TOO_MANY_REQUESTS);

        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        JobInfo jobInfo = JobInfo.newBuilder().setAction(PushManagerJobHandler.ACTION_UPDATE_CHANNEL_REGISTRATION).build();
        assertEquals(JobInfo.JOB_RETRY, jobHandler.performJob(jobInfo));
    }

    /**
     * Test update registration fail to create a channel when channel ID from response is null
     */
    @Test
    public void testUpdateRegistrationResponseNullChannelId() {
        // Verify channel doesn't exist in preferences
        assertNull("Channel ID should be null in preferences", pushManager.getChannelId());

        // Set up channel response
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_CREATED);
        when(response.getResponseBody()).thenReturn(null);

        // Ensure payload is different, so we don't get a null payload
        pushManager.editTags().addTag("someTag").apply();
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.createChannelWithPayload(payload)).thenReturn(response);

        JobInfo jobInfo = JobInfo.newBuilder().setAction(PushManagerJobHandler.ACTION_UPDATE_CHANNEL_REGISTRATION).build();
        assertEquals(JobInfo.JOB_RETRY, jobHandler.performJob(jobInfo));

        // Verify channel creation failed
        assertNull("Channel ID should be null in preferences", pushManager.getChannelId());
    }

    /**
     * Test updating a channel succeeds
     */
    @Test
    public void testUpdateChannelSucceed() throws MalformedURLException {
        // Set Channel ID
        pushManager.onChannelCreated(fakeChannelId);

        assertEquals("Channel ID should exist in preferences", pushManager.getChannelId(), fakeChannelId);

        long lastRegistrationTime = dataStore.getLong("com.urbanairship.push.LAST_REGISTRATION_TIME", 0);

        // Set up channel response
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_OK);

        // Ensure payload is different, so we don't get a null payload
        pushManager.editTags().addTag("someTag").apply();
        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();

        // Return the response
        when(client.updateChannelWithPayload(fakeChannelId, payload)).thenReturn(response);

        JobInfo jobInfo = JobInfo.newBuilder().setAction(PushManagerJobHandler.ACTION_UPDATE_CHANNEL_REGISTRATION).build();
        assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));

        // Verify channel update succeeded
        assertNotSame("Last registration time should be updated", dataStore.getLong("com.urbanairship.push.LAST_REGISTRATION_TIME", 0), lastRegistrationTime);
    }

    /**
     * Test updating channel returns a 409 recreates the channel.
     */
    @Test
    public void testChannelConflict() throws MalformedURLException {
        // Set Channel ID
        pushManager.onChannelCreated(fakeChannelId);

        // Set up a conflict response
        Response conflictResponse = mock(Response.class);
        when(conflictResponse.getStatus()).thenReturn(HttpURLConnection.HTTP_CONFLICT);
        when(client.updateChannelWithPayload(Mockito.eq(new String(fakeChannelId)), Mockito.any(ChannelRegistrationPayload.class))).thenReturn(conflictResponse);

        JobInfo jobInfo = JobInfo.newBuilder().setAction(PushManagerJobHandler.ACTION_UPDATE_CHANNEL_REGISTRATION).build();
        assertEquals(JobInfo.JOB_RETRY, jobHandler.performJob(jobInfo));

        // Verify update was called
        Mockito.verify(client).updateChannelWithPayload(Mockito.eq(new String(fakeChannelId)), Mockito.any(ChannelRegistrationPayload.class));

        // Verify the channel was cleared
        assertNull("Channel ID should be null", pushManager.getChannelId());
    }

    /**
     * Test update named user tags succeeds when the registrar returns true.
     */
    @Test
    public void testUpdateNamedUserTagsSucceed() {
        // Set the channel
        pushManager.onChannelCreated(fakeChannelId);

        when(tagGroupRegistrar.uploadMutations(TagGroupRegistrar.CHANNEL, fakeChannelId)).thenReturn(true);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(PushManagerJobHandler.ACTION_UPDATE_TAG_GROUPS).build();
        Assert.assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));
    }

    /**
     * Test update tags without a channel ID fails.
     */
    @Test
    public void testUpdateTagsNoChannel() {
        // Set the channel
        pushManager.clearChannel();

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(PushManagerJobHandler.ACTION_UPDATE_TAG_GROUPS).build();
        Assert.assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));

        // Verify tag group registrar was not called
        verifyZeroInteractions(tagGroupRegistrar);
    }

    /**
     * Test update named user retries when the upload fails.
     */
    @Test
    public void testUpdateTagsRetry() {
        // Set the channel
        pushManager.onChannelCreated(fakeChannelId);

        when(tagGroupRegistrar.uploadMutations(TagGroupRegistrar.CHANNEL, fakeChannelId)).thenReturn(false);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(PushManagerJobHandler.ACTION_UPDATE_TAG_GROUPS).build();
        Assert.assertEquals(JobInfo.JOB_RETRY, jobHandler.performJob(jobInfo));
    }

}
