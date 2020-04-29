/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestRequest;
import com.urbanairship.config.AirshipUrlConfig;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;

import org.junit.Before;
import org.junit.Test;

import androidx.annotation.NonNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ChannelApiClientTest extends BaseTestCase {

    private ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().build();
    private ChannelApiClient client;
    private TestRequest testRequest;

    @Before
    public void setUp() {
        testRequest = new TestRequest();

        TestAirshipRuntimeConfig runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder()
                                                   .setDeviceUrl("https://example.com")
                                                   .build());

        client = new ChannelApiClient(runtimeConfig, new RequestFactory() {
            @NonNull
            @Override
            public Request createRequest() {
                return testRequest;
            }
        });
    }

    /**
     * Test create channel succeeds request if the status is 200
     */
    @Test
    public void testCreateChannelSucceedsRequest() throws Exception {
        testRequest.responseBody = "{ \"ok\": true, \"channel_id\": \"someChannelId\"}";
        testRequest.responseStatus = 200;

        Response<String> response = client.createChannelWithPayload(payload);

        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/channels/", testRequest.getUrl().toString());
        assertEquals(testRequest.getRequestBody(), payload.toJsonValue().toString());
        assertEquals("someChannelId", response.getResult());
        assertEquals(200, response.getStatus());
    }

    /**
     * Test create channel request fails
     */
    @Test
    public void testCreateChannelFailsRequest() throws Exception {
        testRequest.responseStatus = 501;

        Response<String> response = client.createChannelWithPayload(payload);

        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/channels/", testRequest.getUrl().toString());
        assertEquals(testRequest.getRequestBody(), payload.toJsonValue().toString());
        assertNull(response.getResult());
        assertEquals(501, response.getStatus());
    }

    /**
     * Test update channel succeeds request if the status is 200
     */
    @Test
    public void testUpdateChannelSucceedsRequest() throws Exception {
        testRequest.responseStatus = 200;

        Response<Void> response = client.updateChannelWithPayload("someChannelId", payload);

        assertEquals("PUT", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/channels/someChannelId", testRequest.getUrl().toString());
        assertEquals(testRequest.getRequestBody(), payload.toJsonValue().toString());
        assertEquals(200, response.getStatus());
    }

    /**
     * Test update channel request fails
     */
    @Test
    public void testUpdateChannelFailsRequest() throws Exception {
        testRequest.responseStatus = 501;

        String channelId = "someChannelId";
        Response<Void> response = client.updateChannelWithPayload(channelId, payload);

        assertEquals("PUT", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/channels/someChannelId", testRequest.getUrl().toString());
        assertEquals(testRequest.getRequestBody(), payload.toJsonValue().toString());
        assertEquals(501, response.getStatus());
    }

}
