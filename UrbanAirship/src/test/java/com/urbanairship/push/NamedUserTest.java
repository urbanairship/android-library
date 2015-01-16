package com.urbanairship.push;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.RobolectricGradleTestRunner;
import com.urbanairship.TestApplication;
import com.urbanairship.TestRequest;
import com.urbanairship.http.RequestFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowIntent;

import java.net.URL;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotSame;
import static junit.framework.Assert.assertNull;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)
public class NamedUserTest {

    private final String fakeNamedUserId = "fake-named-user-id";
    private final String fakeToken = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";

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

        namedUser = new NamedUser(TestApplication.getApplication().preferenceDataStore);
    }

    /**
     * Test set valid ID (associate).
     */
    @Test
    public void testSetIDValid() {
        ShadowApplication application = Robolectric.shadowOf(Robolectric.application);
        application.clearStartedServices();

        namedUser.setId(fakeNamedUserId);

        ShadowIntent intent = Robolectric.shadowOf(application.peekNextStartedService());
        assertEquals("Intent action should be to update named user",
                intent.getAction(), PushService.ACTION_UPDATE_NAMED_USER);
        assertEquals("Current named user ID should be set", fakeNamedUserId, namedUser.getId());
    }

    /**
     * Test set invalid ID.
     */
    @Test
    public void testSetIDInvalid() {
        String currentNamedUserId = namedUser.getId();

        namedUser.setId("     ");
        assertEquals("Current named user ID should not have changed", currentNamedUserId, namedUser.getId());
    }

    /**
     * Test set null ID (disassociate).
     */
    @Test
    public void testSetIDNull() {
        ShadowApplication application = Robolectric.shadowOf(Robolectric.application);
        application.clearStartedServices();

        namedUser.setId(null);

        ShadowIntent intent = Robolectric.shadowOf(application.peekNextStartedService());
        assertEquals("Intent action should be to update named user",
                intent.getAction(), PushService.ACTION_UPDATE_NAMED_USER);
        assertNull("Current named user ID should be null", namedUser.getId());
    }

    /**
     * Test when IDs match, don't update named user.
     */
    @Test
    public void testIdsMatchNoUpdate() {
        namedUser.setId(fakeNamedUserId);
        String currentToken = namedUser.getCurrentToken();
        assertEquals("Current named user ID should match", fakeNamedUserId, namedUser.getId());

        namedUser.setId(fakeNamedUserId);
        assertEquals("Current token should not change", currentToken, namedUser.getCurrentToken());
    }

    /**
     * Test force update changes the current token and starts the service.
     */
    @Test
    public void testForceUpdate() {
        String currentToken = namedUser.getCurrentToken();

        ShadowApplication application = Robolectric.shadowOf(Robolectric.application);
        application.clearStartedServices();

        namedUser.forceUpdate();

        ShadowIntent intent = Robolectric.shadowOf(application.peekNextStartedService());
        assertEquals("Intent action should be to update named user",
                intent.getAction(), PushService.ACTION_UPDATE_NAMED_USER);
        assertNotSame("Current token should have changed", currentToken, namedUser.getCurrentToken());
    }

    /**
     * Test update change token.
     */
    @Test
    public void testUpdateChangeToken() {
        String currentToken = namedUser.getCurrentToken();
        namedUser.updateChangeToken();
        assertNotSame("Current token should have changed", currentToken, namedUser.getCurrentToken());
    }

    /**
     * Test set last updated token.
     */
    @Test
    public void testSetLastUpdatedToken() {
        namedUser.setId(fakeNamedUserId);
        String lastUpdatedToken = namedUser.getLastUpdatedToken();

        namedUser.setLastUpdatedToken(fakeToken);
        assertNotSame("Last updated token should not match", namedUser.getLastUpdatedToken(), lastUpdatedToken);
        assertEquals("Last updated token should match", fakeToken, namedUser.getLastUpdatedToken());
    }
}
