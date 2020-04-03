/* Copyright Airship and Contributors */

package com.urbanairship.remotedata;

import android.content.Context;
import android.net.Uri;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PushProviders;
import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestRequest;
import com.urbanairship.UAirship;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.push.PushProvider;
import com.urbanairship.util.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.shadows.ShadowBuild;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RemoteDataApiClientTest extends BaseTestCase {

    private TestRequest testRequest;
    private RemoteDataApiClient client;
    private PushProviders pushProviders;

    private List<PushProvider> availableProviders;
    private TestAirshipRuntimeConfig runtimeConfig;

    @Before
    public void setUp() {
        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();

        testRequest = new TestRequest();
        RequestFactory requestFactory = new RequestFactory() {
            @NonNull
            @Override
            public Request createRequest(@NonNull String requestMethod, @NonNull URL url) {
                testRequest.setRequestMethod(requestMethod);
                testRequest.setURL(url);
                return testRequest;
            }
        };

        availableProviders = new ArrayList<>();
        pushProviders = mock(PushProviders.class);
        when(pushProviders.getAvailableProviders()).thenReturn(availableProviders);

        client = new RemoteDataApiClient(runtimeConfig, pushProviders, requestFactory);
    }

    /**
     * Test fetch remote data request on success
     */
    @Test
    public void testFetchRemoteDataRequestSuccess() {
        String responseTimestamp = DateUtils.createIso8601TimeStamp(System.currentTimeMillis());

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Last-Modified", Collections.singletonList(responseTimestamp));

        JsonMap map = JsonMap.newBuilder().put("foo", "bar").build();
        JsonMap payload = JsonMap.newBuilder().put("type", "test").put("timestamp", responseTimestamp).put("data", map).build();
        JsonList list = new JsonList(Collections.singletonList(payload.toJsonValue()));

        testRequest.response = Response.newBuilder(HttpURLConnection.HTTP_OK)
                                       .setResponseHeaders(headers)
                                       .setResponseMessage("OK")
                                       .setResponseBody(list.toString())
                                       .build();

        String requestTimestamp = DateUtils.createIso8601TimeStamp(0);
        Response response = client.fetchRemoteData(requestTimestamp, new Locale("en"));

        assertEquals("Headers should contain timestamp", testRequest.getRequestHeaders().get("If-Modified-Since"), requestTimestamp);
        assertNotNull("Response should not be null", response);
        assertEquals("Response status should be 200", HttpURLConnection.HTTP_OK, response.getStatus());
        assertEquals("Response should be the JSON list", response.getResponseBody(), list.toString());
        assertEquals("Last-Modified should match with timestamp", responseTimestamp, response.getResponseHeader("Last-Modified"));
    }

    /**
     * Test the SDK version is sent as a query parameter.
     */
    @Test
    public void testSdkVersion() {
        client.fetchRemoteData(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), new Locale("en"));

        Uri uri = Uri.parse(testRequest.getURL().toString());
        assertEquals(uri.getQueryParameter("sdk_version"), UAirship.getVersion());
    }

    /**
     * Test the push providers are sent as a query parameter.
     */
    @Test
    public void testPushProviders() {
        availableProviders.add(new TestPushProvider(PushProvider.FCM_DELIVERY_TYPE));
        availableProviders.add(new TestPushProvider(PushProvider.FCM_DELIVERY_TYPE));
        availableProviders.add(new TestPushProvider(PushProvider.ADM_DELIVERY_TYPE));

        client.fetchRemoteData(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), new Locale("en"));

        Uri uri = Uri.parse(testRequest.getURL().toString());
        assertEquals(uri.getQueryParameter("push_providers"), "fcm,adm");
    }

    /**
     * Test the push providers is not added if the available providers is empty.
     */
    @Test
    public void testEmptyPushProviders() {
        client.fetchRemoteData(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), new Locale("en"));
        Uri uri = Uri.parse(testRequest.getURL().toString());
        assertNull(uri.getQueryParameter("push_providers"));
    }

    /**
     * Test the manufacturer is included if whitelisted.
     */
    @Test
    public void testManufacturer() {
        ShadowBuild.setManufacturer("huawei");
        client.fetchRemoteData(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), new Locale("en"));
        Uri uri = Uri.parse(testRequest.getURL().toString());
        assertEquals(uri.getQueryParameter("manufacturer"), "huawei");
    }

    /**
     * Test the manufacturer is not included if not on the whitelisted.
     */
    @Test
    public void testManufacturerNotIncluded() {
        ShadowBuild.setManufacturer("google");
        client.fetchRemoteData(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), new Locale("en"));
        Uri uri = Uri.parse(testRequest.getURL().toString());
        assertNull(uri.getQueryParameter("fmanufacturer"));
    }

    /**
     * Test locale info is sent as query parameters.
     */
    @Test
    public void testLocale() {
        Locale locale = new Locale("en", "US");
        client.fetchRemoteData(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), locale);

        Uri uri = Uri.parse(testRequest.getURL().toString());
        assertEquals(uri.getQueryParameter("language"), "en");
        assertEquals(uri.getQueryParameter("country"), "US");
    }

    /**
     * Test country is not sent as a query parameter if it's not defined.
     */
    @Test
    public void testLocaleMissingCountry() {
        client.fetchRemoteData(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), new Locale("de"));

        Uri uri = Uri.parse(testRequest.getURL().toString());
        assertEquals(uri.getQueryParameter("language"), "de");
        assertNull(uri.getQueryParameter("country"));
    }

    /**
     * Test language is not sent as a query parameter if it's not defined.
     */
    @Test
    public void testLocaleMissingLanguage() {
        Locale locale = new Locale("", "US");
        client.fetchRemoteData(DateUtils.createIso8601TimeStamp(System.currentTimeMillis()), locale);

        Uri uri = Uri.parse(testRequest.getURL().toString());
        assertNull(uri.getQueryParameter("language"));
        assertEquals(uri.getQueryParameter("country"), "US");
    }

    /**
     * Test fetch remote data request on success with no timestamp
     */
    @Test
    public void testFetchRemoteDataRequestNoTimestamp() {
        String responseTimestamp = DateUtils.createIso8601TimeStamp(System.currentTimeMillis());

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Last-Modified", Collections.singletonList(responseTimestamp));

        JsonMap map = JsonMap.newBuilder().put("foo", "bar").build();
        JsonMap payload = JsonMap.newBuilder().put("type", "test").put("timestamp", responseTimestamp).put("data", map).build();
        JsonList list = new JsonList(Collections.singletonList(payload.toJsonValue()));

        testRequest.response = Response.newBuilder(HttpURLConnection.HTTP_OK)
                                       .setResponseHeaders(headers)
                                       .setResponseMessage("OK")
                                       .setResponseBody(list.toString())
                                       .build();

        Response response = client.fetchRemoteData(null, new Locale("en"));

        assertNull("Headers should not contain timestamp", testRequest.getRequestHeaders().get("If-Modified-Since"));
        assertNotNull("Response should not be null", response);
        assertEquals("Response status should be 200", HttpURLConnection.HTTP_OK, response.getStatus());
        assertEquals("Response should be the JSON list", response.getResponseBody(), list.toString());
        assertEquals("Last-Modified should match with timestamp", responseTimestamp, response.getResponseHeader("Last-Modified"));
    }

    /**
     * Test fetch remote data request on failure
     */
    @Test
    public void testFetchRemoteDataRequestFailure() {
        Map<String, List<String>> headers = new HashMap<>();

        testRequest.response = Response.newBuilder(HttpURLConnection.HTTP_NOT_IMPLEMENTED)
                                       .setResponseHeaders(headers)
                                       .setResponseMessage("Not Implemented")
                                       .build();

        String requestTimestamp = DateUtils.createIso8601TimeStamp(0);
        Response response = client.fetchRemoteData(requestTimestamp, new Locale("en"));

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
        public String getRegistrationToken(@NonNull Context context) throws RegistrationException {
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
