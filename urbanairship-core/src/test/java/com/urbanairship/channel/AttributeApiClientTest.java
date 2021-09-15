/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import androidx.annotation.NonNull;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestRequest;
import com.urbanairship.UAirship;
import com.urbanairship.config.AirshipUrlConfig;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestFactory;
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

    private TestRequest testRequest;
    private List<AttributeMutation> mutations;
    private TestAirshipRuntimeConfig runtimeConfig;
    private RequestFactory requestFactory;

    @Before
    public void setUp() {
        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder()
                                                   .setDeviceUrl("https://example.com")
                                                   .build());


        testRequest = new TestRequest();
        mutations = new ArrayList<>();
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", JsonValue.wrapOpt("expected_key"), 100));

        requestFactory = new RequestFactory() {
            @NonNull
            @Override
            public Request createRequest() {
                return testRequest;
            }
        };
    }

    /**
     * Test android channel update.
     */
    @Test
    public void testAttributeUpdateAndroidChannel() throws JsonException, RequestException {
        AttributeApiClient client = new AttributeApiClient(runtimeConfig, requestFactory, AttributeApiClient.CHANNEL_URL_FACTORY);

        testRequest.responseStatus = 200;

        Response<Void> response = client.updateAttributes("expected_identifier", mutations);

        JsonMap expectedBody = JsonMap.newBuilder()
                                      .putOpt("attributes", mutations)
                                      .build();

        assertEquals("https://example.com/api/channels/expected_identifier/attributes?platform=android", testRequest.getUrl().toString());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals(expectedBody, JsonValue.parseString(testRequest.getRequestBody()));
        assertEquals(200, response.getStatus());
    }

    /**
     * Test amazon channel update.
     */
    @Test
    public void testAttributeUpdateAmazonChannel() throws JsonException, RequestException {
        runtimeConfig.setPlatform(UAirship.AMAZON_PLATFORM);
        AttributeApiClient client = new AttributeApiClient(runtimeConfig, requestFactory, AttributeApiClient.CHANNEL_URL_FACTORY);


        testRequest.responseStatus = 200;

        Response<Void> response = client.updateAttributes("expected_identifier", mutations);

        JsonMap expectedBody = JsonMap.newBuilder()
                                      .putOpt("attributes", mutations)
                                      .build();

        assertEquals("https://example.com/api/channels/expected_identifier/attributes?platform=amazon", testRequest.getUrl().toString());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals(expectedBody, JsonValue.parseString(testRequest.getRequestBody()));
        assertEquals(200, response.getStatus());
    }

    /**
     * Test named user attributes
     */
    @Test
    public void testAttributeUpdateNamedUser() throws JsonException, RequestException {
        AttributeApiClient client = new AttributeApiClient(runtimeConfig, requestFactory, AttributeApiClient.NAMED_USER_URL_FACTORY);
        testRequest.responseStatus = 200;

        Response<Void> response = client.updateAttributes("expected_identifier", mutations);

        JsonMap expectedBody = JsonMap.newBuilder()
                                      .putOpt("attributes", mutations)
                                      .build();

        assertEquals("https://example.com/api/named_users/expected_identifier/attributes", testRequest.getUrl().toString());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals(expectedBody,  JsonValue.parseString(testRequest.getRequestBody()));
        assertEquals(200,  response.getStatus());
    }

    /**
     * Test contact attributes
     */
    @Test
    public void testAttributeUpdateContact() throws JsonException, RequestException {
        AttributeApiClient client = new AttributeApiClient(runtimeConfig, requestFactory, AttributeApiClient.CONTACT_URL_FACTORY);
        testRequest.responseStatus = 200;

        Response<Void> response = client.updateAttributes("expected_identifier", mutations);

        JsonMap expectedBody = JsonMap.newBuilder()
                .putOpt("attributes", mutations)
                .build();

        assertEquals("https://example.com/api/contacts/expected_identifier/attributes", testRequest.getUrl().toString());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals(expectedBody,  JsonValue.parseString(testRequest.getRequestBody()));
        assertEquals(200,  response.getStatus());
    }
}
