/* Copyright Airship and Contributors */

package com.urbanairship.analytics.data;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestRequestSession;
import com.urbanairship.http.RequestAuth;
import com.urbanairship.http.RequestBody;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.remoteconfig.RemoteAirshipConfig;
import com.urbanairship.remoteconfig.RemoteConfig;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class EventApiClientTest extends BaseTestCase {

    private List<JsonValue> events;
    private EventApiClient client;
    private TestRequestSession requestSession = new TestRequestSession();
    private TestAirshipRuntimeConfig runtimeConfig;
    private JsonValue validEvent;
    private JsonValue invalidEvent;

    @Before
    public void setUp() throws JsonException {
        runtimeConfig = new TestAirshipRuntimeConfig(new RemoteConfig(
                new RemoteAirshipConfig(null, null, null, "http://example.com")
        ));
        validEvent = JsonValue.parseString("{\"some\":\"json\"}");
        invalidEvent = JsonValue.NULL;

        events = new ArrayList<>();
        events.add(validEvent);

        client = new EventApiClient(runtimeConfig, requestSession);
    }

    /**
     * Test sending a correct request that succeeds
     */
    @Test
    public void testSendEventsSucceed() throws RequestException, JsonException {
        requestSession.addResponse(200, "");

        Response<EventResponse> response = client.sendEvents("some channel", events, Collections.<String, String>emptyMap());

        assertEquals(200, response.getStatus());
        assertEquals("", response.getBody());
        assertEquals("POST", requestSession.getLastRequest().getMethod());
        assertEquals("http://example.com/warp9/", requestSession.getLastRequest().getUrl().toString());
        assertEquals(new RequestBody.GzippedJson(JsonValue.wrapOpt(events)), requestSession.getLastRequest().getBody());
        assertEquals(new RequestAuth.ChannelTokenAuth("some channel"), requestSession.getLastRequest().getAuth());

    }

    /**
     * Test sending a request with a null URL will return an exception
     */
    @Test(expected = RequestException.class)
    public void testNullUrl() throws RequestException {
        runtimeConfig = new TestAirshipRuntimeConfig(new RemoteConfig());
        client.sendEvents("some channel", events, Collections.<String, String>emptyMap());
    }

    /**
     * Test sending null or empty events returns an empty response.
     */
    @Test
    public void testSendEmptyEvents() throws RequestException {
        requestSession.addResponse(200, "");
        events = new ArrayList<>();

        Response<EventResponse> response = client.sendEvents("some channel", events, Collections.<String, String>emptyMap());

        assertEquals(200, response.getStatus());
        assertEquals("", response.getBody());
        assertEquals("POST", requestSession.getLastRequest().getMethod());
        assertEquals("http://example.com/warp9/", requestSession.getLastRequest().getUrl().toString());
    }

    /**
     * This verifies all required and most optional headers.
     */
    @Test
    public void testRequestHeaders() throws RequestException {
        requestSession.addResponse(200, "");

        Map<String, String> headers = new HashMap<>();
        headers.put("foo", "bar");

        Response<EventResponse> response = client.sendEvents("some channel", events, headers);

        Map<String, String> requestHeaders = requestSession.getLastRequest().getHeaders();

        assertEquals(200, response.getStatus());
        assertEquals("", response.getBody());
        assertEquals("POST", requestSession.getLastRequest().getMethod());
        assertEquals("http://example.com/warp9/", requestSession.getLastRequest().getUrl().toString());
        assertEquals("bar", requestHeaders.get("foo"));
    }

    /**
     * Verify we return a response even if the Json is malformated
     */
    @Test
    public void testWrongJson() throws RequestException {
        requestSession.addResponse(200, "");

        events = new ArrayList<>();
        events.add(invalidEvent);
        Response<EventResponse> response = client.sendEvents("some channel", events, Collections.<String, String>emptyMap());
        assertEquals(200, response.getStatus());
        assertEquals("", response.getBody());
        assertEquals("POST", requestSession.getLastRequest().getMethod());
        assertEquals("http://example.com/warp9/", requestSession.getLastRequest().getUrl().toString());
    }

}
