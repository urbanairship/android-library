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
import static junit.framework.Assert.assertNull;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)
public class NamedUserTest {

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
        assertEquals(intent.getAction(), PushService.ACTION_UPDATE_NAMED_USER);
        assertNull("Current named user ID should be null", namedUser.getId());
    }

    /**
     * Test set associated named user ID.
     */
    @Test
    public void testSetAssociatedId() {
        namedUser.setAssociatedId(fakeNamedUserId);
        assertEquals("Associated named user ID should match", fakeNamedUserId, namedUser.getAssociatedId());
    }

    /**
     * Test set associated named user ID null.
     */
    @Test
    public void testSetAssociatedIdNull() {
        namedUser.setAssociatedId(null);
        assertNull("Associated named user ID should be null", namedUser.getAssociatedId());
    }
}
