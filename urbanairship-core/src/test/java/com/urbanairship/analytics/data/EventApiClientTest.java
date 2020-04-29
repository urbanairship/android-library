/* Copyright Airship and Contributors */

package com.urbanairship.analytics.data;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.LegacyTestRequest;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

public class EventApiClientTest extends BaseTestCase {

    private List<String> events;
    private EventApiClient client;
    private LegacyTestRequest testRequest;
    private TestAirshipRuntimeConfig runtimeConfig;

    @Before
    public void setUp() {
        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();

        events = new ArrayList<>();
        events.add("{\"some\":\"json\"}");

        testRequest = new LegacyTestRequest();
        RequestFactory mockRequestFactory = Mockito.mock(RequestFactory.class);
        when(mockRequestFactory.createRequest(anyString(), any(URL.class))).thenReturn(testRequest);

        client = new EventApiClient(runtimeConfig, mockRequestFactory);
    }

    /**
     * Test sending null or empty events returns a null response.
     */
    @Test
    public void testSendEmptyEvents() {
        assertNull(client.sendEvents(new ArrayList<String>(), Collections.<String, String>emptyMap()));
    }

    /**
     * Test the request body contains the passed in events.
     */
    @Test
    public void testSendBody() throws IOException {
        testRequest.response = new Response.Builder<Void>(HttpURLConnection.HTTP_OK)
                                       .setResponseBody(events.toString())
                                       .build();

        EventResponse response = client.sendEvents(events, Collections.<String, String>emptyMap());

        assertEquals("Event request body should match", testRequest.getRequestBody(), events.toString());
        assertNotNull("Event response should not be null", response);
        assertEquals("Event response status should be 200", HttpURLConnection.HTTP_OK, response.getStatus());
    }


    /**
     * This verifies all required and most optional headers.
     */
    @Test
    public void testRequestHeaders() {
        testRequest.response = new Response.Builder<Void>(HttpURLConnection.HTTP_OK)
                                       .setResponseBody(events.toString())
                                       .build();

        Map<String, String> headers = new HashMap<>();
        headers.put("foo", "bar");

        client.sendEvents(events, headers);
        Map<String, String> requestHeaders = testRequest.getRequestHeaders();

        assertEquals("bar", requestHeaders.get("foo"));
    }

    /**
     * Verify we return a null event response when the request responds with null.
     */
    @Test
    public void testNullResponse() {
        testRequest.response = null;
        EventResponse response = client.sendEvents(events, Collections.<String, String>emptyMap());
        assertNull(response);
    }

}
