/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import androidx.annotation.NonNull;

import com.google.common.collect.Lists;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestRequest;
import com.urbanairship.UAirship;
import com.urbanairship.channel.TagGroupApiClient;
import com.urbanairship.channel.TagGroupRegistrar;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;

public class TagGroupApiClientTest extends BaseTestCase {

    private TestRequest testRequest;
    private AirshipConfigOptions configOptions;
    private RequestFactory requestFactory;
    private TagGroupsMutation mutation;

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

        mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));
    }

    /**
     * Test android channel update.
     */
    @Test
    public void testAndroidChannelTagUpdate() throws JsonException {
        TagGroupApiClient client = new TagGroupApiClient(UAirship.ANDROID_PLATFORM, configOptions, requestFactory);

        Response response = client.updateTagGroups(TagGroupRegistrar.CHANNEL, "identifier", mutation);
        assertEquals(testRequest.response, response);
        assertEquals("https://test.urbanairship.com/api/channels/tags/", testRequest.getURL().toString());
        assertEquals("POST", testRequest.getRequestMethod());

        JsonMap expectedBody = JsonMap.newBuilder()
                                      .put("audience", JsonMap.newBuilder()
                                                              .put("android_channel", "identifier")
                                                              .build())
                                      .putAll(mutation.toJsonValue().getMap())
                                      .build();

        JsonValue requestBody = JsonValue.parseString(testRequest.getRequestBody());
        assertEquals(expectedBody.toJsonValue(), requestBody);
    }

    /**
     * Test amazon channel update.
     */
    @Test
    public void testAmazonChannelTagUpdate() throws JsonException {
        TagGroupApiClient client = new TagGroupApiClient(UAirship.AMAZON_PLATFORM, configOptions, requestFactory);

        Response response = client.updateTagGroups(TagGroupRegistrar.CHANNEL, "identifier", mutation);
        assertEquals(testRequest.response, response);
        assertEquals("https://test.urbanairship.com/api/channels/tags/", testRequest.getURL().toString());
        assertEquals("POST", testRequest.getRequestMethod());

        JsonMap expectedBody = JsonMap.newBuilder()
                                      .put("audience", JsonMap.newBuilder()
                                                              .put("amazon_channel", "identifier")
                                                              .build())
                                      .putAll(mutation.toJsonValue().getMap())
                                      .build();

        JsonValue requestBody = JsonValue.parseString(testRequest.getRequestBody());
        assertEquals(expectedBody.toJsonValue(), requestBody);
    }

    /**
     * Test named user update.
     */
    @Test
    public void testNamedUserTagUpdate() throws JsonException {
        TagGroupApiClient client = new TagGroupApiClient(UAirship.ANDROID_PLATFORM, configOptions, requestFactory);

        Response response = client.updateTagGroups(TagGroupRegistrar.NAMED_USER, "identifier", mutation);
        assertEquals(testRequest.response, response);
        assertEquals("https://test.urbanairship.com/api/named_users/tags/", testRequest.getURL().toString());
        assertEquals("POST", testRequest.getRequestMethod());

        JsonMap expectedBody = JsonMap.newBuilder()
                                      .put("audience", JsonMap.newBuilder()
                                                              .put("named_user_id", "identifier")
                                                              .build())
                                      .putAll(mutation.toJsonValue().getMap())
                                      .build();

        JsonValue requestBody = JsonValue.parseString(testRequest.getRequestBody());
        assertEquals(expectedBody.toJsonValue(), requestBody);
    }

}
