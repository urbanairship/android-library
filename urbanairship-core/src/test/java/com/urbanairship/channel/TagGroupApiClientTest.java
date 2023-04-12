/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.google.common.collect.Lists;
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

import java.util.HashSet;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;

public class TagGroupApiClientTest extends BaseTestCase {

    private TestRequestSession requestSession = new TestRequestSession();
    private TagGroupsMutation mutation;
    private TestAirshipRuntimeConfig runtimeConfig;

    @Before
    public void setUp() {
        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder()
                                                   .setDeviceUrl("https://test.urbanairship.com")
                                                   .build());

        requestSession.addResponse(200, "{ \"ok\": true}");

        mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));
    }

    @Test
    public void testUpload() throws JsonException, RequestException {
        TagGroupApiClient client = new TagGroupApiClient(runtimeConfig, requestSession, () -> "some-audience", "some-path");

        Response<Void> response = client.updateTags("identifier", mutation);
        assertEquals("https://test.urbanairship.com/some-path", requestSession.getLastRequest().getUrl().toString());
        assertEquals("POST", requestSession.getLastRequest().getMethod());

        JsonMap expectedBody = JsonMap.newBuilder()
                                      .put("audience", JsonMap.newBuilder()
                                                              .put("some-audience", "identifier")
                                                              .build())
                                      .putAll(mutation.toJsonValue().optMap())
                                      .build();

        assertEquals(new RequestBody.Json(expectedBody), requestSession.getLastRequest().getBody());
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
