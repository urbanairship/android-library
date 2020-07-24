/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.google.common.collect.Lists;
import com.urbanairship.BaseTestCase;
import com.urbanairship.LegacyTestRequest;
import com.urbanairship.TestAirshipRuntimeConfig;
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

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;

import androidx.annotation.NonNull;

import static org.junit.Assert.assertEquals;

public class TagGroupApiClientTest extends BaseTestCase {

    private LegacyTestRequest testRequest;
    private RequestFactory requestFactory;
    private TagGroupsMutation mutation;
    private TestAirshipRuntimeConfig runtimeConfig;

    @Before
    public void setUp() {
        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder()
                                                   .setDeviceUrl("https://test.urbanairship.com")
                                                   .build());

        testRequest = new LegacyTestRequest();
        testRequest.response = new Response.Builder<Void>(HttpURLConnection.HTTP_OK)
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

    @Test
    public void testUpload() throws JsonException, RequestException {
        TagGroupApiClient client = new TagGroupApiClient(runtimeConfig, requestFactory, "some-audience", "some-path");

        Response<Void> response = client.updateTags("identifier", mutation);
        assertEquals(testRequest.response, response);
        assertEquals("https://test.urbanairship.com/some-path", testRequest.getURL().toString());
        assertEquals("POST", testRequest.getRequestMethod());

        JsonMap expectedBody = JsonMap.newBuilder()
                                      .put("audience", JsonMap.newBuilder()
                                                              .put("some-audience", "identifier")
                                                              .build())
                                      .putAll(mutation.toJsonValue().optMap())
                                      .build();

        JsonValue requestBody = JsonValue.parseString(testRequest.getRequestBody());
        assertEquals(expectedBody.toJsonValue(), requestBody);
    }

    @Test
    public void testAndroidChannelClient() {
        runtimeConfig.setPlatform(UAirship.ANDROID_PLATFORM);
        TagGroupApiClient client = TagGroupApiClient.channelClient(runtimeConfig);

        assertEquals("android_channel", client.getAudienceKey());
        assertEquals("api/channels/tags/", client.getPath());
    }

    @Test
    public void testAmazonChannelClient() {
        runtimeConfig.setPlatform(UAirship.AMAZON_PLATFORM);
        TagGroupApiClient client = TagGroupApiClient.channelClient(runtimeConfig);

        assertEquals("amazon_channel", client.getAudienceKey());
        assertEquals("api/channels/tags/", client.getPath());
    }

    @Test
    public void testNamedUserClient() {
        TagGroupApiClient client = TagGroupApiClient.namedUserClient(runtimeConfig);

        assertEquals("named_user_id", client.getAudienceKey());
        assertEquals("api/named_users/tags/", client.getPath());;
    }
}
