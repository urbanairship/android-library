/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestRequest;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ChannelApiClientTest extends BaseTestCase {

    private static final String CHANNEL_ID_KEY = "channel_id";

    // Empty payload
    private ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().build();
    private ChannelApiClient client;
    private TestRequest testRequest;

    @Before
    public void setUp() {
        testRequest = new TestRequest();

        // Set hostURL
        AirshipConfigOptions airshipConfigOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setInProduction(false)
                .setDeviceUrl("https://go-demo.urbanairship.com/")
                .build();

        client = new ChannelApiClient(airshipConfigOptions, new RequestFactory() {
            @NonNull
            @Override
            public Request createRequest(@NonNull String requestMethod, @NonNull URL url) {
                return testRequest;
            }
        });
    }

    /**
     * Test create channel succeeds request if the status is 200
     */
    @Test
    public void testCreateChannelSucceedsRequest() throws Exception {

        Map<String, List<String>> headers = new HashMap<>();

        testRequest.response = Response.newBuilder(HttpURLConnection.HTTP_OK)
                                       .setResponseHeaders(headers)
                                       .setResponseMessage("OK")
                                       .setResponseBody("{ \"ok\": true, \"channel_id\": \"someChannelId\"}")
                                       .build();

        ChannelResponse<String> response = client.createChannelWithPayload(payload);

        assertNotNull("Channel response should not be null", response);
        assertEquals("Channel request should be the JSON payload", testRequest.getRequestBody(), payload.toJsonValue().toString());
        assertEquals("Channel response status should be 200", HttpURLConnection.HTTP_OK,
                response.getStatus());
        assertEquals("Channel ID should match with response", "someChannelId",
                JsonValue.parseString(response.getResponseBody()).optMap().opt(CHANNEL_ID_KEY).getString());
        assertEquals("someChannelId", response.getResult());
    }

    /**
     * Test create channel request fails
     */
    @Test
    public void testCreateChannelFailsRequest() throws Exception {
        Map<String, List<String>> headers = new HashMap<>();

        testRequest.response = Response.newBuilder(HttpURLConnection.HTTP_NOT_IMPLEMENTED)
                                       .setResponseHeaders(headers)
                                       .setResponseMessage("Not Implemented")
                                       .build();

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

        testRequest.response = Response.newBuilder(HttpURLConnection.HTTP_OK)
                                       .setResponseHeaders(headers)
                                       .setResponseMessage("OK")
                                       .build();

        String channelId = "someChannelId";
        ChannelResponse<Void> response = client.updateChannelWithPayload(channelId, payload);

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

        testRequest.response = Response.newBuilder(HttpURLConnection.HTTP_NOT_IMPLEMENTED)
                                       .setResponseHeaders(headers)
                                       .setResponseMessage("Not Implemented")
                                       .build();

        String channelId = "someChannelId";
        ChannelResponse<Void> response = client.updateChannelWithPayload(channelId, payload);

        assertNotNull("Channel response should not be null", response);
        assertEquals("Channel request should be the JSON payload", testRequest.getRequestBody(), payload.toJsonValue().toString());
        assertEquals("Channel response status should be 501", HttpURLConnection.HTTP_NOT_IMPLEMENTED,
                response.getStatus());
    }

}
