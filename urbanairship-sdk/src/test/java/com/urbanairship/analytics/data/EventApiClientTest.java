/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics.data;

import android.os.Build;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.TestRequest;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushUser;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

public class EventApiClientTest extends BaseTestCase {

    private List<String> events = new ArrayList<>();
    private EventApiClient client;
    private TestRequest testRequest;


    @Before
    public void setUp() {
        events.add("{\"some\":\"json\"}");

        testRequest = new TestRequest();

        RequestFactory mockRequestFactory = Mockito.mock(RequestFactory.class);
        when(mockRequestFactory.createRequest(anyString(), any(URL.class))).thenReturn(testRequest);

        RichPushUser richPushUser = Mockito.mock(RichPushUser.class);
        when(richPushUser.getId()).thenReturn("userId");

        RichPushInbox inbox = Mockito.mock(RichPushInbox.class);
        when(inbox.getUser()).thenReturn(richPushUser);
        TestApplication.getApplication().setInbox(inbox);


        client = new EventApiClient(TestApplication.getApplication(), mockRequestFactory);
    }

    /**
     * Test sending null or empty events returns a null response.
     */
    @Test
    public void testSendEmptyEvents() {
        assertNull(client.sendEvents(UAirship.shared(), new ArrayList<String>()));
        assertNull(null);
    }

    /**
     * Test the request body contains the passed in events.
     */
    @Test
    public void testSendBody() throws IOException {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody(events.toString())
                .create();

        EventResponse response = client.sendEvents(UAirship.shared(), events);

        assertEquals("Event request body should match", testRequest.getRequestBody(), events.toString());
        assertNotNull("Event response should not be null", response);
        assertEquals("Event response status should be 200", HttpURLConnection.HTTP_OK, response.getStatus());
    }

    /**
     * This verifies all required and most optional headers.
     */
    @Test
    public void testRequestHeaders() {
        Locale.setDefault(new Locale("en", "US", "POSIX"));

        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody(events.toString())
                .create();

        AirshipConfigOptions airshipConfig = UAirship.shared().getAirshipConfigOptions();

        String[][] expectedHeaders = new String[][] {
                { "X-UA-Device-Family", "android" },
                { "X-UA-Package-Name", UAirship.getPackageName() },
                { "X-UA-Package-Version", UAirship.getPackageInfo().versionName },
                { "X-UA-App-Key", airshipConfig.getAppKey() },
                { "X-UA-In-Production", Boolean.toString(airshipConfig.inProduction) },
                { "X-UA-Device-Model", Build.MODEL },
                { "X-UA-Android-Version-Code", String.valueOf(Build.VERSION.SDK_INT) },
                { "X-UA-Lib-Version", UAirship.getVersion() },
                { "X-UA-Timezone", TimeZone.getDefault().getID() },
                { "X-UA-Locale-Language", "en" },
                { "X-UA-Locale-Country", "US" },
                { "X-UA-Locale-Variant", "POSIX" },
                { "X-UA-User-ID", "userId" }

        };

        client.sendEvents(UAirship.shared(), events);
        Map<String, String> requestHeaders = testRequest.getRequestHeaders();

        for (String[] keyValuePair : expectedHeaders) {
            String actualValue = requestHeaders.get(keyValuePair[0]);
            assertEquals(keyValuePair[1], actualValue);
        }
    }

    /**
     * Test that amazon is set as the device family when the plaform is amazon.
     */
    @Test
    public void testAmazonDeviceFamily() {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);

        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody(events.toString())
                .create();

        client.sendEvents(UAirship.shared(), events);

        Map<String, String> requestHeaders = testRequest.getRequestHeaders();
        String deviceFamily = requestHeaders.get("X-UA-Device-Family");
        assertEquals("Expected amazon device family", deviceFamily, "amazon");
    }

    /**
     * This verifies that we don't add the X-UA-Locale-Country if the country
     * field is blank on the locale.
     */
    @Test
    public void testRequestHeaderEmptyLocaleCountryHeaders() {
        Locale.setDefault(new Locale("en", "", "POSIX"));

        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody(events.toString())
                .create();

        client.sendEvents(UAirship.shared(), events);

        Map<String, String> requestHeaders = testRequest.getRequestHeaders();
        assertNull(requestHeaders.get("X-UA-Locale-Country"));
    }

    /**
     * This verifies that we don't add the X-UA-Locale-Variant if the variant
     * field is blank on the locale.
     */
    @Test
    public void testRequestHeaderEmptyLocaleVariantHeaders() {
        Locale.setDefault(new Locale("en", "US", ""));

        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody(events.toString())
                .create();

        client.sendEvents(UAirship.shared(), events);

        Map<String, String> requestHeaders = testRequest.getRequestHeaders();
        assertNull(requestHeaders.get("X-UA-Locale-Variant"));
    }

    /**
     * This verifies that we don't add any locale fields if the language
     * is empty.
     */
    @Test
    public void testRequestHeaderEmptyLanguageLocaleHeaders() {
        Locale.setDefault(new Locale("", "US", "POSIX"));

        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody(events.toString())
                .create();

        client.sendEvents(UAirship.shared(), events);

        Map<String, String> requestHeaders = testRequest.getRequestHeaders();
        assertNull(requestHeaders.get("X-UA-Locale-Language"));
        assertNull(requestHeaders.get("X-UA-Locale-Country"));
        assertNull(requestHeaders.get("X-UA-Locale-Variant"));
    }

    /**
     * Verify we return a null event response when the request responds with null.
     */
    @Test
    public void testNullResponse() {
        testRequest.response = null;
        EventResponse response = client.sendEvents(UAirship.shared(), events);
        assertNull(response);
    }
}
