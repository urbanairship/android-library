/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.google.common.collect.Lists;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestRequest;
import com.urbanairship.UAirship;
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

import java.util.HashSet;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class TagGroupApiClientTest extends BaseTestCase {

    private TestRequest testRequest;
    private RequestFactory requestFactory;
    private TagGroupsMutation mutation;
    private TestAirshipRuntimeConfig runtimeConfig;

    @Before
    public void setUp() {
        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder()
                                                   .setDeviceUrl("https://test.urbanairship.com")
                                                   .build());

        testRequest = new TestRequest();
        testRequest.responseStatus = 200;
        testRequest.responseBody = "{ \"ok\": true}";

        requestFactory = Mockito.mock(RequestFactory.class);
        when(requestFactory.createRequest()).thenReturn(testRequest);

        mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));
    }

    @Test
    public void testUpload() throws JsonException, RequestException {
        TagGroupApiClient client = new TagGroupApiClient(runtimeConfig, requestFactory, new Callable<String>() {
            @Override
            public String call() throws Exception {
                return "some-audience";
            }
        }, "some-path");

        Response<Void> response = client.updateTags("identifier", mutation);
        assertEquals(testRequest.responseBody, response.getResponseBody());
        assertEquals("https://test.urbanairship.com/some-path", testRequest.getUrl().toString());
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
    public void testAndroidChannelClient() throws RequestException {
        runtimeConfig.setPlatform(UAirship.ANDROID_PLATFORM);
        TagGroupApiClient client = TagGroupApiClient.channelClient(runtimeConfig);

        assertEquals("android_channel", client.getAudienceKey());
        assertEquals("api/channels/tags/", client.getPath());
    }

    @Test
    public void testAmazonChannelClient() throws RequestException {
        runtimeConfig.setPlatform(UAirship.AMAZON_PLATFORM);
        TagGroupApiClient client = TagGroupApiClient.channelClient(runtimeConfig);

        assertEquals("amazon_channel", client.getAudienceKey());
        assertEquals("api/channels/tags/", client.getPath());
    }

    @Test
    public void testNamedUserClient() throws RequestException {
        TagGroupApiClient client = TagGroupApiClient.namedUserClient(runtimeConfig);

        assertEquals("named_user_id", client.getAudienceKey());
        assertEquals("api/named_users/tags/", client.getPath());
    }

    @Test
    public void testContactClient() throws RequestException {
        TagGroupApiClient client = TagGroupApiClient.contactClient(runtimeConfig);

        assertEquals("contact_id", client.getAudienceKey());
        assertEquals("api/contacts/tags/", client.getPath());
    }
}
