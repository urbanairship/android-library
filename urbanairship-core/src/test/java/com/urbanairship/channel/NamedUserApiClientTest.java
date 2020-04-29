/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestRequest;
import com.urbanairship.config.AirshipUrlConfig;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class NamedUserApiClientTest extends BaseTestCase {

    private final String fakeNamedUserId = "fake-named-user-id";
    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private NamedUserApiClient client;
    private TestRequest testRequest;
    private TestAirshipRuntimeConfig runtimeConfig;
    private RequestFactory mockRequestFactory;

    @Before
    public void setUp() {
        testRequest = new TestRequest();

        mockRequestFactory = Mockito.mock(RequestFactory.class);
        when(mockRequestFactory.createRequest()).thenReturn(testRequest);

        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder()
                                                   .setDeviceUrl("https://example.com")
                                                   .build());

        client = new NamedUserApiClient(runtimeConfig, mockRequestFactory);
    }

    /**
     * Test associate named user to channel request succeeds if status is 200.
     */
    @Test
    public void testAssociateSucceeds() throws RequestException, JsonException {
        testRequest.responseStatus = 200;
        Response<Void> response = client.associate(fakeNamedUserId, fakeChannelId);

        JsonMap expected = JsonMap.newBuilder()
               .put("device_type", "android")
               .put("named_user_id", fakeNamedUserId)
               .put("channel_id", fakeChannelId)
               .build();

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/named_users/associate/", testRequest.getUrl().toString());
        assertEquals(expected, JsonValue.parseString(testRequest.getRequestBody()));
    }

    /**
     * Test disassociate named user from channel request succeeds if status is 200.
     */
    @Test
    public void testDisassociateSucceeds() throws RequestException, JsonException {
        testRequest.responseStatus = 200;
        Response<Void> response = client.disassociate(fakeChannelId);

        JsonMap expected = JsonMap.newBuilder()
                                  .put("device_type", "android")
                                  .put("channel_id", fakeChannelId)
                                  .build();

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/named_users/disassociate/", testRequest.getUrl().toString());
        assertEquals(expected, JsonValue.parseString(testRequest.getRequestBody()));
    }

    /**
     * Test associate with null URL.
     */
    @Test(expected = RequestException.class)
    public void testNullUrlAssociate() throws RequestException {
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder().build());
        client.associate(fakeNamedUserId, fakeChannelId);
    }

    /**
     * Test disassociate with null URL.
     */
    @Test(expected = RequestException.class)
    public void testNullUrlDisassociate() throws RequestException {
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder().build());
        client.disassociate(fakeChannelId);
    }

}
