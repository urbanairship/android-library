package com.urbanairship.channel;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class SubscriptionListApiClientTest extends BaseTestCase {

    private TestRequest testRequest;
    private RequestFactory requestFactory;
    private List<SubscriptionListMutation> mutations;
    private TestAirshipRuntimeConfig runtimeConfig;

    @Before
    public void setup() {
        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder()
                                                   .setDeviceUrl("https://test.urbanairship.com")
                                                   .build());

        testRequest = new TestRequest();

        requestFactory = Mockito.mock(RequestFactory.class);
        when(requestFactory.createRequest()).thenReturn(testRequest);

        mutations = new ArrayList<>();
        mutations.add(SubscriptionListMutation.newSubscribeMutation("listId", 0L));
    }

    @Test
    public void testUpdateSubscriptions() throws RequestException, JsonException {
        testRequest.responseStatus = 202;

        SubscriptionListApiClient client = new SubscriptionListApiClient(runtimeConfig, requestFactory, new Callable<String>() {
            @Override
            public String call() {
                return "audience-key";
            }
        }, "api/subscription_lists/channels", "api/channels/subscription_lists");

        Response<Void> response = client.updateSubscriptionLists("identifier", mutations);

        assertEquals(testRequest.responseBody, response.getResponseBody());
        assertEquals("https://test.urbanairship.com/api/channels/subscription_lists", testRequest.getUrl().toString());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals(202, response.getStatus());

        JsonMap expectedBody = JsonMap.newBuilder()
                                      .put("audience", JsonMap.newBuilder()
                                                              .put("audience-key", "identifier")
                                                              .build())
                                      .put("subscription_lists", JsonValue.wrap(mutations))
                                      .build();
        JsonValue requestBody = JsonValue.parseString(testRequest.getRequestBody());
        assertEquals(expectedBody.toJsonValue(), requestBody);
    }

    @Test
    public void testGetSubscriptionLists() throws RequestException, JsonException {
        testRequest.responseStatus = 200;
        testRequest.responseBody = JsonMap.newBuilder()
                .put("ok", "true")
                .put("list_ids", JsonValue.wrap(Arrays.asList("one", "two", "three")))
                .build()
                .toString();

        SubscriptionListApiClient client = new SubscriptionListApiClient(runtimeConfig, requestFactory, new Callable<String>() {
            @Override
            public String call() {
                return "audience-key";
            }
        }, "api/subscription_lists/channels", "api/channels/subscription_lists");

        Response<Set<String>> response = client.getSubscriptionLists("identifier");

        assertEquals(testRequest.responseBody, response.getResponseBody());
        assertEquals("https://test.urbanairship.com/api/subscription_lists/channels/identifier", testRequest.getUrl().toString());
        assertEquals("GET", testRequest.getRequestMethod());
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
