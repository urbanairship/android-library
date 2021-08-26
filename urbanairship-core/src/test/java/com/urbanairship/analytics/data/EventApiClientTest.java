/* Copyright Airship and Contributors */

package com.urbanairship.analytics.data;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestRequest;
import com.urbanairship.config.AirshipUrlConfig;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

public class EventApiClientTest extends BaseTestCase {

    private List<JsonValue> events;
    private EventApiClient client;
    private TestRequest testRequest;
    private TestAirshipRuntimeConfig runtimeConfig;
    private RequestFactory mockRequestFactory;

    private JsonValue validEvent;
    private JsonValue invalidEvent;

    @Before
    public void setUp() throws JsonException {
        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder()
                                                   .setAnalyticsUrl("http://example.com")
                                                   .build());

        validEvent = JsonValue.parseString("{\"some\":\"json\"}");
        invalidEvent = JsonValue.NULL;

        events = new ArrayList<>();
        events.add(validEvent);

        testRequest = new TestRequest();
        mockRequestFactory = Mockito.mock(RequestFactory.class);
        when(mockRequestFactory.createRequest()).thenReturn(testRequest);

        client = new EventApiClient(runtimeConfig, mockRequestFactory);
    }

    /**
     * Test sending a correct request that succeeds
     */
    @Test
    public void testSendEventsSucceed() throws RequestException, JsonException {
        testRequest.responseBody = "";
        testRequest.responseStatus = 200;
        testRequest.responseLastModifiedTime = 0;

        Response<EventResponse> response = client.sendEvents(events, Collections.<String, String>emptyMap());

        assertEquals(200, response.getStatus());
        assertEquals("", response.getResponseBody());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("http://example.com/warp9/", testRequest.getUrl().toString());
        assertEquals(JsonValue.wrapOpt(events), JsonValue.parseString(testRequest.getRequestBody()));
        assertEquals(0, response.getLastModifiedTime());
        assertNull(response.getResponseHeaders());
    }

    /**
     * Test sending a request with a null URL will return an exception
     */
    @Test(expected = RequestException.class)
    public void testNullUrl() throws RequestException {
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder().build());
        client.sendEvents(events, Collections.<String, String>emptyMap());
    }

    /**
     * Test sending null or empty events returns an empty response.
     */
    @Test
    public void testSendEmptyEvents() throws RequestException {
        testRequest.responseBody = "";
        testRequest.responseStatus = 200;
        testRequest.responseLastModifiedTime = 0;

        events = new ArrayList<>();

        Response<EventResponse> response = client.sendEvents(events, Collections.<String, String>emptyMap());

        assertEquals(200, response.getStatus());
        assertEquals("", response.getResponseBody());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("http://example.com/warp9/", testRequest.getUrl().toString());
        assertEquals(0, response.getLastModifiedTime());
        assertNull(response.getResponseHeaders());

    }

    /**
     * This verifies all required and most optional headers.
     */
    @Test
    public void testRequestHeaders() throws RequestException {
        testRequest.responseBody = "";
        testRequest.responseStatus = 200;
        testRequest.responseLastModifiedTime = 0;

        Map<String, String> headers = new HashMap<>();
        headers.put("foo", "bar");

        Response<EventResponse> response = client.sendEvents(events, headers);

        Map<String, String> requestHeaders = testRequest.getRequestHeaders();

        assertEquals(200, response.getStatus());
        assertEquals("", response.getResponseBody());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("http://example.com/warp9/", testRequest.getUrl().toString());
        assertEquals(0, response.getLastModifiedTime());
        assertEquals("bar", requestHeaders.get("foo"));
    }

    /**
     * Verify we return a response even if the Json is malformated
     */
    @Test
    public void testWrongJson() throws RequestException {
        testRequest.responseBody = "";
        testRequest.responseStatus = 200;
        testRequest.responseLastModifiedTime = 0;

        events = new ArrayList<>();
        events.add(invalidEvent);
        Response<EventResponse> response = client.sendEvents(events, Collections.<String, String>emptyMap());
        assertEquals(200, response.getStatus());
        assertEquals("", response.getResponseBody());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("http://example.com/warp9/", testRequest.getUrl().toString());
        assertEquals(0, response.getLastModifiedTime());
    }

}
