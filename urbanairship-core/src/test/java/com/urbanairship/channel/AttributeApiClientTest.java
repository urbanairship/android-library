/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestRequestSession;
import com.urbanairship.UAirship;
import com.urbanairship.config.AirshipUrlConfig;
import com.urbanairship.http.RequestBody;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Attributes API client tests.
 */
public class AttributeApiClientTest extends BaseTestCase {

    private TestRequestSession requestSession = new TestRequestSession();
    private List<AttributeMutation> mutations;
    private TestAirshipRuntimeConfig runtimeConfig;

    @Before
    public void setUp() {
        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder()
                                                   .setDeviceUrl("https://example.com")
                                                   .build());

        mutations = new ArrayList<>();
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", JsonValue.wrapOpt("expected_key"), 100));
    }

    /**
     * Test android channel update.
     */
    @Test
    public void testAttributeUpdateAndroidChannel() throws JsonException, RequestException {
        AttributeApiClient client = new AttributeApiClient(runtimeConfig, requestSession, AttributeApiClient.CHANNEL_URL_FACTORY);

        requestSession.addResponse(200);

        Response<Void> response = client.updateAttributes("expected_identifier", mutations);

        RequestBody expectedBody = new RequestBody.Json(JsonMap.newBuilder()
                                                               .putOpt("attributes", mutations)
                                                               .build());

        assertEquals("https://example.com/api/channels/expected_identifier/attributes?platform=android", requestSession.getLastRequest().getUrl().toString());
        assertEquals("POST", requestSession.getLastRequest().getMethod());
        assertEquals(expectedBody, requestSession.getLastRequest().getBody());
        assertEquals(200, response.getStatus());
    }

    /**
     * Test amazon channel update.
     */
    @Test
    public void testAttributeUpdateAmazonChannel() throws RequestException {
        runtimeConfig.setPlatform(UAirship.AMAZON_PLATFORM);
        AttributeApiClient client = new AttributeApiClient(runtimeConfig, requestSession, AttributeApiClient.CHANNEL_URL_FACTORY);

        requestSession.addResponse(200);

        Response<Void> response = client.updateAttributes("expected_identifier", mutations);

        RequestBody expectedBody = new RequestBody.Json(JsonMap.newBuilder()
                                                               .putOpt("attributes", mutations)
                                                               .build());

        assertEquals("https://example.com/api/channels/expected_identifier/attributes?platform=amazon", requestSession.getLastRequest().getUrl().toString());
        assertEquals("POST", requestSession.getLastRequest().getMethod());
        assertEquals(expectedBody, requestSession.getLastRequest().getBody());
        assertEquals(200, response.getStatus());
    }

    /**
     * Test named user attributes
     */
    @Test
    public void testAttributeUpdateNamedUser() throws JsonException, RequestException {
        AttributeApiClient client = new AttributeApiClient(runtimeConfig, requestSession, AttributeApiClient.NAMED_USER_URL_FACTORY);
        requestSession.addResponse(200);

        Response<Void> response = client.updateAttributes("expected_identifier", mutations);

        RequestBody expectedBody = new RequestBody.Json(JsonMap.newBuilder()
                                                               .putOpt("attributes", mutations)
                                                               .build());

        assertEquals("https://example.com/api/named_users/expected_identifier/attributes", requestSession.getLastRequest().getUrl().toString());
        assertEquals("POST", requestSession.getLastRequest().getMethod());
        assertEquals(expectedBody, requestSession.getLastRequest().getBody());
        assertEquals(200, response.getStatus());
    }

    /**
     * Test contact attributes
     */
    @Test
    public void testAttributeUpdateContact() throws JsonException, RequestException {
        AttributeApiClient client = new AttributeApiClient(runtimeConfig, requestSession, AttributeApiClient.CONTACT_URL_FACTORY);
        requestSession.addResponse(200);

        Response<Void> response = client.updateAttributes("expected_identifier", mutations);

        RequestBody expectedBody = new RequestBody.Json(JsonMap.newBuilder()
                                                               .putOpt("attributes", mutations)
                                                               .build());

        assertEquals("https://example.com/api/contacts/expected_identifier/attributes", requestSession.getLastRequest().getUrl().toString());
        assertEquals("POST", requestSession.getLastRequest().getMethod());
        assertEquals(expectedBody, requestSession.getLastRequest().getBody());
        assertEquals(200, response.getStatus());
    }

}
