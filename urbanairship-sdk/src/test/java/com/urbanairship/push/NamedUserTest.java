/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.content.Intent;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.TestRequest;
import com.urbanairship.http.RequestFactory;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowIntent;

import java.net.URL;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotSame;
import static junit.framework.Assert.assertNull;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

public class NamedUserTest extends BaseTestCase {

    private final String fakeNamedUserId = "fake-named-user-id";

    private AirshipConfigOptions mockAirshipConfigOptions;
    private NamedUser namedUser;
    private TestRequest testRequest;

    @Before
    public void setUp() {
        mockAirshipConfigOptions = Mockito.mock(AirshipConfigOptions.class);
        testRequest = new TestRequest();

        RequestFactory mockRequestFactory = Mockito.mock(RequestFactory.class);
        when(mockRequestFactory.createRequest(anyString(), any(URL.class))).thenReturn(testRequest);

        when(mockAirshipConfigOptions.getAppKey()).thenReturn("appKey");
        when(mockAirshipConfigOptions.getAppSecret()).thenReturn("appSecret");

        TestApplication.getApplication().setOptions(mockAirshipConfigOptions);

        namedUser = new NamedUser(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore);
    }

    /**
     * Test set valid ID (associate).
     */
    @Test
    public void testSetIDValid() {
        ShadowApplication application = Shadows.shadowOf(RuntimeEnvironment.application);
        application.clearStartedServices();

        namedUser.setId(fakeNamedUserId);

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals("Intent action should be clearing pending named user tags",
                PushService.ACTION_CLEAR_PENDING_NAMED_USER_TAGS, startedIntent.getAction());

        startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals("Intent action should be to update named user",
                PushService.ACTION_UPDATE_NAMED_USER, startedIntent.getAction());
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
        ShadowApplication application = Shadows.shadowOf(RuntimeEnvironment.application);
        application.clearStartedServices();

        namedUser.setId(null);

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals("Intent action should be clearing pending named user tags",
                PushService.ACTION_CLEAR_PENDING_NAMED_USER_TAGS, startedIntent.getAction());

        startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals("Intent action should be to update named user",
                PushService.ACTION_UPDATE_NAMED_USER, startedIntent.getAction());
        assertNull("Named user ID should be null", namedUser.getId());
    }

    /**
     * Test init starts named user and tags update service.
     */
    @Test
    public void testInitStartNamedUserUpdateService() {
        namedUser.setId("test");
        ShadowApplication.getInstance().clearStartedServices();

        namedUser.init();

        Intent updateIntent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals(PushService.ACTION_UPDATE_NAMED_USER, updateIntent.getAction());

        Intent tagIntent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals(PushService.ACTION_UPDATE_NAMED_USER_TAGS, tagIntent.getAction());
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
     * Test force update changes the current token and starts the service.
     */
    @Test
    public void testForceUpdate() {
        String changeToken = namedUser.getChangeToken();

        ShadowApplication application = Shadows.shadowOf(RuntimeEnvironment.application);
        application.clearStartedServices();

        namedUser.forceUpdate();

        ShadowIntent intent = Shadows.shadowOf(application.peekNextStartedService());
        assertEquals("Intent action should be to update named user",
                intent.getAction(), PushService.ACTION_UPDATE_NAMED_USER);
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
     * Test editTagGroups apply starts the update named user tags service.
     */
    @Test
    public void testStartUpdateNamedUserTagsService() {

        namedUser.editTagGroups()
                 .addTag("tagGroup", "tag1")
                 .addTag("tagGroup", "tag2")
                 .addTag("tagGroup", "tag3")
                 .removeTag("tagGroup", "tag3")
                 .removeTag("tagGroup", "tag4")
                 .removeTag("tagGroup", "tag5")
                 .apply();

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals("Expect Update Named User Tags Service", PushService.ACTION_UPDATE_NAMED_USER_TAGS, startedIntent.getAction());
    }

    /**
     * Test editTagGroups apply does not start the service when addTags and removeTags are empty.
     */
    @Test
    public void testEmptyAddTagsRemoveTags() {

        namedUser.editTagGroups().apply();

        Intent startedIntent = ShadowApplication.getInstance().peekNextStartedService();
        assertNull("Update named user tags service should not have started", startedIntent);
    }

    /**
     * Test startUpdateService starts the update named user service.
     */
    @Test
    public void testStartUpdateService() {

        namedUser.startUpdateService();

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals("Expect Update Named User Service", PushService.ACTION_UPDATE_NAMED_USER, startedIntent.getAction());
    }

    /**
     * Test startUpdateTagsService starts the update named user tags service.
     */
    @Test
    public void testStartUpdateTagsService() {

        namedUser.startUpdateTagsService();

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals("Expect Update Named User Tags Service", PushService.ACTION_UPDATE_NAMED_USER_TAGS, startedIntent.getAction());
    }

    /**
     * Test startClearPendingTagsService starts the clear named user tags service.
     */
    @Test
    public void testStartClearPendingTagsService() {
        namedUser.startClearPendingTagsService();

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals("Expect Clear Pending Tags Service", PushService.ACTION_CLEAR_PENDING_NAMED_USER_TAGS, startedIntent.getAction());
    }
}
