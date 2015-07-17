package com.urbanairship.push;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.TestRequest;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;

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

public class ChannelAPIClientTest extends BaseTestCase {


    // Empty payload
    private ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().build();
    private AirshipConfigOptions mockAirshipConfigOptions;
    private ChannelAPIClient client;
    private TestRequest testRequest;


    @Before
    public void setUp() throws Exception {
        mockAirshipConfigOptions = Mockito.mock(AirshipConfigOptions.class);
        testRequest = new TestRequest();

        RequestFactory mockRequestFactory = Mockito.mock(RequestFactory.class);
        when(mockRequestFactory.createRequest(anyString(), any(URL.class))).thenReturn(testRequest);

        when(mockAirshipConfigOptions.getAppKey()).thenReturn("appKey");
        when(mockAirshipConfigOptions.getAppSecret()).thenReturn("appSecret");

        TestApplication.getApplication().setOptions(mockAirshipConfigOptions);

        // Set hostURL
        UAirship.shared().getAirshipConfigOptions().hostURL = "https://go-demo.urbanairship.com/";

        client = new ChannelAPIClient(mockRequestFactory);
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

        ChannelResponse response = client.createChannelWithPayload(payload);

        assertNotNull("Channel response should not be null", response);
        assertEquals("Channel request should be the JSON payload", testRequest.getRequestBody(), payload.toJsonValue().toString());
        assertEquals("Channel response status should be 200", HttpURLConnection.HTTP_OK,
                response.getStatus());
        assertEquals("Channel ID should match with response", "someChannelId",
                response.getChannelId());
        assertEquals("Channel location should match with response", channelLocation,
                response.getChannelLocation());
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

        ChannelResponse response = client.createChannelWithPayload(payload);

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
        ChannelResponse response = client.updateChannelWithPayload(channelLocation, payload);

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
        ChannelResponse response = client.updateChannelWithPayload(channelLocation, payload);

        assertNotNull("Channel response should not be null", response);
        assertEquals("Channel request should be the JSON payload", testRequest.getRequestBody(), payload.toJsonValue().toString());
        assertEquals("Channel response status should be 501", HttpURLConnection.HTTP_NOT_IMPLEMENTED,
                response.getStatus());
    }
}
