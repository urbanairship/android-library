/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestRequest;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

public class ChannelApiClientTest extends BaseTestCase {
    private static final String CHANNEL_ID_KEY = "channel_id";
    private static final String CHANNEL_LOCATION_KEY = "Location";

    // Empty payload
    private ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().build();
    private ChannelApiClient client;
    private TestRequest testRequest;

    @Before
    public void setUp() throws Exception {
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

        client = new ChannelApiClient(UAirship.ANDROID_PLATFORM, airshipConfigOptions, mockRequestFactory);
    }

    /**
     * Test create channel succeeds request if the status is 200
     */
    @Test
    public void testCreateChannelSucceedsRequest() throws Exception {
        String channelLocation = "https://go.urbanairship.com/api/channels/someChannelId";

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Location", Arrays.asList(new String[] { channelLocation }));

        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseHeaders(headers)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\": true, \"channel_id\": \"someChannelId\"}")
                .create();

        Response response = client.createChannelWithPayload(payload);

        assertNotNull("Channel response should not be null", response);
        assertEquals("Channel request should be the JSON payload", testRequest.getRequestBody(), payload.toJsonValue().toString());
        assertEquals("Channel response status should be 200", HttpURLConnection.HTTP_OK,
                response.getStatus());
        assertEquals("Channel ID should match with response", "someChannelId",
                JsonValue.parseString(response.getResponseBody()).optMap().opt(CHANNEL_ID_KEY).getString());
        assertEquals("Channel location should match with response", channelLocation,
                response.getResponseHeader(CHANNEL_LOCATION_KEY));
    }

    /**
     * Test create channel request fails
     */
    @Test
    public void testCreateChannelFailsRequest() throws Exception {
        Map<String, List<String>> headers = new HashMap<>();

        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_NOT_IMPLEMENTED)
                .setResponseHeaders(headers)
                .setResponseMessage("Not Implemented")
                .create();

        Response response = client.createChannelWithPayload(payload);

        assertNotNull("Channel response should not be null", response);
        assertEquals("Channel request should be the JSON payload", testRequest.getRequestBody(), payload.toJsonValue().toString());
        assertEquals("Channel response status should be 501", HttpURLConnection.HTTP_NOT_IMPLEMENTED,
                response.getStatus());
    }

    /**
     * Test update channel succeeds request if the status is 200
     */
    @Test
    public void testUpdateChannelSucceedsRequest() throws Exception {
        Map<String, List<String>> headers = new HashMap<>();

        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseHeaders(headers)
                .setResponseMessage("OK")
                .create();

        URL channelLocation = new URL("https://go.urbanairship.com/api/channels/someChannelId");
        Response response = client.updateChannelWithPayload(channelLocation, payload);

        assertNotNull("Channel response should not be null", response);
        assertEquals("Channel request should be the JSON payload", testRequest.getRequestBody(), payload.toJsonValue().toString());
        assertEquals("Channel response status should be 200", HttpURLConnection.HTTP_OK,
                response.getStatus());
    }

    /**
     * Test update channel request fails
     */
    @Test
    public void testUpdateChannelFailsRequest() throws Exception {
        Map<String, List<String>> headers = new HashMap<>();

        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_NOT_IMPLEMENTED)
                .setResponseHeaders(headers)
                .setResponseMessage("Not Implemented")
                .create();

        URL channelLocation = new URL("https://go.urbanairship.com/api/channels/someChannelId");
        Response response = client.updateChannelWithPayload(channelLocation, payload);

        assertNotNull("Channel response should not be null", response);
        assertEquals("Channel request should be the JSON payload", testRequest.getRequestBody(), payload.toJsonValue().toString());
        assertEquals("Channel response status should be 501", HttpURLConnection.HTTP_NOT_IMPLEMENTED,
                response.getStatus());
    }
}
