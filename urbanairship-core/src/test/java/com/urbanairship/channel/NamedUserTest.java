/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.TestRequest;
import com.urbanairship.channel.NamedUser;
import com.urbanairship.channel.NamedUserJobHandler;
import com.urbanairship.channel.TagGroupRegistrar;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.robolectric.RuntimeEnvironment;

import java.net.URL;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNotSame;
import static junit.framework.Assert.assertNull;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

public class NamedUserTest extends BaseTestCase {

    private final String fakeNamedUserId = "fake-named-user-id";

    private AirshipConfigOptions airshipConfigOptions;
    private NamedUser namedUser;
    private TestRequest testRequest;
    private JobDispatcher mockDispatcher;
    private TagGroupRegistrar mockTagGroupRegistrar;
    private AirshipChannel mockChannel;

    @Before
    public void setUp() {
        mockDispatcher = mock(JobDispatcher.class);

        airshipConfigOptions = AirshipConfigOptions.newBuilder().build();
        airshipConfigOptions = mock(AirshipConfigOptions.class);
        mockTagGroupRegistrar = mock(TagGroupRegistrar.class);
        mockChannel = mock(AirshipChannel.class);

        testRequest = new TestRequest();

        RequestFactory mockRequestFactory = mock(RequestFactory.class);
        when(mockRequestFactory.createRequest(anyString(), any(URL.class))).thenReturn(testRequest);


        TestApplication.getApplication().setOptions(airshipConfigOptions);

        namedUser = new NamedUser(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore, mockTagGroupRegistrar, mockChannel, mockDispatcher);
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
                return jobInfo.getAction().equals(NamedUserJobHandler.ACTION_UPDATE_NAMED_USER);
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
                return jobInfo.getAction().equals(NamedUserJobHandler.ACTION_UPDATE_NAMED_USER);
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
        namedUser.setId(null);

        // Pending tag group changes should be cleared
        verify(mockTagGroupRegistrar).clearMutations(TagGroupRegistrar.NAMED_USER);

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(NamedUserJobHandler.ACTION_UPDATE_NAMED_USER);
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
        shadowOf(RuntimeEnvironment.application).clearStartedServices();

        namedUser.init();

        verify(mockDispatcher, atLeastOnce()).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(NamedUserJobHandler.ACTION_UPDATE_NAMED_USER);
            }
        }));

        verify(mockDispatcher, atLeastOnce()).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(NamedUserJobHandler.ACTION_UPDATE_TAG_GROUPS);
            }
        }));
    }

    /**
     * Test when IDs match, don't update named user.
     */
    @Test
    public void testIdsMatchNoUpdate() {
        namedUser.setId(fakeNamedUserId);
        String changeToken = namedUser.getChangeToken();
        assertEquals("Named user ID should match", fakeNamedUserId, namedUser.getId());

        namedUser.setId(fakeNamedUserId);
        assertEquals("Change token should not change", changeToken, namedUser.getChangeToken());
    }

    /**
     * Test force update changes the current token and dispatches an update job.
     */
    @Test
    public void testForceUpdate() {
        String changeToken = namedUser.getChangeToken();

        namedUser.forceUpdate();

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(NamedUserJobHandler.ACTION_UPDATE_NAMED_USER);
            }
        }));

        assertNotSame("Change token should have changed", changeToken, namedUser.getChangeToken());
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
                return jobInfo.getAction().equals(NamedUserJobHandler.ACTION_UPDATE_TAG_GROUPS);
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
                return jobInfo.getAction().equals(NamedUserJobHandler.ACTION_UPDATE_NAMED_USER);
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
                return jobInfo.getAction().equals(NamedUserJobHandler.ACTION_UPDATE_TAG_GROUPS);
            }
        }));
    }

}
