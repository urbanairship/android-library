/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestRequest;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;

import static com.urbanairship.UAirship.AMAZON_PLATFORM;
import static com.urbanairship.UAirship.ANDROID_PLATFORM;
import static org.junit.Assert.assertEquals;

/**
 * Attributes API client tests.
 */
public class AttributeApiClientTest extends BaseTestCase {

    private TestRequest testRequest;
    private AirshipConfigOptions configOptions;
    private RequestFactory requestFactory;
    private List<AttributeMutation> mutations;

    @Before
    public void setUp() {
        configOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setInProduction(false)
                .setDeviceUrl("https://test.urbanairship.com/")
                .build();

        testRequest = new TestRequest();
        testRequest.response = Response.newBuilder(HttpURLConnection.HTTP_OK)
                                       .setResponseMessage("OK")
                                       .setResponseBody("{ \"ok\": true}")
                                       .build();

        requestFactory = new RequestFactory() {
            @NonNull
            @Override
            public Request createRequest(@NonNull String requestMethod, @NonNull URL url) {
                testRequest.setURL(url);
                testRequest.setRequestMethod(requestMethod);

                return testRequest;
            }
        };

        mutations = new ArrayList<>();
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", "expected_key"));
    }

    /**
     * Test android channel update.
     */
    @Test
    public void testAttributeUpdateAndroid() throws JsonException {
        AttributeApiClient client = new AttributeApiClient(ANDROID_PLATFORM, configOptions, requestFactory);

        List<PendingAttributeMutation> expectedMutations = PendingAttributeMutation.fromAttributeMutations(mutations, 0);

        Response response = client.updateAttributes("expected_identifier", expectedMutations);
        assertEquals(testRequest.response, response);
        assertEquals("https://test.urbanairship.com/api/channels/expected_identifier/attributes?platform=android", testRequest.getURL().toString());
        assertEquals("POST", testRequest.getRequestMethod());

        JsonList attributesPayload = new JsonList(Arrays.asList(JsonMap.newBuilder()
                                                                       .put("action", "set")
                                                                       .put("key", "expected_key")
                                                                       .put("value", "expected_key")
                                                                       .put("timestamp", "1970-01-01T00:00:00")
                                                                       .build().toJsonValue()));

        JsonMap expectedBody = JsonMap.newBuilder()
                                      .putOpt("attributes", attributesPayload)
                                      .build();

        JsonValue requestBody = JsonValue.parseString(testRequest.getRequestBody());
        assertEquals(expectedBody.toJsonValue(), requestBody);
    }

    /**
     * Test amazon channel update.
     */
    @Test
    public void testAttributeUpdateAmazon() throws JsonException {
        AttributeApiClient client = new AttributeApiClient(AMAZON_PLATFORM, configOptions, requestFactory);

        List<PendingAttributeMutation> expectedMutations = PendingAttributeMutation.fromAttributeMutations(mutations, 0);

        Response response = client.updateAttributes("expected_identifier", expectedMutations);
        assertEquals(testRequest.response, response);
        assertEquals("https://test.urbanairship.com/api/channels/expected_identifier/attributes?platform=amazon", testRequest.getURL().toString());
        assertEquals("POST", testRequest.getRequestMethod());

        JsonList attributesPayload = new JsonList(Arrays.asList(JsonMap.newBuilder()
                                                                       .put("action", "set")
                                                                       .put("key", "expected_key")
                                                                       .put("value", "expected_key")
                                                                       .put("timestamp", "1970-01-01T00:00:00")
                                                                       .build().toJsonValue()));

        JsonMap expectedBody = JsonMap.newBuilder()
                                      .putOpt("attributes", attributesPayload)
                                      .build();

        JsonValue requestBody = JsonValue.parseString(testRequest.getRequestBody());
        assertEquals(expectedBody.toJsonValue(), requestBody);
    }
}