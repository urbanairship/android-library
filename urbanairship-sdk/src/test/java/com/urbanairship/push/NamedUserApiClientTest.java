/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestRequest;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

public class NamedUserApiClientTest extends BaseTestCase {

    private final String fakeNamedUserId = "fake-named-user-id";
    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private NamedUserApiClient client;
    private TestRequest testRequest;

    @Before
    public void setUp() {
        testRequest = new TestRequest();

        RequestFactory mockRequestFactory = Mockito.mock(RequestFactory.class);
        when(mockRequestFactory.createRequest(anyString(), any(URL.class))).thenReturn(testRequest);

        // Set hostURL
        AirshipConfigOptions airshipConfigOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setInProduction(false)
                .setHostURL("https://go-demo.urbanairship.com/")
                .build();

        client = new NamedUserApiClient(UAirship.ANDROID_PLATFORM, airshipConfigOptions, mockRequestFactory);
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
     * Test associate and disassociate with malformed host URL returns null.
     */
    @Test
    public void testMalformedUrl() {
        RequestFactory mockRequestFactory = Mockito.mock(RequestFactory.class);

        // Set hostURL
        AirshipConfigOptions airshipConfigOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setInProduction(false)
                .setHostURL("files://thisIsMalformed")
                .build();


        client = new NamedUserApiClient(UAirship.ANDROID_PLATFORM, airshipConfigOptions, mockRequestFactory);

        Response response = client.associate(fakeNamedUserId, fakeChannelId);
        assertNull("Response should be null", response);

        response = client.disassociate(fakeChannelId);
        assertNull("Response should be null", response);
    }
}
