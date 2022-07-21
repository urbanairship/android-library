/* Copyright Airship and Contributors */

package com.urbanairship.remotedata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PushProviders;
import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestRequest;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushProvider;
import com.urbanairship.util.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
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

public class RemoteDataApiClientTest extends BaseTestCase {

    private TestRequest testRequest;
    private RemoteDataApiClient client;
    private PushProviders pushProviders;
    private RequestFactory mockRequestFactory;

    private List<PushProvider> availableProviders;
    private TestAirshipRuntimeConfig runtimeConfig;
    private RemoteDataApiClient.PayloadParser payloadParser;

    @Before
    public void setUp() {
        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();

        testRequest = new TestRequest();
        mockRequestFactory = Mockito.mock(RequestFactory.class);
        when(mockRequestFactory.createRequest()).thenReturn(testRequest);

        availableProviders = new ArrayList<>();
        pushProviders = mock(PushProviders.class);
        when(pushProviders.getAvailableProviders()).thenReturn(availableProviders);

        client = new RemoteDataApiClient(runtimeConfig, () -> pushProviders, mockRequestFactory);
        payloadParser = (headers, url, payloads) -> RemoteDataPayload.parsePayloads(payloads, JsonMap.EMPTY_MAP);
    }

    /**
     * Test fetch remote data request on success
     */
    @Test
    public void testFetchRemoteDataRequestSuccess() throws RequestException {
        String responseTimestamp = DateUtils.createIso8601TimeStamp(System.currentTimeMillis());

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Last-Modified", Collections.singletonList(responseTimestamp));

        JsonValue payload = JsonMap.newBuilder()
                                   .put("type", "test")
                                   .put("timestamp", "2017-01-01T12:00:00")
                                   .put("data", JsonMap.newBuilder().put("foo", "bar").build())
                                   .build()
                                   .toJsonValue();

        JsonMap responseJson = JsonMap.newBuilder().putOpt("payloads", Collections.singleton(payload)).build();
        testRequest.responseHeaders = headers;
        testRequest.responseBody = responseJson.toString();
        testRequest.responseStatus = 200;

        String requestTimestamp = DateUtils.createIso8601TimeStamp(0);
        Response<RemoteDataApiClient.Result> response = client.fetchRemoteDataPayloads(requestTimestamp, new Locale("en"), 555, payloadParser);

        assertEquals(testRequest.getRequestHeaders().get("If-Modified-Since"), requestTimestamp);
        assertNotNull(response);
        assertEquals(responseTimestamp, response.getResponseHeader("Last-Modified"));


        assertEquals(testRequest.getUrl(), response.getResult().url);
        assertEquals(555, Integer.parseInt(testRequest.getUrl().getQueryParameter("random_value")));
        assertEquals(payloadParser.parse(headers, testRequest.getUrl(), responseJson.opt("payloads").optList()), response.getResult().payloads);
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
        testRequest.responseBody = responseJson.toString();
        testRequest.responseStatus = 200;

        final Set<RemoteDataPayload> parsedResponse = new HashSet<>();
        parsedResponse.add(RemoteDataPayload.emptyPayload("neat"));

        Response<RemoteDataApiClient.Result> response = client.fetchRemoteDataPayloads(null, new Locale("en"), 555, (RemoteDataApiClient.PayloadParser) (headers, url, payloads) -> {
            assertEquals(testRequest.getUrl(), url);
            assertEquals(responseJson.opt("payloads").optList(), payloads);
           return parsedResponse;
        });

        assertNotNull(response);
        assertEquals(testRequest.getUrl(), response.getResult().url);
        assertEquals(parsedResponse, response.getResult().payloads);
    }


    /**
     * Test the SDK version is sent as a query parameter.
     */
    @Test
    public void testSdkVersion() throws RequestException {
        client.fetchRemoteDataPayloads(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), new Locale("en"), 555, payloadParser);

