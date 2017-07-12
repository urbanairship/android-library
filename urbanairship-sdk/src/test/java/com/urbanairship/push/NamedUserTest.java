/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import com.google.common.collect.Lists;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.TestRequest;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplication;

import java.net.URL;
import java.util.HashSet;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotSame;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class NamedUserTest extends BaseTestCase {

    private final String fakeNamedUserId = "fake-named-user-id";

    private AirshipConfigOptions mockAirshipConfigOptions;
    private NamedUser namedUser;
    private TestRequest testRequest;
    private JobDispatcher mockDispatcher;

    @Before
    public void setUp() {
        mockDispatcher = mock(JobDispatcher.class);
        mockAirshipConfigOptions = mock(AirshipConfigOptions.class);
        testRequest = new TestRequest();

        RequestFactory mockRequestFactory = mock(RequestFactory.class);
        when(mockRequestFactory.createRequest(anyString(), any(URL.class))).thenReturn(testRequest);

        when(mockAirshipConfigOptions.getAppKey()).thenReturn("appKey");
        when(mockAirshipConfigOptions.getAppSecret()).thenReturn("appSecret");

        TestApplication.getApplication().setOptions(mockAirshipConfigOptions);

        namedUser = new NamedUser(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore, mockDispatcher);
    }

    /**
     * Test set valid ID (associate).
     */
    @Test
    public void testSetIDValid() {
        // Make sure we have a pending tag group change
        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));
        namedUser.getTagGroupStore().push(mutation);

        ShadowApplication application = Shadows.shadowOf(RuntimeEnvironment.application);
        application.clearStartedServices();

        namedUser.setId(fakeNamedUserId);

        assertTrue(namedUser.getTagGroupStore().getMutations().isEmpty());

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
        // Make sure we have a pending tag group change
        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));
        namedUser.getTagGroupStore().push(mutation);

        ShadowApplication application = Shadows.shadowOf(RuntimeEnvironment.application);
        application.clearStartedServices();

        namedUser.setId(null);

        // Pending tag group changes should be cleared
        assertTrue(namedUser.getTagGroupStore().getMutations().isEmpty());

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
        ShadowApplication.getInstance().clearStartedServices();

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
     * Test disassociateNamedUserIfNull clears the named user ID when it is null.
     */
    @Test
    public void testDisassociateNamedUserNullId() {
        namedUser.setId(null);
        namedUser.disassociateNamedUserIfNull();
        assertNull("Named user ID should be null", namedUser.getId());
    }

    /**
     * Test disassociateNamedUserIfNull does not clear named user ID, when it is not null.
     */
    @Test
    public void testDisassociateNamedUserNonNullId() {
        namedUser.setId(fakeNamedUserId);
        namedUser.disassociateNamedUserIfNull();
        assertEquals("Named user ID should remain the same", fakeNamedUserId, namedUser.getId());
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
