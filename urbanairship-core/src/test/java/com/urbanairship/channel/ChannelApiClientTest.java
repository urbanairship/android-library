/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestRequestSession;
import com.urbanairship.config.AirshipUrlConfig;
import com.urbanairship.http.RequestBody;
import com.urbanairship.http.Response;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ChannelApiClientTest extends BaseTestCase {

    private ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().build();
    private ChannelApiClient client;
    private TestRequestSession requestSession = new TestRequestSession();

    @Before
    public void setUp() {
        TestAirshipRuntimeConfig runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder()
                                                   .setDeviceUrl("https://example.com")
                                                   .build());

        client = new ChannelApiClient(runtimeConfig, requestSession);
    }

    /**
     * Test create channel succeeds request if the status is 200
     */
    @Test
    public void testCreateChannelSucceedsRequest() throws Exception {
        requestSession.addResponse(200, "{ \"ok\": true, \"channel_id\": \"someChannelId\"}");

        Response<String> response = client.createChannelWithPayload(payload);

        assertEquals("POST", requestSession.getLastRequest().getMethod());
        assertEquals("https://example.com/api/channels/", requestSession.getLastRequest().getUrl().toString());
        assertEquals(requestSession.getLastRequest().getBody(), new RequestBody.Json(payload));
        assertEquals("someChannelId", response.getResult());
        assertEquals(200, response.getStatus());
    }

    /**
     * Test create channel request fails
     */
    @Test
    public void testCreateChannelFailsRequest() throws Exception {
        requestSession.addResponse(501);

        Response<String> response = client.createChannelWithPayload(payload);

        assertEquals("POST", requestSession.getLastRequest().getMethod());
        assertEquals("https://example.com/api/channels/", requestSession.getLastRequest().getUrl().toString());
        assertEquals(requestSession.getLastRequest().getBody(), new RequestBody.Json(payload));
        assertNull(response.getResult());
        assertEquals(501, response.getStatus());
    }

    /**
     * Test update channel succeeds request if the status is 200
     */
    @Test
    public void testUpdateChannelSucceedsRequest() throws Exception {
        requestSession.addResponse(200);

        Response<Void> response = client.updateChannelWithPayload("someChannelId", payload);

        assertEquals("PUT", requestSession.getLastRequest().getMethod());
        assertEquals("https://example.com/api/channels/someChannelId", requestSession.getLastRequest().getUrl().toString());
        assertEquals(requestSession.getLastRequest().getBody(), new RequestBody.Json(payload));
        assertEquals(200, response.getStatus());
    }

    /**
     * Test update channel request fails
     */
    @Test
    public void testUpdateChannelFailsRequest() throws Exception {
        requestSession.addResponse(501);

        String channelId = "someChannelId";
        Response<Void> response = client.updateChannelWithPayload(channelId, payload);

        assertEquals("PUT", requestSession.getLastRequest().getMethod());
        assertEquals("https://example.com/api/channels/someChannelId", requestSession.getLastRequest().getUrl().toString());
        assertEquals(requestSession.getLastRequest().getBody(), new RequestBody.Json(payload));
        assertEquals(501, response.getStatus());
    }

}
