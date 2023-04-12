/* Copyright Airship and Contributors */

package com.urbanairship.remotedata;

import android.content.Context;
import android.net.Uri;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PushProviders;
import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestRequestSession;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushProvider;
import com.urbanairship.util.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.shadows.ShadowBuild;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RemoteDataApiClientTest extends BaseTestCase {

    private TestRequestSession requestSession = new TestRequestSession();
    private RemoteDataApiClient client;
    private PushProviders pushProviders;

    private List<PushProvider> availableProviders;
    private TestAirshipRuntimeConfig runtimeConfig;
    private RemoteDataApiClient.PayloadParser payloadParser;

    @Before
    public void setUp() {
        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();

        availableProviders = new ArrayList<>();
        pushProviders = mock(PushProviders.class);
        when(pushProviders.getAvailableProviders()).thenReturn(availableProviders);

        client = new RemoteDataApiClient(runtimeConfig, requestSession, () -> pushProviders);
        payloadParser = (headers, url, payloads) -> RemoteDataPayload.parsePayloads(payloads, JsonMap.EMPTY_MAP);
    }

    /**
     * Test fetch remote data request on success
     */
    @Test
    public void testFetchRemoteDataRequestSuccess() throws RequestException {
        String responseTimestamp = DateUtils.createIso8601TimeStamp(System.currentTimeMillis());

        Map<String, String> headers = new HashMap<>();
        headers.put("Last-Modified", responseTimestamp);

        JsonValue payload = JsonMap.newBuilder()
                                   .put("type", "test")
                                   .put("timestamp", "2017-01-01T12:00:00")
                                   .put("data", JsonMap.newBuilder().put("foo", "bar").build())
                                   .build()
                                   .toJsonValue();

        JsonMap responseJson = JsonMap.newBuilder().putOpt("payloads", Collections.singleton(payload)).build();

        requestSession.addResponse(200, responseJson.toString(), headers);

        String requestTimestamp = DateUtils.createIso8601TimeStamp(0);
        Response<RemoteDataApiClient.Result> response = client.fetchRemoteDataPayloads(requestTimestamp, new Locale("en"), 555, payloadParser);

        assertEquals(requestSession.getLastRequest().getHeaders().get("If-Modified-Since"), requestTimestamp);
        assertNotNull(response);
        assertEquals(responseTimestamp, response.getHeaders().get("Last-Modified"));


        assertEquals(requestSession.getLastRequest().getUrl(), response.getResult().url);
        assertEquals(555, Integer.parseInt(requestSession.getLastRequest().getUrl().getQueryParameter("random_value")));
        assertEquals(payloadParser.parse(headers, requestSession.getLastRequest().getUrl(), responseJson.opt("payloads").optList()), response.getResult().payloads);
    }

    /**
     * Test response parser.
     */
    @Test
    public void testResponseParser() throws RequestException {
        JsonValue payload = JsonMap.newBuilder()
                                   .put("type", "test")
                                   .put("timestamp", "2017-01-01T12:00:00")
                                   .put("data", JsonMap.newBuilder().put("foo", "bar").build())
                                   .build()
                                   .toJsonValue();

        final JsonMap responseJson = JsonMap.newBuilder().putOpt("payloads", Collections.singleton(payload)).build();
        requestSession.addResponse(200, responseJson.toString());

        final Set<RemoteDataPayload> parsedResponse = new HashSet<>();
        parsedResponse.add(RemoteDataPayload.emptyPayload("neat"));

        Response<RemoteDataApiClient.Result> response = client.fetchRemoteDataPayloads(null, new Locale("en"), 555, (RemoteDataApiClient.PayloadParser) (headers, url, payloads) -> {
            assertEquals(requestSession.getLastRequest().getUrl(), url);
            assertEquals(responseJson.opt("payloads").optList(), payloads);
           return parsedResponse;
        });

        assertNotNull(response);
        assertEquals(requestSession.getLastRequest().getUrl(), response.getResult().url);
        assertEquals(parsedResponse, response.getResult().payloads);
    }


    /**
     * Test the SDK version is sent as a query parameter.
     */
    @Test
    public void testSdkVersion() throws RequestException {
        requestSession.addResponse(400, null);

        client.fetchRemoteDataPayloads(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), new Locale("en"), 555, payloadParser);

        Uri uri = Uri.parse(requestSession.getLastRequest().getUrl().toString());
        assertEquals(uri.getQueryParameter("sdk_version"), UAirship.getVersion());
    }

    /**
     * Test the push providers are sent as a query parameter.
     */
    @Test
    public void testPushProviders() throws RequestException {
        requestSession.addResponse(400, null);

        availableProviders.add(new TestPushProvider(PushProvider.FCM_DELIVERY_TYPE));
        availableProviders.add(new TestPushProvider(PushProvider.FCM_DELIVERY_TYPE));
        availableProviders.add(new TestPushProvider(PushProvider.ADM_DELIVERY_TYPE));

        client.fetchRemoteDataPayloads(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), new Locale("en"), 555, payloadParser);

        Uri uri = Uri.parse(requestSession.getLastRequest().getUrl().toString());
        assertEquals(uri.getQueryParameter("push_providers"), "fcm,adm");
    }

    /**
     * Test the push providers is not added if the available providers is empty.
     */
    @Test
    public void testEmptyPushProviders() throws RequestException {
        requestSession.addResponse(400, null);

        client.fetchRemoteDataPayloads(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), new Locale("en"), 555, payloadParser);
        Uri uri = Uri.parse(requestSession.getLastRequest().getUrl().toString());
        assertNull(uri.getQueryParameter("push_providers"));
    }

    /**
     * Test the manufacturer is included if on the "should include" list.
     */
    @Test
    public void testManufacturer() throws RequestException {
        requestSession.addResponse(400, null);

        ShadowBuild.setManufacturer("huawei");
        client.fetchRemoteDataPayloads(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), new Locale("en"), 555, payloadParser);
        Uri uri = Uri.parse(requestSession.getLastRequest().getUrl().toString());
        assertEquals(uri.getQueryParameter("manufacturer"), "huawei");
    }

    /**
     * Test the manufacturer is not included if not on the "should include" list.
     */
    @Test
    public void testManufacturerNotIncluded() throws RequestException {
        requestSession.addResponse(400, null);

        ShadowBuild.setManufacturer("google");
        client.fetchRemoteDataPayloads(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), new Locale("en"), 555, payloadParser);
        Uri uri = Uri.parse(requestSession.getLastRequest().getUrl().toString());
        assertNull(uri.getQueryParameter("manufacturer"));
    }

    /**
     * Test locale info is sent as query parameters.
     */
    @Test
    public void testLocale() throws RequestException {
        requestSession.addResponse(400, null);

        Locale locale = new Locale("en", "US");
        client.fetchRemoteDataPayloads(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), locale, 555, payloadParser);

        Uri uri = Uri.parse(requestSession.getLastRequest().getUrl().toString());
        assertEquals(uri.getQueryParameter("language"), "en");
        assertEquals(uri.getQueryParameter("country"), "US");
    }

    /**
     * Test country is not sent as a query parameter if it's not defined.
     */
    @Test
    public void testLocaleMissingCountry() throws RequestException {
        requestSession.addResponse(400, null);

        client.fetchRemoteDataPayloads(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), new Locale("de"), 555, payloadParser);

        Uri uri = Uri.parse(requestSession.getLastRequest().getUrl().toString());
        assertEquals(uri.getQueryParameter("language"), "de");
        assertNull(uri.getQueryParameter("country"));
    }

    /**
     * Test language is not sent as a query parameter if it's not defined.
     */
    @Test
    public void testLocaleMissingLanguage() throws RequestException {
        requestSession.addResponse(400, null);

        Locale locale = new Locale("", "US");
        client.fetchRemoteDataPayloads(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), locale, 555, payloadParser);

        Uri uri = Uri.parse(requestSession.getLastRequest().getUrl().toString());
        assertNull(uri.getQueryParameter("language"));
        assertEquals(uri.getQueryParameter("country"), "US");
    }

    /**
     * Test fetch remote data request on success with no timestamp
     */
    @Test
    public void testFetchRemoteDataRequestNoTimestamp() throws RequestException {
        String responseTimestamp = DateUtils.createIso8601TimeStamp(System.currentTimeMillis());

        Map<String, String> headers = new HashMap<>();
        headers.put("Last-Modified", responseTimestamp);

        JsonMap payload = JsonMap.newBuilder().putOpt("payloads", JsonList.EMPTY_LIST).build();

        requestSession.addResponse(200, payload.toString(), headers);

        Response response = client.fetchRemoteDataPayloads(null, new Locale("en"), 555, payloadParser);

        assertNull("Headers should not contain timestamp", requestSession.getLastRequest().getHeaders().get("If-Modified-Since"));
        assertNotNull("Response should not be null", response);
        assertEquals("Response status should be 200", HttpURLConnection.HTTP_OK, response.getStatus());
        assertEquals("Response should be the JSONMap", response.getBody(), payload.toString());
        assertEquals("Last-Modified should match with timestamp", responseTimestamp, response.getHeaders().get("Last-Modified"));
    }

    /**
     * Test fetch remote data request on failure
     */
    @Test
    public void testFetchRemoteDataRequestFailure() throws RequestException {
        Map<String, String> headers = new HashMap<>();

        requestSession.addResponse(501, "", headers);

        String requestTimestamp = DateUtils.createIso8601TimeStamp(0);
        Response response = client.fetchRemoteDataPayloads(requestTimestamp, new Locale("en"), 555, payloadParser);

        assertNotNull("Response should not be null", response);
        assertEquals("Response status should be 501", HttpURLConnection.HTTP_NOT_IMPLEMENTED, response.getStatus());
    }

    private static class TestPushProvider implements PushProvider {
        private final String deliveryType;

        public TestPushProvider(@DeliveryType String deliveryType) {
            this.deliveryType = deliveryType;
        }

        @Override
        public int getPlatform() {
            throw new RuntimeException("Not implemented");
        }

        @NonNull
        @Override
        public String getDeliveryType() {
            return deliveryType;
        }

        @Nullable
        @Override
        public String getRegistrationToken(@NonNull Context context) {
            throw new RuntimeException("Not implemented");
        }

        @Override
        public boolean isAvailable(@NonNull Context context) {
            return true;
        }

        @Override
        public boolean isSupported(@NonNull Context context) {
            return true;
        }
    }
}