        Uri uri = Uri.parse(testRequest.getUrl().toString());
        assertEquals(uri.getQueryParameter("sdk_version"), UAirship.getVersion());
    }

    /**
     * Test the push providers are sent as a query parameter.
     */
    @Test
    public void testPushProviders() throws RequestException {
        availableProviders.add(new TestPushProvider(PushProvider.FCM_DELIVERY_TYPE));
        availableProviders.add(new TestPushProvider(PushProvider.FCM_DELIVERY_TYPE));
        availableProviders.add(new TestPushProvider(PushProvider.ADM_DELIVERY_TYPE));

        client.fetchRemoteDataPayloads(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), new Locale("en"), 555, payloadParser);

        Uri uri = Uri.parse(testRequest.getUrl().toString());
        assertEquals(uri.getQueryParameter("push_providers"), "fcm,adm");
    }

    /**
     * Test the push providers is not added if the available providers is empty.
     */
    @Test
    public void testEmptyPushProviders() throws RequestException {
        client.fetchRemoteDataPayloads(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), new Locale("en"), 555, payloadParser);
        Uri uri = Uri.parse(testRequest.getUrl().toString());
        assertNull(uri.getQueryParameter("push_providers"));
    }

    /**
     * Test the manufacturer is included if on the "should include" list.
     */
    @Test
    public void testManufacturer() throws RequestException {
        ShadowBuild.setManufacturer("huawei");
        client.fetchRemoteDataPayloads(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), new Locale("en"), 555, payloadParser);
        Uri uri = Uri.parse(testRequest.getUrl().toString());
        assertEquals(uri.getQueryParameter("manufacturer"), "huawei");
    }

    /**
     * Test the manufacturer is not included if not on the "should include" list.
     */
    @Test
    public void testManufacturerNotIncluded() throws RequestException {
        ShadowBuild.setManufacturer("google");
        client.fetchRemoteDataPayloads(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), new Locale("en"), 555, payloadParser);
        Uri uri = Uri.parse(testRequest.getUrl().toString());
        assertNull(uri.getQueryParameter("manufacturer"));
    }

    /**
     * Test locale info is sent as query parameters.
     */
    @Test
    public void testLocale() throws RequestException {
        Locale locale = new Locale("en", "US");
        client.fetchRemoteDataPayloads(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), locale, 555, payloadParser);

        Uri uri = Uri.parse(testRequest.getUrl().toString());
        assertEquals(uri.getQueryParameter("language"), "en");
        assertEquals(uri.getQueryParameter("country"), "US");
    }

    /**
     * Test country is not sent as a query parameter if it's not defined.
     */
    @Test
    public void testLocaleMissingCountry() throws RequestException {
        client.fetchRemoteDataPayloads(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), new Locale("de"), 555, payloadParser);

        Uri uri = Uri.parse(testRequest.getUrl().toString());
        assertEquals(uri.getQueryParameter("language"), "de");
        assertNull(uri.getQueryParameter("country"));
    }

    /**
     * Test language is not sent as a query parameter if it's not defined.
     */
    @Test
    public void testLocaleMissingLanguage() throws RequestException {
        Locale locale = new Locale("", "US");
        client.fetchRemoteDataPayloads(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), locale, 555, payloadParser);

        Uri uri = Uri.parse(testRequest.getUrl().toString());
        assertNull(uri.getQueryParameter("language"));
        assertEquals(uri.getQueryParameter("country"), "US");
    }

    /**
     * Test fetch remote data request on success with no timestamp
     */
    @Test
    public void testFetchRemoteDataRequestNoTimestamp() throws RequestException {
        String responseTimestamp = DateUtils.createIso8601TimeStamp(System.currentTimeMillis());

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Last-Modified", Collections.singletonList(responseTimestamp));

        JsonMap payload = JsonMap.newBuilder().putOpt("payloads", JsonList.EMPTY_LIST).build();

        testRequest.responseStatus = 200;
        testRequest.responseHeaders = headers;
        testRequest.responseBody = payload.toString();

        Response response = client.fetchRemoteDataPayloads(null, new Locale("en"), 555, payloadParser);

        assertNull("Headers should not contain timestamp", testRequest.getRequestHeaders().get("If-Modified-Since"));
        assertNotNull("Response should not be null", response);
        assertEquals("Response status should be 200", HttpURLConnection.HTTP_OK, response.getStatus());
        assertEquals("Response should be the JSONMap", response.getResponseBody(), payload.toString());
        assertEquals("Last-Modified should match with timestamp", responseTimestamp, response.getResponseHeader("Last-Modified"));
    }

    /**
     * Test fetch remote data request on failure
     */
    @Test
    public void testFetchRemoteDataRequestFailure() throws RequestException {
        Map<String, List<String>> headers = new HashMap<>();

        testRequest.responseStatus = 501;
        testRequest.responseHeaders = headers;

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
