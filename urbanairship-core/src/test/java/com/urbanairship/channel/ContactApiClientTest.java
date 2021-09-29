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

public class ContactApiClientTest extends BaseTestCase {

    private final String fakeNamedUserId = "fake-named-user-id";
    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeContactId = "fake_contact_id";
    private ContactApiClient client;
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

        client = new ContactApiClient(runtimeConfig, mockRequestFactory);
    }

    /**
     * Test resolve contact request succeeds if status is 200.
     */
    @Test
    public void testResolveSucceeds() throws RequestException, JsonException {
        testRequest.responseBody = "{ \"ok\": true, \"contact_id\": \"fake_contact_id\", \"is_anonymous\": true }";
        testRequest.responseStatus = 200;

        JsonMap expected = JsonMap.newBuilder()
                .put("channel_id", fakeChannelId)
                .put("device_type", "android")
                .build();

        Response<ContactIdentity> response = client.resolve(fakeChannelId);

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/contacts/resolve/", testRequest.getUrl().toString());
        assertEquals(expected, JsonValue.parseString(testRequest.getRequestBody()).optMap());
        assertEquals("fake_contact_id", response.getResult().getContactId());
        assertEquals(true, response.getResult().isAnonymous());
    }

    /**
     * Test identify contact request succeeds if status is 200.
     */
    @Test
    public void testIdentifySucceeds() throws RequestException, JsonException {
        testRequest.responseBody = "{ \"ok\": true, \"contact_id\": \"fake_contact_id\"}";
        testRequest.responseStatus = 200;

        JsonMap expected = JsonMap.newBuilder()
                .put("named_user_id", fakeNamedUserId)
                .put("channel_id", fakeChannelId)
                .put("device_type", "android")
                .put("contact_id", fakeContactId)
                .build();

        Response<ContactIdentity> response = client.identify(fakeNamedUserId, fakeChannelId, fakeContactId);

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/contacts/identify/", testRequest.getUrl().toString());
        assertEquals(expected, JsonValue.parseString(testRequest.getRequestBody()).optMap());
        assertEquals("fake_contact_id", response.getResult().getContactId());
        assertEquals(false, response.getResult().isAnonymous());
    }

    /**
     * Test identify contact request succeeds if status is 200.
     */
    @Test
    public void testIdentifyContactIdNullSucceeds() throws RequestException, JsonException {
        testRequest.responseBody = "{ \"ok\": true, \"contact_id\": \"fake_contact_id\"}";
        testRequest.responseStatus = 200;

        JsonMap expected = JsonMap.newBuilder()
                .put("named_user_id", fakeNamedUserId)
                .put("channel_id", fakeChannelId)
                .put("device_type", "android")
                .build();

        Response<ContactIdentity> response = client.identify(fakeNamedUserId, fakeChannelId, null);

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/contacts/identify/", testRequest.getUrl().toString());
        assertEquals(expected, JsonValue.parseString(testRequest.getRequestBody()).optMap());
        assertEquals("fake_contact_id", response.getResult().getContactId());
        assertEquals(false, response.getResult().isAnonymous());
    }

    /**
     * Test reset contact request succeeds if status is 200.
     */
    @Test
    public void testResetSucceeds() throws RequestException, JsonException {
        testRequest.responseBody = "{ \"ok\": true, \"contact_id\": \"fake_contact_id\"}";
        testRequest.responseStatus = 200;

        JsonMap expected = JsonMap.newBuilder()
                .put("channel_id", fakeChannelId)
                .put("device_type", "android")
                .build();

        Response<ContactIdentity> response = client.reset(fakeChannelId);

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/contacts/reset/", testRequest.getUrl().toString());
        assertEquals(expected, JsonValue.parseString(testRequest.getRequestBody()).optMap());
        assertEquals("fake_contact_id", response.getResult().getContactId());
        assertEquals(true, response.getResult().isAnonymous());
    }

    /**
     * Test resolve with null URL.
     */
    @Test(expected = RequestException.class)
    public void testNullUrlResolve() throws RequestException {
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder().build());
        client.resolve(fakeChannelId);
    }

    /**
     * Test identify with null URL.
     */
    @Test(expected = RequestException.class)
    public void testNullUrlIdentify() throws RequestException {
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder().build());
        client.identify(fakeNamedUserId, fakeChannelId, fakeContactId);
    }

    /**
     * Test reset with null URL.
     */
    @Test(expected = RequestException.class)
    public void testNullUrlReset() throws RequestException {
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder().build());
        client.reset(fakeChannelId);
    }

}
