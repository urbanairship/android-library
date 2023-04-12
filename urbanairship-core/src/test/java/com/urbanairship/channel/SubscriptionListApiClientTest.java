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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;

public class SubscriptionListApiClientTest extends BaseTestCase {

    private TestRequestSession requestSession = new TestRequestSession();

    private List<SubscriptionListMutation> mutations;
    private TestAirshipRuntimeConfig runtimeConfig;

    @Before
    public void setup() {
        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder()
                                                   .setDeviceUrl("https://test.urbanairship.com")
                                                   .build());

        mutations = new ArrayList<>();
        mutations.add(SubscriptionListMutation.newSubscribeMutation("listId", 0L));
    }

    @Test
    public void testUpdateSubscriptions() throws RequestException, JsonException {
        requestSession.addResponse(202);

        SubscriptionListApiClient client = new SubscriptionListApiClient(runtimeConfig, requestSession, new Callable<String>() {
            @Override
            public String call() {
                return "audience-key";
            }
        }, "api/subscription_lists/channels", "api/channels/subscription_lists");

        Response<Void> response = client.updateSubscriptionLists("identifier", mutations);

        assertEquals("https://test.urbanairship.com/api/channels/subscription_lists", requestSession.getLastRequest().getUrl().toString());
        assertEquals("POST", requestSession.getLastRequest().getMethod());
        assertEquals(202, response.getStatus());

        JsonMap expectedBody = JsonMap.newBuilder()
                                      .put("audience", JsonMap.newBuilder()
                                                              .put("audience-key", "identifier")
                                                              .build())
                                      .put("subscription_lists", JsonValue.wrap(mutations))
                                      .build();
        RequestBody requestBody = requestSession.getLastRequest().getBody();
        assertEquals(new RequestBody.Json(expectedBody), requestBody);
    }

    @Test
    public void testGetSubscriptionLists() throws RequestException, JsonException {
        requestSession.addResponse(200,
                JsonMap.newBuilder()
                       .put("ok", "true")
                       .put("list_ids", JsonValue.wrap(Arrays.asList("one", "two", "three")))
                       .build()
                       .toString()
        );

        SubscriptionListApiClient client = new SubscriptionListApiClient(runtimeConfig, requestSession, new Callable<String>() {
            @Override
            public String call() {
                return "audience-key";
            }
        }, "api/subscription_lists/channels", "api/channels/subscription_lists");

        Response<Set<String>> response = client.getSubscriptionLists("identifier");

        assertEquals("https://test.urbanairship.com/api/subscription_lists/channels/identifier", requestSession.getLastRequest().getUrl().toString());
        assertEquals("GET", requestSession.getLastRequest().getMethod());
        assertEquals(200, response.getStatus());

        Set<String> expected = new HashSet<String>(3) {{
            add("one");
            add("two");
            add("three");
        }};
        assertEquals(expected, response.getResult());
    }

    @Test
    public void testAndroidChannelClient() throws RequestException {
        runtimeConfig.setPlatform(UAirship.ANDROID_PLATFORM);
        SubscriptionListApiClient client = SubscriptionListApiClient.channelClient(runtimeConfig);

        assertEquals("android_channel", client.getAudienceKey());
        assertEquals("api/channels/subscription_lists", client.getUpdatePath());
        assertEquals("api/subscription_lists/channels/identifier", client.getListPath("identifier"));
    }

    @Test
    public void testAmazonChannelClient() throws RequestException {
        runtimeConfig.setPlatform(UAirship.AMAZON_PLATFORM);
        SubscriptionListApiClient client = SubscriptionListApiClient.channelClient(runtimeConfig);

        assertEquals("amazon_channel", client.getAudienceKey());
        assertEquals("api/channels/subscription_lists", client.getUpdatePath());
        assertEquals("api/subscription_lists/channels/identifier", client.getListPath("identifier"));
    }
}
