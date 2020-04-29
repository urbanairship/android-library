/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import android.app.Application;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.net.HttpURLConnection;

import androidx.test.core.app.ApplicationProvider;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

public class NamedUserTest extends BaseTestCase {

    private final String fakeNamedUserId = "fake-named-user-id";

    private NamedUser namedUser;
    private JobDispatcher mockDispatcher;
    private TagGroupRegistrar mockTagGroupRegistrar;
    private AirshipChannel mockChannel;
    private PreferenceDataStore dataStore;
    private NamedUserApiClient mockNamedUserClient;
    private Application application;

    @Before
    public void setUp() {
        application = ApplicationProvider.getApplicationContext();
        mockDispatcher = mock(JobDispatcher.class);
        mockNamedUserClient = Mockito.mock(NamedUserApiClient.class);

        mockTagGroupRegistrar = mock(TagGroupRegistrar.class);
        mockChannel = mock(AirshipChannel.class);

        dataStore = TestApplication.getApplication().preferenceDataStore;
        dataStore.put(UAirship.DATA_COLLECTION_ENABLED_KEY, true);

        namedUser = new NamedUser(TestApplication.getApplication(), dataStore, mockTagGroupRegistrar,
                mockChannel, mockDispatcher, mockNamedUserClient);
    }

    /**
     * Test channel create dispatches a job to update the named user.
     */
    @Test
    public void testChannelCreateUpdatesNamedUser() {
        ArgumentCaptor<AirshipChannelListener> argument = ArgumentCaptor.forClass(AirshipChannelListener.class);
        namedUser.init();
        verify(mockChannel).addChannelListener(argument.capture());
        AirshipChannelListener listener = argument.getValue();
        assertNotNull(listener);

        clearInvocations(mockDispatcher);

        listener.onChannelCreated("some-channel");
        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(NamedUser.ACTION_UPDATE_NAMED_USER);
            }
        }));
    }

    /**
     * Test set valid ID (associate).
     */
    @Test
    public void testSetIDValid() {
        // Make sure we have a pending tag group change
        namedUser.setId(fakeNamedUserId);

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(NamedUser.ACTION_UPDATE_NAMED_USER);
            }
        }));

        assertEquals("Named user ID should be set", fakeNamedUserId, namedUser.getId());
    }

    /**
     * Test set invalid ID.
     */
    @Test
    public void testSetIDInvalid() {
        String currentNamedUserId = namedUser.getId();

        namedUser.setId("     ");
        assertEquals("Named user ID should not have changed", currentNamedUserId, namedUser.getId());
    }

    /**
     * Test set null ID (disassociate).
     */
    @Test
    public void testSetIDNull() {
        // Set an initial id
        namedUser.setId("neat");
        clearInvocations(mockDispatcher, mockTagGroupRegistrar);

        // Clear it
        namedUser.setId(null);

        // Pending tag group changes should be cleared
        verify(mockTagGroupRegistrar).clearMutations(TagGroupRegistrar.NAMED_USER);

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(NamedUser.ACTION_UPDATE_NAMED_USER);
            }
        }));

        assertNull("Named user ID should be null", namedUser.getId());
    }

    /**
     * Test set empty ID (disassociate).
     */
    @Test
    public void testSetIDEmpty() {
        // Set an initial id
        namedUser.setId("neat");
        clearInvocations(mockDispatcher, mockTagGroupRegistrar);

        namedUser.setId("");

        // Pending tag group changes should be cleared
        verify(mockTagGroupRegistrar).clearMutations(TagGroupRegistrar.NAMED_USER);

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(NamedUser.ACTION_UPDATE_NAMED_USER);
            }
        }));

        assertNull("Named user ID should be null", namedUser.getId());
    }

    /**
     * Test init dispatches a job to update tag groups and the named user.
     */
    @Test
    public void testInit() {
        namedUser.setId("test");
        shadowOf(application).clearStartedServices();

        namedUser.init();

        verify(mockDispatcher, atLeastOnce()).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(NamedUser.ACTION_UPDATE_NAMED_USER);
            }
        }));


    }

    /**
     * Test when IDs match, don't update named user.
     */
    @Test
    public void testIdsMatchNoUpdate() {
        namedUser.setId(fakeNamedUserId);
        clearInvocations(mockDispatcher);

        namedUser.setId(fakeNamedUserId);
        verifyZeroInteractions(mockDispatcher);
    }

    /**
     * Test force update changes the current token and dispatches an update job.
     */
    @Test
    public void testForceUpdate() {
        namedUser.forceUpdate();

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(NamedUser.ACTION_UPDATE_NAMED_USER);
            }
        }));
    }

    /**
     * Test editTagGroups apply dispatches a job to update the tag groups.
     */
    @Test
    public void testStartUpdateNamedUserTagsService() {
        namedUser.editTagGroups()
                 .addTag("tagGroup", "tag1")
                 .removeTag("tagGroup", "tag5")
                 .apply();

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(NamedUser.ACTION_UPDATE_TAG_GROUPS);
            }
        }));
    }

    /**
     * Test editTagGroups apply does not dispatch a job to update the tag groups when data opt-in is disabled.
     */
    @Test
    public void testStartUpdateNamedUserTagsServiceDataCollectionDisabled() {
        dataStore.put(UAirship.DATA_COLLECTION_ENABLED_KEY, false);

        namedUser.editTagGroups()
                 .addTag("tagGroup", "tag1")
                 .removeTag("tagGroup", "tag5")
                 .apply();

        verify(mockDispatcher, times(0)).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(NamedUser.ACTION_UPDATE_TAG_GROUPS);
            }
        }));
    }

    /**
     * Test editTagGroups apply does dispatch job when addTags and removeTags are empty.
     */
    @Test
    public void testEmptyAddTagsRemoveTags() {
        namedUser.editTagGroups().apply();
        verifyZeroInteractions(mockDispatcher);
    }

    /**
     * Test dispatchNamedUserUpdateJob dispatches a job to update the named user.
     */
    @Test
    public void testStartUpdateService() {
        namedUser.dispatchNamedUserUpdateJob();

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(NamedUser.ACTION_UPDATE_NAMED_USER);
            }
        }));
    }

    /**
     * Test dispatchUpdateTagGroupsJob dispatches a job to update the tag groups.
     */
    @Test
    public void testStartUpdateTagsService() {
        namedUser.dispatchUpdateTagGroupsJob();

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(NamedUser.ACTION_UPDATE_TAG_GROUPS);
            }
        }));
    }

    @Test
    public void testNamedUserDataCollectionDisabled() {
        dataStore.put(UAirship.DATA_COLLECTION_ENABLED_KEY, false);
        namedUser.setId("some-user");
        assertNull(namedUser.getId());
    }

    @Test
    public void testNamedUserClearOnDataCollectionDisabled() {
        namedUser.setId("cool");
        assertEquals("cool", namedUser.getId());

        clearInvocations(mockDispatcher);

        dataStore.put(UAirship.DATA_COLLECTION_ENABLED_KEY, false);
        namedUser.onDataCollectionEnabledChanged(false);

        assertNull(namedUser.getId());
        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(NamedUser.ACTION_UPDATE_NAMED_USER);
            }
        }));
    }

    /**
     * Test associate named user succeeds if the status is 2xx.
     */
    @Test
    public void testAssociateNamedUserSucceed() throws RequestException {
        namedUser.setId("namedUserID");
        when(mockChannel.getId()).thenReturn("channelID");

        for (int statusCode = 200; statusCode < 300; statusCode++) {
            // Force an update
            namedUser.forceUpdate();
            assertFalse(namedUser.isIdUpToDate());

            // Set up a 2xx response
            Response response = new Response.Builder<Void>(statusCode).build();
            when(mockNamedUserClient.associate("namedUserID", "channelID")).thenReturn(response);

            // Perform the update
            JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUser.ACTION_UPDATE_NAMED_USER).build();
            assertEquals(JobInfo.JOB_FINISHED, namedUser.onPerformJob(UAirship.shared(), jobInfo));

            // Verify the update was performed
            verify(mockNamedUserClient).associate("namedUserID", "channelID");

            assertTrue(namedUser.isIdUpToDate());

            reset(mockNamedUserClient);
        }
    }

    /**
     * Test associate named user fails if the status is 403
     */
    @Test
    public void testAssociateNamedUserFailed() throws RequestException {
        namedUser.setId("namedUserID");
        when(mockChannel.getId()).thenReturn("channelID");

        assertFalse(namedUser.isIdUpToDate());

        // Set up a 403 response
        Response response = Mockito.mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_FORBIDDEN);
        when(mockNamedUserClient.associate("namedUserID", "channelID")).thenReturn(response);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUser.ACTION_UPDATE_NAMED_USER).build();
        assertEquals(JobInfo.JOB_FINISHED, namedUser.onPerformJob(UAirship.shared(), jobInfo));

        // Verify the update was performed
        verify(mockNamedUserClient).associate("namedUserID", "channelID");

        // Verify its still not up to date
        assertFalse(namedUser.isIdUpToDate());
    }

    /**
     * Test associate named user fails if the status is 500
     */
    @Test
    public void testAssociateNamedUserFailedRetry() throws RequestException {
        namedUser.setId("namedUserID");
        when(mockChannel.getId()).thenReturn("channelID");
        assertFalse(namedUser.isIdUpToDate());

        // Set up a 500 response
        Response response = new Response.Builder<Void>(500).build();
        when(mockNamedUserClient.associate("namedUserID", "channelID")).thenReturn(response);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUser.ACTION_UPDATE_NAMED_USER).build();
        assertEquals(JobInfo.JOB_RETRY, namedUser.onPerformJob(UAirship.shared(), jobInfo));

        // Verify the update was performed
        verify(mockNamedUserClient).associate("namedUserID", "channelID");

        // Verify still not up to date
        assertFalse(namedUser.isIdUpToDate());
    }

    /**
     * Test associate named user retries if the status is 429
     */
    @Test
    public void testAssociateNamedUserTooManyRequests() throws RequestException {
        when(mockChannel.getId()).thenReturn("channelID");
        namedUser.setId("namedUserID");

        // Set up a 429 response
        Response response = new Response.Builder<Void>(429).build();
        when(mockNamedUserClient.associate("namedUserID", "channelID")).thenReturn(response);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUser.ACTION_UPDATE_NAMED_USER).build();
        assertEquals(JobInfo.JOB_RETRY, namedUser.onPerformJob(UAirship.shared(), jobInfo));

        // Verify the update was performed
        verify(mockNamedUserClient).associate("namedUserID", "channelID");

        // Verify still not up to date
        assertFalse(namedUser.isIdUpToDate());
    }

    /**
     * Test disassociate named user succeeds if the status is 2xx.
     */
    @Test
    public void testDisassociateNamedUserSucceed() throws RequestException {
        when(mockChannel.getId()).thenReturn("channelID");
        namedUser.setId(null);

        for (int statusCode = 200; statusCode < 300; statusCode++) {
            // Force an update
            namedUser.forceUpdate();
            assertFalse(namedUser.isIdUpToDate());

            // Set up a 2xx response
            Response response = new Response.Builder<Void>(statusCode).build();
            when(mockNamedUserClient.disassociate("channelID")).thenReturn(response);

            // Perform the update
            JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUser.ACTION_UPDATE_NAMED_USER).build();
            assertEquals(JobInfo.JOB_FINISHED, namedUser.onPerformJob(UAirship.shared(), jobInfo));

            // Verify the update was performed
            verify(mockNamedUserClient).disassociate("channelID");

            // Verify the ID is up to date
            assertTrue(namedUser.isIdUpToDate());

            // Reset the mocks so we can verify again
            reset(mockNamedUserClient);
        }
    }

    /**
     * Test disassociate named user fails if status is not 200.
     */
    @Test
    public void testDisassociateNamedUserFailed() throws RequestException {
        when(mockChannel.getId()).thenReturn("channelID");
        namedUser.setId(null);
        namedUser.forceUpdate();

        // Set up a 404 response
        Response response = Mockito.mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);
        when(mockNamedUserClient.disassociate("channelID")).thenReturn(response);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUser.ACTION_UPDATE_NAMED_USER).build();
        assertEquals(JobInfo.JOB_FINISHED, namedUser.onPerformJob(UAirship.shared(), jobInfo));

        // Verify the update was performed
        verify(mockNamedUserClient).disassociate("channelID");

        // Verify still not up to date
        assertFalse(namedUser.isIdUpToDate());
    }

    /**
     * Test disassociate named user fails if the status is 500
     */
    @Test
    public void testDisassociateNamedUserFailedRetry() throws RequestException {
        when(mockChannel.getId()).thenReturn("channelID");
        namedUser.setId(null);
        namedUser.forceUpdate();

        // Set up a 500 response
        Response response = new Response.Builder<Void>(500).build();
        when(mockNamedUserClient.disassociate("channelID")).thenReturn(response);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUser.ACTION_UPDATE_NAMED_USER).build();
        assertEquals(JobInfo.JOB_RETRY, namedUser.onPerformJob(UAirship.shared(), jobInfo));

        // Verify the update was performed
        verify(mockNamedUserClient).disassociate("channelID");

        // Verify still not up to date
        assertFalse(namedUser.isIdUpToDate());
    }

    /**
     * Test disassociate named user retries if the status is 429
     */
    @Test
    public void testDisassociateNamedUserTooManyRequests() throws RequestException {
        when(mockChannel.getId()).thenReturn("channelID");
        namedUser.setId(null);
        namedUser.forceUpdate();

        // Set up a 429 response
        Response response = new Response.Builder<Void>(429).build();
        when(mockNamedUserClient.disassociate("channelID")).thenReturn(response);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUser.ACTION_UPDATE_NAMED_USER).build();
        assertEquals(JobInfo.JOB_RETRY, namedUser.onPerformJob(UAirship.shared(), jobInfo));

        // Verify the update was performed
        verify(mockNamedUserClient).disassociate("channelID");

        // Verify still not up to date
        assertFalse(namedUser.isIdUpToDate());
    }

    /**
     * Test associate without channel fails.
     */
    @Test
    public void testAssociateNamedUserFailedNoChannel() {
        when(mockChannel.getId()).thenReturn(null);
        namedUser.setId("namedUserId");

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUser.ACTION_UPDATE_NAMED_USER).build();
        assertEquals(JobInfo.JOB_FINISHED, namedUser.onPerformJob(UAirship.shared(), jobInfo));

        // Verify associate not called when channel ID doesn't exist
        verifyZeroInteractions(mockNamedUserClient);

        // Verify still not up to date
        assertFalse(namedUser.isIdUpToDate());
    }

    /**
     * Test disassociate without channel fails.
     */
    @Test
    public void testDisassociateNamedUserFailedNoChannel() {
        when(mockChannel.getId()).thenReturn(null);
        namedUser.setId(null);
        namedUser.forceUpdate();

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUser.ACTION_UPDATE_NAMED_USER).build();
        assertEquals(JobInfo.JOB_FINISHED, namedUser.onPerformJob(UAirship.shared(), jobInfo));

        // Verify associate not called when channel ID doesn't exist
        verifyZeroInteractions(mockNamedUserClient);

        // Verify still not up to date
        assertFalse(namedUser.isIdUpToDate());
    }

    /**
     * Test update named user tags succeeds when the registrar returns true.
     */
    @Test
    public void testUpdateNamedUserTagsSucceed() {
        // Return a named user ID
        namedUser.setId("namedUserId");
        when(mockTagGroupRegistrar.uploadMutations(TagGroupRegistrar.NAMED_USER, "namedUserId")).thenReturn(true);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUser.ACTION_UPDATE_TAG_GROUPS).build();
        assertEquals(JobInfo.JOB_FINISHED, namedUser.onPerformJob(UAirship.shared(), jobInfo));
    }

    /**
     * Test update named user tags without named user ID fails.
     */
    @Test
    public void testUpdateNamedUserTagsNoNamedUser() {
        // Return a null named user ID
        namedUser.setId(null);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUser.ACTION_UPDATE_TAG_GROUPS).build();
        assertEquals(JobInfo.JOB_FINISHED, namedUser.onPerformJob(UAirship.shared(), jobInfo));

        // Verify updateNamedUserTags not called when channel ID doesn't exist
        verifyZeroInteractions(mockTagGroupRegistrar);
    }

    /**
     * Test update named user retries when the upload fails.
     */
    @Test
    public void testUpdateNamedUserTagsRetry() {
        // Return a named user ID
        namedUser.setId("namedUserId");

        // Provide pending changes
        when(mockTagGroupRegistrar.uploadMutations(TagGroupRegistrar.NAMED_USER, "namedUserId")).thenReturn(false);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(NamedUser.ACTION_UPDATE_TAG_GROUPS).build();
        assertEquals(JobInfo.JOB_RETRY, namedUser.onPerformJob(UAirship.shared(), jobInfo));
    }

}
