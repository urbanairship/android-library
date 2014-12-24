package com.urbanairship.push;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.RobolectricGradleTestRunner;
import com.urbanairship.TestApplication;
import com.urbanairship.TestRequest;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)

public class NamedUserAPIClientTest {

    private final String fakeNamedUserId = "fake-named-user-id";
    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private AirshipConfigOptions mockAirshipConfigOptions;
    private NamedUserAPIClient client;
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

        // Set hostURL
        UAirship.shared().getAirshipConfigOptions().hostURL = "https://go-demo.urbanairship.com/";

        client = new NamedUserAPIClient(mockRequestFactory);
    }

    /**
     * Test associate named user to channel request succeeds if status is 200.
     */
    @Test
    public void testAssociateSucceeds() {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\": true}")
                .create();

        Response response = client.associate(fakeNamedUserId, fakeChannelId);

        assertNotNull("Response should not be null", response);
        assertEquals("Response status should be 200", HttpURLConnection.HTTP_OK, response.getStatus());
    }

    /**
     * Test associate with null named user returns null.
     */
    @Test
    public void testAssociateNullNamedUser() {
        Response response = client.associate(null, fakeChannelId);

        assertNull("Response should be null", response);
    }

    /**
     * Test associate with null channel ID returns null.
     */
    @Test
    public void testAssociateNullChannelId() {
        Response response = client.associate(fakeNamedUserId, null);

        assertNull("Response should be null", response);
    }

    /**
     * Test disassociate named user from channel request succeeds if status is 200.
     */
    @Test
    public void testDisassociateSucceeds() {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\": true}")
                .create();

        Response response = client.disassociate(fakeChannelId);

        assertNotNull("Response should not be null", response);
        assertEquals("Response status should be 200", HttpURLConnection.HTTP_OK, response.getStatus());
    }

    /**
     * Test disassociate with null channel ID returns null.
     */
    @Test
    public void testDisassociateNullChannelId() {
        Response response = client.disassociate(null);

        assertNull("Response should be null", response);
    }

    /**
     * Test associate and disassociate with malformed host URL returns null.
     */
    @Test
    public void testMalformedUrl() {
        RequestFactory mockRequestFactory = Mockito.mock(RequestFactory.class);
        // Set hostURL
        UAirship.shared().getAirshipConfigOptions().hostURL = "files://thisIsMalformed";

        NamedUserAPIClient client2 = new NamedUserAPIClient(mockRequestFactory);

        Response response = client2.associate(fakeNamedUserId, fakeChannelId);
        assertNull("Response should be null", response);

        response = client2.disassociate(fakeChannelId);
        assertNull("Response should be null", response);
    }
}
